# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Tribbae is a family idea-management app (links, recipes, activities, gifts, events) with three parts that share one REST API:

| Part | Stack | Dir |
|------|-------|-----|
| Backend | Go + gRPC + grpc-gateway (REST) + MongoDB | `backend/` |
| Web | React 19 + TypeScript + Vite + Tailwind 4 | `frontend/` |
| Android | Kotlin Multiplatform + Jetpack Compose (package `com.linkkeeper.app`) | `composeApp/` |
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

Frontend (from `frontend/`): `npm run build` (runs `tsc -b` then `vite build` — this is the type check CI runs), `npm run lint`.

CI (`.github/workflows/test.yml`): `go build` + `go vet` + `go test -race` (with a MongoDB service) + `govulncheck` for backend, `npm ci && npm run lint && npm run build` for frontend, `./gradlew assembleDebug` for Android. `release.yml` triggers on `v*` tags (APK + Docker images).

## Backend architecture

- `cmd/server/main.go` wires everything: Mongo connect → `db.EnsureIndexes` → `auth.EnsureAdminAccount` → services → gRPC server on `GRPC_PORT` (9090) → grpc-gateway mux on `PORT` (8080) dialing back into the gRPC server.
- Each domain in `internal/<domain>/` (auth, link, folder, child, follow, comment, admin, ai) has `service.go` (business logic + Mongo access via `bson.M`) and `handler.go` (gRPC server implementing the generated interface). Services receive their `*mongo.Collection`s from `main.go`.
- **API surface is defined in `backend/proto/tribbae/v1/*.proto`** with `google.api.http` annotations. Generated Go, gateway and OpenAPI code lives in `backend/gen/` and **is committed** (needed for the Docker build) — regenerate with `task backend:proto`, never hand-edit.
- Auth: `internal/interceptor` is a gRPC unary interceptor that validates `Authorization: Bearer <JWT>` and puts the user ID in the context. Endpoints that work unauthenticated must be added to the `publicMethods` map there (token is then optional) — read endpoints for public content (`GetLink`, `GetFolder`, `ListLinks` with a folder, comments) are public and rely on the service-level visibility checks. Admin-only methods are guarded by `UnaryAdminAuth`.
- Some routes bypass grpc-gateway and are plain `net/http` handlers wrapped in `main.go`: `POST /v1/ai/generate` (`withAI`), `GET /v1/links/preview` (Open Graph extraction, `withPreview`), and the GDPR routes `GET /v1/account/export` / `POST /v1/account/delete` (`withAccount`, `internal/account`; deletion requires the password, is rate-limited like login, and erases everything the account owns while keeping other users' ideas — **a new collection holding user data must be added to `account.Service.Export` and `Delete`**). `auth.ValidateToken` rejects tokens of deleted accounts. Privacy policy: `frontend/src/pages/Privacy.tsx` (`/confidentialite`; editor and host placeholders to fill), linked from sign-up on both clients. Android `LinkViewModel.signOut()` also wipes the offline cache. New non-proto HTTP routes follow the same wrapper pattern.
- In production a single image (root `Dockerfile`) serves the built frontend from `/public` (`STATIC_DIR` to override); anything under `/v1/` goes to the API. The SPA fallback goes through `internal/seo`: for each URL it replaces what lies between `<!--seo:head-->` markers in `frontend/index.html` with that page's head (title, description, canonical on `SITE_URL`, Open Graph, schema.org JSON-LD: Recipe/Event/Book/CollectionPage/FAQPage/BreadcrumbList) and fills `#root` with equivalent plain HTML, so search engines and AI crawlers that don't run JS see real content. Only anonymously visible content is rendered (same rules as a visitor without account); unknown/private ideas get 404 + noindex, private screens noindex, legacy URLs 301. It also serves dynamic `/sitemap.xml` (with images) and `/llms.txt`. The client mirrors head tags with `components/Seo.tsx` (every page renders one; it removes the server's `data-seo` tags on mount). The FAQ lives in `frontend/public/faq.json`, read by both `pages/Faq.tsx` and the server (FAQPage). Category intros are duplicated in `seo/categories.go` and `lib/categories.ts`.
- JSON is camelCase (protojson); enums serialize as names, e.g. `LINK_CATEGORY_RECETTE`.
- Tribe (`internal/tribe`, `tribe.proto`): accounts of one family share everything (ideas, folders, children). `tribe.Service.Peers(userID)` = the user + members with `sharing=true`; it is injected via `SetTribePeers` into the link, folder and child services and every ownership check must go through it (`isPeer`/`ownedByTribe`), never `owner_id == userID` alone. A member added by email sees the tribe immediately but their own content joins only after they confirm (`sharing`), so knowing an email never exposes an account. Publishing (idea or folder visibility, moving an idea into a public folder) stays author/owner-only. One tribe per account (unique index on `members.user_id`); without a tribe document the tribe is virtual and named after the account. Ideas are always signed with the author's account name; the tribe name is only for « La tribu … » (home, Ma tribu). Clients mirror the peer set (web `useMyData().peers`, Android `LinkViewModel.isTribePeer`).
- Link images: `internal/preview` scrapes pages (Chrome TLS fingerprint via `netsafe.NewBrowserClient`, then WhatsApp/Facebook preview UAs, oEmbed). `link.RunImageBackfill` (started in `main.go`) fills `image_url` for links without one in the background, recording attempts in `image_checked_at` (retry after 7 days); changing a link's URL triggers an immediate retry. Android mirrors the extractor offline in `androidMain/.../data/PageImageExtractor.kt` (jsoup) + `OgImageFetcher.kt`, and both clients fetch an image when an image-less idea is opened.

### AI generation (`internal/ai`)
Ideas are written by the **local Ollama model only, no web search** (`service.go`, `ollama.go`, `prompt.go`). The model answers under a JSON schema imposed via Ollama `format` (title, description, category, tags, ageRange — no URL nor price, they would be invented); `parseIdeas` normalizes and salvages truncated JSON. Small CPU VPS: default `qwen3:1.7b`, `think:false` only when the model supports thinking, one generation at a time → `ErrBusy`/429, pulled and warmed at startup. Ideas are only returned; clients save the ones the user keeps. Model benchmark on real prompts: `go test -tags aibench -run TestBench ./internal/ai` (see `bench_test.go`).

Config is all env vars, see `backend/internal/config/config.go` (Mongo, JWT, ports, Ollama, `ADMIN_PASSWORD`).

## Clients

- **Web** (refonte « Le feu de camp », mobile-first): `src/lib/api.ts` is the single API layer (base `/v1`, relative — Vite proxy in dev, `API_URL=… npm run dev` to target another backend; same origin in prod). Types in `src/lib/types.ts`, design tokens in `src/index.css` (`@theme`), category/folder metadata in `src/lib/categories.ts`. `lib/data.tsx` (`useMyData`) caches the user's links/folders/children; `components/Composer.tsx` (`useComposer`) owns every create/edit sheet (add, idea form, folder form, AI). Routes in `App.tsx` (data router), screens in `pages/`. Lint (`npm run lint`) uses the strict React Hooks v7 rules: no synchronous setState in effects — mount sheets only when open instead of resetting them.
- **Android**: `composeApp/src/commonMain/kotlin/` — `data/` (models, `*Repository`, `ApiClient`/`AuthenticatedApiClient` over `HttpURLConnection`, `SessionManager`), `viewmodel/LinkViewModel.kt` (large, holds most app state as `StateFlow`), `ui/` (Compose screens). Navigation is state-based in `AppModern.kt`: tabs are composed on first visit and then kept alive but unplaced (`keptHidden`) while another tab or a pushed screen is shown, so returning is instant and keeps scroll — a tab's `LaunchedEffect(Unit)` therefore runs once, not on every visit (community ideas refresh via `refreshCommunityIfStale`, 5 min). The Explorer mosaic renders cards in batches of 24. Measure perf on a release build: debug Compose is ~2× slower. All repositories default to `data.Backend.url` (**production** `https://tribbae.bananaops.cloud`); a debug build can target a local backend with `./gradlew :composeApp:assembleDebug -Ptribbae.apiUrl=http://10.0.2.2:8080` (`BuildConfig.API_URL`, applied in `MainActivity`; cleartext is allowed for 10.0.2.2/localhost only). Offline-first: local creation then sync, backend is source of truth.

Idea age (`ageRange`) stays a string server-side but is entered with an age picker (bands = Explorer filter bands, shortcuts from the tribe's children ages, precise steppers by 6 months under 2 years) and always written in one canonical form (« 3–6 ans », « 18 mois – 3 ans », « 10 ans et + », « Tous âges »); parsing/formatting live in `frontend/src/lib/age.ts` and `composeApp/.../data/AgeRange.kt` (keep in sync; `AgeRangesTest` pins the cases). Legacy free-text values are still read.

Explorer default order « À la une » is a weighted shuffle reseeded every 6 h (`rotationScore`, FNV-1a + u^(1/weight), weight from likes and freshness), implemented identically in `frontend/src/lib/links.ts` and `ui/ExploreScreen.kt` — keep them in sync (`androidUnitTest/.../RotationScoreTest.kt` pins reference values from the web).

Folder customization (« Personnaliser » screen: cover illu/uni/tipis/pois/rayures/photo, 9 illustrated banners, description, key date + countdown, « Pour qui » children, view mode, sort, pinned) lives in `folder.proto` (`UpdateFolderRequest` uses `optional` fields + `set_child_ids` so older clients don't wipe them; `internal/folder/style.go` validates; key date/children/pinned are hidden from non-tribe viewers). Logic is duplicated in `frontend/src/lib/folderStyle.ts` and `composeApp/.../data/FolderStyle.kt` (auto illustration from name/date, countdown labels — `FolderStyleTest` pins the cases). Banner drawings are generated from the design mockup into `frontend/src/components/FolderBannerArt.tsx` and `composeApp/.../ui/campfire/FolderBannerShapes.kt` (pastel ones take the folder color's bg/mid); folder icon paths and the 9-color palette (bg/mid/ink) are duplicated in `lib/categories.ts` and `ui/campfire/FolderCover.kt` / `Campfire.kt` / `Theme.kt`.

Screen transitions follow the « Transitions & navigation » mockup on both clients: opening an idea morphs its card into the detail (web: View Transitions via React Router `viewTransition`, names `idea-visual`/`idea-title`, `lib/transitions.ts` + `index.css`; the detail shows the cached/preview link instantly so the morph has a target. Android: `Screen.Detail.origin` from `CardOrigins` (recorded by `IdeaVisual`) + `heroFrom` in `AppModern.kt`, `LocalOpenProgress` delays the detail body; predictive back shrinks it to 92 %). Drilling (detail → folder « Rangée dans ») slides in from the right while the previous page recedes 30 % and dims, and the source idea is outlined in the folder. Tab changes fade in with a 26 px slide in the tab's direction. Durations/easings live in `ui/Transitions.kt` and `index.css`; keep them in sync.

Adding an idea follows the « Ajouter une idée v2 » mockup on both clients: a multi-step sheet (start from the clipboard link / a link / writing / a photo / the AI → the link is read and its findings shown → « Où on la range ? » with 3 suggested folders and their reasons, a folder picker with search and inline creation, category, age bands, public toggle → « C'est rangé ! » celebration with the eve reminder). Web `components/AddFlow.tsx` (opened by `useComposer().openAdd`, « Plus de détails » falls back to `LinkFormSheet`), Android `ui/AddFlowScreen.kt`. Suggestions, « qui la verra » and age bands are duplicated in `lib/addFlow.ts` and `data/AddFlow.kt` (`AddFlowTest`). There is no photo upload backend: the photo start uses an image address on the web and the local picker (file://) on Android.

## Cross-cutting changes

Link categories (Idée, Cadeau, Activité, Sortie=`EVENEMENT`, Recette, Livre, Déco=`DECORATION`) and their colors are duplicated in places that must stay in sync: `backend/proto/tribbae/v1/link.proto`, `frontend/src/lib/categories.ts` + `src/index.css`, `composeApp/src/commonMain/kotlin/data/Link.kt` + `ui/Theme.kt` (`CategoryPalettes`, `TribbaeTheme`). Likewise, a new/changed field typically touches: proto → `task backend:proto` → Go service/Mongo mapping → `frontend/src/lib/types.ts` + `lib/api.ts` → Kotlin model + repository.
