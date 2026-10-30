# Tribbae

Les idées de la tribu au même endroit : sorties, recettes, cadeaux, activités, livres, déco.
Une famille y range ses idées dans des dossiers, les partage avec ses proches et en découvre d'autres.

Site : [tribbae.fr](https://tribbae.fr) · App Android : `fr.tribbae.app`

## Architecture

| Partie | Techno | Dossier |
|--------|--------|---------|
| Backend API | Go + gRPC + grpc-gateway (REST) + MongoDB 9 | `backend/` |
| Web | React 19 + TypeScript + Vite + Tailwind CSS 4 | `frontend/` |
| Android | Kotlin Multiplatform + Jetpack Compose | `composeApp/` |
| IA | Ollama (modèle local, sans GPU) | `backend/internal/ai/` |

Les deux clients parlent à la même API REST (`/v1/…`), définie dans `backend/proto/tribbae/v1/*.proto`.
Toute fonctionnalité visible est faite sur le web **et** sur Android.

## Prérequis

- [Go 1.26](https://go.dev/dl/)
- [Node.js 24](https://nodejs.org/)
- [Docker](https://www.docker.com/) (Ollama, et MongoDB si besoin)
- [MongoDB 9](https://www.mongodb.com/) sur `localhost:27017`, par exemple `docker run -d -p 27017:27017 mongo:9`
- [Task](https://taskfile.dev/) : `brew install go-task`
- Pour Android : [Android Studio](https://developer.android.com/studio), JDK 17, SDK 37
- Pour modifier l'API : [Buf](https://buf.build/docs/installation)

## Démarrage rapide

```bash
task frontend:install
task dev          # Ollama (Docker) + backend + frontend
```

- Web → http://localhost:5173 (Vite redirige `/v1` vers le backend)
- API REST → http://localhost:8080 · gRPC → `localhost:9090`
- Ollama → http://localhost:11434

Tout en Docker (MongoDB compris) : `task dev:docker` (`docker-compose.local.yml`).

`task backend` charge `backend/.env.example` : pour changer une valeur en local, l'exporter dans le shell
(elle prend le pas sur le fichier), par exemple `ADMIN_PASSWORD=… task backend`.

L'app Android vise la production (`https://tribbae.fr`) par défaut. Pour la brancher sur le backend local
depuis l'émulateur :

```bash
./gradlew :composeApp:assembleDebug -Ptribbae.apiUrl=http://10.0.2.2:8080
```

## Commandes

| Commande | Description |
|----------|-------------|
| `task dev` | Ollama + backend + frontend en parallèle |
| `task dev:docker` / `task dev:stop` | Tout via Docker Compose local / l'arrêter |
| `task backend` | Serveur Go (gRPC + passerelle HTTP) |
| `task backend:proto` | Régénère le code à partir des `.proto` (`buf generate`) — le code généré est commité |
| `task frontend:dev` | Vite en mode dev |
| `task frontend:build` | Build de production du web |
| `task build` / `task build-release` | APK Android debug / release |
| `task run` | Compile, installe et lance l'app sur l'appareil connecté |
| `task logs` | Logs ADB de l'app |
| `task check` | Lint et tests Android |
| `task ollama:models` | Modèles Ollama disponibles |
| `task seed` / `task seed:clean` | Données de test (k6) / les supprimer |
| `task docker:build` | Image de production en local |

`task --list` donne la liste complète.

## Tests

```bash
cd backend && go test ./...                 # MongoDB local requis, sinon les tests sont ignorés
cd frontend && npm run lint && npm test && npm run build
./gradlew :composeApp:testDebugUnitTest
```

La logique écrite deux fois (web et Android : âges, ordre « À la une », personnalisation des dossiers,
ajout d'idée, IA, tribu…) est testée des deux côtés avec les mêmes cas : changer un cas d'un côté,
c'est le changer de l'autre.

## Fonctionnalités

### Idées et dossiers
- 7 catégories : Idée, Cadeau, Activité, Sortie, Recette, Livre, Déco
- Ajout en quelques étapes depuis un lien (copié, partagé vers l'app), un texte, une photo ou l'IA ;
  le lien est lu (titre, image, description) et trois dossiers sont suggérés
- Dossiers personnalisables : couverture, bannière illustrée, couleur, date clé avec compte à rebours,
  enfants concernés, affichage et tri, épinglage
- Âge conseillé, tags, lieu, prix, note, ingrédients (liste de courses), date d'événement
  avec rappel la veille
- Photos envoyées au serveur, réduites et nettoyées de leurs métadonnées (GPS compris)

### La tribu et le cercle
- **Le foyer** : les comptes d'une même famille partagent tout (idées, dossiers, enfants) ;
  on y entre sur invitation par email
- **Le cercle** : des proches invités sur certains dossiers seulement, en lecture ou en écriture
- Profils enfants (date de naissance) pour filtrer les idées par âge
- Adresse email à confirmer avant de publier ou d'inviter

### Explorer
- Idées et dossiers publics de la communauté, ordre « À la une » renouvelé toutes les 6 h
- Likes, commentaires, abonnements, enregistrement d'une idée publique dans ses dossiers
- Pages publiques lisibles sans compte et sans JavaScript (référencement, aperçus de partage,
  `sitemap.xml`, `llms.txt`)

### Idées avec l'IA
- Une phrase à compléter (« Je cherche des activités pour Léo ce week-end… ») ; un modèle local
  (`qwen3:1.7b` par défaut) propose 5 idées avec leur « pourquoi », à garder ou écarter
- Rien n'est envoyé à un service tiers ; pas de recherche web

### Assistants
- **Claude** (connecteur MCP, `SITE_URL/mcp`, OAuth 2.1) : lister ses dossiers et idées, ajouter
  des idées privées — voir [docs/mcp.md](docs/mcp.md). Désactivé par défaut (`MCP_ENABLED`).
- **Gemini** sur Android 16+ (AppFunctions, sur l'appareil) : lister ses dossiers, créer des idées
  et des dossiers privés
- Écran « Applications connectées » pour voir et révoquer les accès

### Le reste
- Hors ligne sur Android : création et modification locales, envoyées à la reconnexion
- Mode sombre « La veillée » (Jour / Soir / Auto)
- Session glissante de 30 jours, mot de passe oublié par email
- Export des données et suppression du compte (RGPD)
- Statistiques d'usage pour l'administrateur (web)

## Configuration

Tout se règle par variables d'environnement (`backend/internal/config/config.go`) ; les valeurs
d'exemple et leur rôle sont dans [`backend/.env.example`](backend/.env.example). Les principales :

| Variable | Rôle |
|----------|------|
| `MONGO_URI`, `MONGO_DB` | Base MongoDB |
| `JWT_SECRET` | Signature des sessions — à changer en production |
| `PORT`, `GRPC_PORT` | Ports HTTP (8080) et gRPC (9090) |
| `SITE_URL`, `REDIRECT_HOSTS` | Adresse publique ; autres noms redirigés vers elle |
| `OLLAMA_URL`, `OLLAMA_MODEL` | Serveur et modèle d'IA |
| `ADMIN_PASSWORD` | Crée le compte administrateur (≥ 12 caractères) |
| `BREVO_API_KEY`, `MAIL_FROM` | Emails transactionnels ; sans clé ils sont seulement journalisés |
| `MCP_ENABLED`, `OAUTH_REDIRECT_URIS` | Serveur MCP et applications connectées |

## Structure

```
tribbae/
├── backend/
│   ├── cmd/server/       # Point d'entrée : branche tous les services
│   ├── internal/         # Un dossier par domaine (link, folder, tribe, auth, ai, seo, mcpserver…)
│   ├── proto/            # API (protobuf + annotations REST)
│   └── gen/              # Code généré par buf (commité)
├── frontend/src/
│   ├── pages/            # Écrans
│   ├── components/
│   └── lib/              # api.ts (seule couche API), types, logique partagée + tests
├── composeApp/src/
│   ├── commonMain/kotlin/  # data/ (modèles, dépôts, ApiClient), ui/ (Compose), viewmodel/
│   └── androidMain/kotlin/ # MainActivity, fonctions Gemini, stockage
├── docs/                 # Documentation (serveur MCP)
├── scripts/              # Données de test, release
├── Dockerfile            # Image unique : API + web
└── Taskfile.yml
```

## Déploiement

Une seule image Docker (`Dockerfile` à la racine) sert l'API et le web compilé ; tout ce qui est sous
`/v1/` va à l'API. En production elle tourne avec MongoDB et Ollama via `docker-compose.yml`
(derrière Traefik) :

```bash
JWT_SECRET=… ADMIN_PASSWORD=… docker compose up -d
```

- `.github/workflows/test.yml` : backend (build, vet, tests avec MongoDB, govulncheck),
  web (lint, tests, build), Android (APK debug) à chaque PR
- `release-please.yml` : prépare la release (version, CHANGELOG) à partir des commits
  [Conventional Commits](https://www.conventionalcommits.org/)
- `release.yml` : sur un tag `v*`, APK signé (avec son fichier de mapping R8) et image
  `jplanckeel/tribbae` (amd64 + arm64)

## Contribuer

Une PR par sujet, branche `<type>/<description>` depuis `main`, commits en Conventional Commits
signés (GPG) et avec `Signed-off-by` (`git commit -s`). Le titre de la PR devient le commit sur `main`.
Les détails d'architecture sont dans [CLAUDE.md](CLAUDE.md).

## Licence

MIT
