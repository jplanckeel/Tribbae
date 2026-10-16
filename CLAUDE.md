# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Tribbae is a family idea-management app (links, recipes, activities, gifts, events) with three parts that share one REST API:

| Part | Stack | Dir |
|------|-------|-----|
| Backend | Go + gRPC + grpc-gateway (REST) + MongoDB | `backend/` |
| Web | React 19 + TypeScript + Vite + Tailwind 4 | `frontend/` |
| Android | Kotlin Multiplatform + Jetpack Compose (package `com.linkkeeper.app`) | `composeApp/` |
| Web search for AI | SearXNG (custom Docker image) | `searxng/` |

**Core rule (from `.kiro/steering/`): every user-facing feature must be implemented on both clients (web + Android).** Docs, comments and UI strings are in French; gRPC error messages are in English.

## Commands

Task runner is [Task](https://taskfile.dev) (`Taskfile.yml` at the root).

```bash
task dev                # SearXNG + Ollama (Docker) + backend + frontend in parallel
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

Frontend (from `frontend/`): `npm run build` (runs `tsc -b` then `vite build` — this is the type check CI runs), `npm run lint`.

CI (`.github/workflows/test.yml`): `go build` + `go vet` + `go test -race` (with a MongoDB service) + `govulncheck` for backend, `npm ci && npm run lint && npm run build` for frontend, `./gradlew assembleDebug` for Android. `release.yml` triggers on `v*` tags (APK + Docker images).

## Backend architecture

- `cmd/server/main.go` wires everything: Mongo connect → `db.EnsureIndexes` → `auth.EnsureAdminAccount` → services → gRPC server on `GRPC_PORT` (9090) → grpc-gateway mux on `PORT` (8080) dialing back into the gRPC server.
- Each domain in `internal/<domain>/` (auth, link, folder, child, follow, comment, admin, ai) has `service.go` (business logic + Mongo access via `bson.M`) and `handler.go` (gRPC server implementing the generated interface). Services receive their `*mongo.Collection`s from `main.go`.
- **API surface is defined in `backend/proto/tribbae/v1/*.proto`** with `google.api.http` annotations. Generated Go, gateway and OpenAPI code lives in `backend/gen/` and **is committed** (needed for the Docker build) — regenerate with `task backend:proto`, never hand-edit.
- Auth: `internal/interceptor` is a gRPC unary interceptor that validates `Authorization: Bearer <JWT>` and puts the user ID in the context. Endpoints that work unauthenticated must be added to the `publicMethods` map there (token is then optional) — read endpoints for public content (`GetLink`, `GetFolder`, `ListLinks` with a folder, comments) are public and rely on the service-level visibility checks. Admin-only methods are guarded by `UnaryAdminAuth`.
- Some routes bypass grpc-gateway and are plain `net/http` handlers wrapped in `main.go`: `POST /v1/ai/generate` (`withAI`) and `GET /v1/links/preview` (Open Graph extraction, `withPreview`). New non-proto HTTP routes follow the same wrapper pattern.
- In production a single image (root `Dockerfile`) serves the built frontend from `/public` with SPA fallback (`withSPA`); anything under `/v1/` goes to the API.
- JSON is camelCase (protojson); enums serialize as names, e.g. `LINK_CATEGORY_RECETTE`.
- Tribe (`internal/tribe`, `tribe.proto`): accounts of one family share everything (ideas, folders, children). `tribe.Service.Peers(userID)` = the user + members with `sharing=true`; it is injected via `SetTribePeers` into the link, folder and child services and every ownership check must go through it (`isPeer`/`ownedByTribe`), never `owner_id == userID` alone. A member added by email sees the tribe immediately but their own content joins only after they confirm (`sharing`), so knowing an email never exposes an account. Publishing (idea or folder visibility, moving an idea into a public folder) stays author/owner-only. One tribe per account (unique index on `members.user_id`); without a tribe document the tribe is virtual and named after the account. Ideas are always signed with the author's account name; the tribe name is only for « La tribu … » (home, Ma tribu). Clients mirror the peer set (web `useMyData().peers`, Android `LinkViewModel.isTribePeer`).
- Link images: `internal/preview` scrapes pages (Chrome TLS fingerprint via `netsafe.NewBrowserClient`, then WhatsApp/Facebook preview UAs, oEmbed). `link.RunImageBackfill` (started in `main.go`) fills `image_url` for links without one in the background, recording attempts in `image_checked_at` (retry after 7 days); changing a link's URL triggers an immediate retry. Android mirrors the extractor offline in `androidMain/.../data/PageImageExtractor.kt` (jsoup) + `OgImageFetcher.kt`, and both clients fetch an image when an image-less idea is opened.

### AI generation (`internal/ai`)
Pipeline: web search — Ollama's web search API (`websearch.go`, when `OLLAMA_API_KEY` is set; not blocked like datacenter IPs) then SearXNG as fallback (`search.go`: 1–2 queries, safesearch; Google/Qwant/Startpage disabled in `searxng/settings.yml` because they 403 VPS IPs); blocked/duplicate domains filtered, max 8 sources → the model writes ideas citing a **source number** under a JSON schema imposed via Ollama `format` / Gemini `responseJsonSchema` (`prompt.go`); it never writes URLs, the server maps the source to its real URL → each idea is enriched from its page via `preview.Fetch` (image, recipe ingredients, price, location). Local model: Ollama on a small CPU VPS (default `qwen3:1.7b`, `think:false` only when the model supports thinking, one generation at a time → `ErrBusy`/429, pulled and warmed at startup). Premium users with `GEMINI_API_KEY`: Gemini writes the ideas (same prompt/schema), falling back to Ollama. Ideas are only returned; clients save the ones the user keeps. Model benchmark on real prompts: `go test -tags aibench -run TestBench ./internal/ai` (see `bench_test.go`).

Config is all env vars, see `backend/internal/config/config.go` (Mongo, JWT, ports, Ollama, SearXNG, Gemini, `ADMIN_PASSWORD`).

## Clients

- **Web** (refonte « Le feu de camp », mobile-first): `src/lib/api.ts` is the single API layer (base `/v1`, relative — Vite proxy in dev, `API_URL=… npm run dev` to target another backend; same origin in prod). Types in `src/lib/types.ts`, design tokens in `src/index.css` (`@theme`), category/folder metadata in `src/lib/categories.ts`. `lib/data.tsx` (`useMyData`) caches the user's links/folders/children; `components/Composer.tsx` (`useComposer`) owns every create/edit sheet (add, idea form, folder form, AI). Routes in `App.tsx` (data router), screens in `pages/`. Lint (`npm run lint`) uses the strict React Hooks v7 rules: no synchronous setState in effects — mount sheets only when open instead of resetting them.
- **Android**: `composeApp/src/commonMain/kotlin/` — `data/` (models, `*Repository`, `ApiClient`/`AuthenticatedApiClient` over `HttpURLConnection`, `SessionManager`), `viewmodel/LinkViewModel.kt` (large, holds most app state as `StateFlow`), `ui/` (Compose screens). Navigation is state-based (`SubScreen`) in `AppModern.kt`. `ApiClient` defaults to the **production** backend `https://tribbae.bananaops.cloud`. Offline-first: local creation then sync, backend is source of truth.

## Cross-cutting changes

Link categories (Idée, Cadeau, Activité, Sortie=`EVENEMENT`, Recette, Livre, Déco=`DECORATION`) and their colors are duplicated in places that must stay in sync: `backend/proto/tribbae/v1/link.proto`, `frontend/src/lib/categories.ts` + `src/index.css`, `composeApp/src/commonMain/kotlin/data/Link.kt` + `ui/Theme.kt` (`CategoryPalettes`, `TribbaeTheme`). Likewise, a new/changed field typically touches: proto → `task backend:proto` → Go service/Mongo mapping → `frontend/src/lib/types.ts` + `lib/api.ts` → Kotlin model + repository.
