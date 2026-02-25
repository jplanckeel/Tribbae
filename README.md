# Tribbae

Application familiale de gestion d'idées — liens, recettes, activités, cadeaux, événements.

## Architecture

| Couche | Techno | Dossier |
|--------|--------|---------|
| App mobile Android | Kotlin Multiplatform + Jetpack Compose | `composeApp/` |
| Frontend web | React + TypeScript + Tailwind CSS + Vite | `frontend/` |
| Backend API | Go + gRPC + grpc-gateway REST + MongoDB | `backend/` |
| IA | Ollama (LLM local) | `backend/internal/ai/` |

## Prérequis

- [Go 1.23+](https://go.dev/dl/)
- [Node.js 20+](https://nodejs.org/)
- [Docker](https://www.docker.com/) (pour Ollama)
- [Android Studio](https://developer.android.com/studio) + SDK Android (pour le mobile)
- [Task](https://taskfile.dev/) — `brew install go-task` ou `go install github.com/go-task/task/v3/cmd/task@latest`
- [MongoDB](https://www.mongodb.com/) en local ou Atlas

## Démarrage rapide

```bash
# Installer les dépendances frontend
task frontend:install

# Copier et adapter les variables d'environnement
cp backend/.env.example backend/.env

# Démarrer tout l'environnement de dev (Ollama + backend + frontend)
task dev
```

Accès :
- Frontend → http://localhost:5173
- Backend API → http://localhost:8080
- Ollama → http://localhost:11434

## Commandes

### Dev

| Commande | Description |
|----------|-------------|
| `task dev` | Démarre Ollama + backend + frontend en parallèle |
| `task dev:stop` | Arrête backend et frontend |

### Backend

| Commande | Description |
|----------|-------------|
| `task backend` | Lance le serveur Go (gRPC + HTTP gateway) |
| `task backend:build` | Compile le binaire |
| `task backend:proto` | Régénère le code protobuf (`buf generate`) |

### Frontend

| Commande | Description |
|----------|-------------|
| `task frontend:dev` | Lance Vite en mode dev |
| `task frontend:build` | Build de production |
| `task frontend:install` | Installe les dépendances npm |

### Ollama (IA)

| Commande | Description |
|----------|-------------|
| `task ollama` | Démarre Ollama en Docker et pull le modèle |
| `task ollama:stop` | Arrête le conteneur |
| `task ollama:logs` | Affiche les logs |
| `task ollama:models` | Liste les modèles disponibles |

Le modèle par défaut est `qwen2.5:3b` (optimisé CPU, ~2 Go RAM, excellent en français). Pour en changer :

```bash
task ollama OLLAMA_MODEL=mistral
```

> Pourquoi `qwen2.5:3b` ? Tourne confortablement sur 2 CPU / 8 Go RAM (VPS ou machine modeste), multilingue français natif, très bon pour générer du JSON structuré.

### Mobile Android

| Commande | Description |
|----------|-------------|
| `task build` | Compile l'APK debug |
| `task install` | Compile et installe sur un appareil connecté |
| `task run` | Compile, installe et lance l'app |
| `task logs` | Affiche les logs ADB |

## Fonctionnalités

- Gestion d'idées par catégories : 💡 Idée · 🎁 Cadeau · 🏃 Activité · 📅 Événement · 🍳 Recette
- Organisation en listes (dossiers) avec partage et collaboration
- Filtres par catégorie, liste, enfant, favoris
- Génération d'idées par IA (Ollama) — ex: "anniversaire pirate pour un enfant de 2 ans"
- Profils enfants avec filtrage par âge
- Agenda des événements
- Liste de courses (ingrédients)
- Communauté — listes publiques partagées
- App mobile Android + web responsive

## Variables d'environnement

Copier `backend/.env.example` en `backend/.env` et adapter :

```env
MONGO_URI=mongodb://localhost:27017
MONGO_DB=tribbae
JWT_SECRET=change-me-in-production
PORT=8080
GRPC_PORT=9090
BASE_URL=http://localhost:8080
OLLAMA_URL=http://localhost:11434
OLLAMA_MODEL=qwen2.5:3b
```

## Structure du projet

```
tribbae/
├── backend/          # API Go (gRPC + REST gateway)
│   ├── cmd/server/   # Point d'entrée
│   ├── internal/     # Logique métier (auth, link, folder, ai...)
│   ├── proto/        # Définitions protobuf
│   └── gen/          # Code généré (buf)
├── frontend/         # App web React + Vite
│   └── src/
│       ├── components/
│       ├── pages/
│       └── api.ts
├── composeApp/       # App Android Kotlin Multiplatform
│   └── src/commonMain/kotlin/
│       ├── data/     # Modèles, repositories, ApiClient
│       ├── ui/       # Écrans Compose
│       └── viewmodel/
└── Taskfile.yml
```
