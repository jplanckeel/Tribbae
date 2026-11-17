# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Tribbae is a family idea-management app (links, recipes, activities, gifts, events) with three parts that share one REST API:

| Part | Stack | Dir |
|------|-------|-----|
| Backend | Go + gRPC + grpc-gateway (REST) + MongoDB | `backend/` |
| Web | React 19 + TypeScript + Vite + Tailwind 4 | `frontend/` |
| Android | Kotlin Multiplatform + Jetpack Compose (application id `fr.tribbae.app`, code package `com.linkkeeper.app`) | `composeApp/` |
| AI | Ollama (local model, Docker) | `backend/internal/ai/` |

**Core rule (from `.kiro/steering/`): every user-facing feature must be implemented on both clients (web + Android).** Docs, comments and UI strings are in French; gRPC error messages are in English.

## Commands

Task runner is [Task](https://taskfile.dev) (`Taskfile.yml` at the root).

```bash
task dev                # Ollama (Docker) + backend + frontend in parallel
task dev:docker         # Everything via docker-compose.local.yml
task backend            # go run ./cmd/server — NOTE: loads backend/.env.example, not .env
task backend:proto      # buf generate (run after editing any .proto)
task frontend:dev       # Vite on :5173, proxies /v1 -> localhost:8080
task build              # Android debug APK (./gradlew :composeApp:assembleDebug)
task run                # install + launch on connected device
task check              # ./gradlew check (Android lint + tests)
task seed               # k6 run scripts/seed-data.js (test data)
```

Backend (from `backend/`):
```bash
go build ./...
go test ./...
go test ./internal/link -run TestName -v   # single test
```
Backend tests (`internal/{link,folder,follow,comment}/*_test.go`) hit a real MongoDB at `mongodb://localhost:27017`, create a throwaway `tribbae_test_<id>` DB, and **skip** (not fail) if Mongo isn't reachable. Some use `gopter` property-based testing.

Frontend (from `frontend/`): `npm run build` (runs `tsc -b` then `vite build` — this is the type check CI runs), `npm run lint`, `npm test` (Vitest, `src/lib/*.test.ts`, pure logic in Node with `TZ=Europe/Paris`; `npm run test:watch` while editing). Logic duplicated with Android is tested on both sides with the same cases (e.g. `age.test.ts` ↔ `AgeRangesTest`, `links.test.ts` ↔ `RotationScoreTest`): change a case on one side, change it on the other.

QA: the `qa` agent (`.claude/agents/qa.md`) runs every suite, starts the app locally (throwaway `tribbae_qa` database, no real email, never production) and checks the main flows plus the project's edge cases (tribe/circle rights, public vs private, unverified email, offline sync, dark mode, web/Android parity, GDPR), then reports bugs without changing app code. Use it to check a PR or before a release.

CI (`.github/workflows/test.yml`): `go build` + `go vet` + `go test -race` (with a MongoDB service) + `govulncheck` for backend, `npm ci && npm run lint && npm test && npm run build` for frontend, `./gradlew assembleDebug` then `./gradlew check` (unit tests, including web parity, and Android lint, whose errors fail the build) for Android. `release.yml` triggers on `v*` tags (APK + Docker images).

## Git workflow

- **One PR per topic.** Never commit on `main`: branch from an up-to-date `main`, keep the branch to a single subject (a fix, a feature, a CI change…), and open a separate PR for anything unrelated found along the way. A CI fix and a dependency bump are two PRs, not one.
- Branch names: `<type>/<short-kebab-description>`, e.g. `fix/govulncheck-go-patch`, `feat/tribe-invitations`, `ci/mongo-9`.
- **Commits follow [Conventional Commits](https://www.conventionalcommits.org/)**: `<type>(<scope>): <description>`, description in English, imperative, lowercase, no final period.
  - Types: `feat`, `fix`, `refactor`, `perf`, `test`, `docs`, `style`, `build`, `ci`, `chore`, `revert`.
  - Scopes (optional): `backend`, `frontend`, `android`, `ai`, `proto`, `seo`, `ci`, `docker`, `deps`, or a backend domain (`link`, `folder`, `tribe`…).
  - Breaking change: `!` after the type/scope and a `BREAKING CHANGE:` footer.
  - Examples: `fix(ci): always install the latest go 1.26 patch`, `feat(tribe)!: require confirmation before sharing content`.
- PRs are squash-merged, so **the PR title must itself be a Conventional Commit** (it becomes the commit on `main`). The PR description says what changed, why, and how it was tested.
- Commits must be authored and signed by the `jplanckeel` account (`Jérémy PLANCKEEL <jeremy.planckeel@outlook.com>`, GPG key `49D1339A1DA8FB47`), set in this repo's local git config. Never pass `--no-gpg-sign` or override the identity, never rewrite published history and never force-push `main`.
- **Every commit needs a DCO sign-off** (`Signed-off-by: Jérémy PLANCKEEL <jeremy.planckeel@outlook.com>`): the DCO app blocks any PR containing a commit without it. Always commit with `git commit -s` (a local `prepare-commit-msg` hook also adds it). GPG signing and sign-off are two different things; both are required.

## Backend essentials

- `cmd/server/main.go` wires everything: Mongo → `db.EnsureIndexes` → `auth.EnsureAdminAccount` → services → gRPC on `GRPC_PORT` (9090) → grpc-gateway on `PORT` (8080). Config is all env vars (`backend/internal/config/config.go`).
- Each domain in `internal/<domain>/` has `service.go` (logic + Mongo via `bson.M`) and `handler.go` (gRPC server). Services get their `*mongo.Collection`s from `main.go`.
- **API surface = `backend/proto/tribbae/v1/*.proto`.** Generated code in `backend/gen/` is committed — regenerate with `task backend:proto`, never hand-edit. JSON is camelCase (protojson); enums serialize as names (`LINK_CATEGORY_RECETTE`).
- MongoDB 9, Go driver v2. Every list query must be served by an index matching its filter **and** sort (`internal/db/indexes.go`; to replace one, add the old name to `obsolete`). Batch author/count lookups with `$in` + projection — never one query per item, never load `password`.
- Ownership checks go through the tribe peer set (`isPeer`/`ownedByTribe`), **never `owner_id == userID` alone**. Publishing stays author/owner-only and needs a verified email.
- Unauthenticated endpoints must be listed in `publicMethods` (`internal/interceptor`). Non-proto HTTP routes follow the `with…` wrapper pattern in `main.go`.
- **A new collection holding user data must be added to `account.Service.Export` and `Delete`** (GDPR).
- Emails go through `internal/mail` `Notifier` interfaces; a failed email never fails the user action, and logs never carry an address.

## Clients essentials

- **Web**: `src/lib/api.ts` is the single API layer, types in `src/lib/types.ts`. Strict React Hooks v7 lint: no synchronous setState in effects — mount sheets only when open. Use theme tokens (`bg-card`, `var(--color-…)`), never `bg-white` or literals, so dark mode works.
- **Android**: offline-first — every local change goes through `markLinkChanged` + `schedulePush`, never a direct API call. Repositories default to **production**; a debug build targets local with `-Ptribbae.apiUrl=http://10.0.2.2:8080`. Use `ui/Theme.kt` getters, never `Color.White`. `applicationId` `fr.tribbae.app` never changes; reflection needs an R8 keep rule; no exact-alarm permissions.
- Logic duplicated between web (`frontend/src/lib/*.ts`) and Android (`composeApp/.../data/*.kt`) is tested on both sides with the same cases — change both together.

## Cross-cutting changes

Link categories (Idée, Cadeau, Activité, Sortie=`EVENEMENT`, Recette, Livre, Déco=`DECORATION`) and their colors are duplicated in places that must stay in sync: `backend/proto/tribbae/v1/link.proto`, `frontend/src/lib/categories.ts` + `src/index.css`, `composeApp/src/commonMain/kotlin/data/Link.kt` + `ui/Theme.kt` (`CategoryPalettes`, `TribbaeTheme`). Likewise, a new/changed field typically touches: proto → `task backend:proto` → Go service/Mongo mapping → `frontend/src/lib/types.ts` + `lib/api.ts` → Kotlin model + repository.

## Reference docs (read the one for the area you touch)

- [`docs/claude/backend.md`](docs/claude/backend.md) — server wiring, Mongo, sessions, auth, plain HTTP routes, GDPR, photos, domain/SEO/production, emails, link images, AI generation.
- [`docs/claude/features.md`](docs/claude/features.md) — tribe, email verification, folder share link, circle invitations, moderation/blocking, comments, gift lists, shared shopping list.
- [`docs/claude/assistants.md`](docs/claude/assistants.md) — OAuth 2.1, MCP server, connected apps, Gemini AppFunctions (with `docs/mcp.md`).
- [`docs/claude/clients.md`](docs/claude/clients.md) — web and Android architecture, navigation, offline sync, Play Store/R8, and each screen (age picker, search, Explorer rotation, folder customization, visibility, likes, transitions, Ma tribu, add flow, wheel picker, shopping list, dark mode).
