# Tribbae

Application familiale de gestion d'idées — liens, recettes, activités, cadeaux, événements.

## Architecture

| Couche | Techno | Dossier |
|--------|--------|---------|
| App mobile Android | Kotlin Multiplatform + Jetpack Compose | `composeApp/` |
| Frontend web | React + TypeScript + Tailwind CSS + Vite | `frontend/` |
| Backend API | Go + gRPC + grpc-gateway REST + MongoDB | `backend/` |
| IA | Ollama (modèle local) | `backend/internal/ai/` |

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

# Démarrer Ollama + backend + frontend
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

### Mobile Android

| Commande | Description |
|----------|-------------|
| `task build` | Compile l'APK debug |
| `task install` | Compile et installe sur un appareil connecté |
| `task run` | Compile, installe et lance l'app |
| `task logs` | Affiche les logs ADB |

## Fonctionnalités

### Gestion d'idées
- 5 catégories : 💡 Idée · 🎁 Cadeau · 🏃 Activité · 📅 Événement · 🍳 Recette
- Organisation en listes (dossiers) avec icônes et couleurs personnalisables
- Filtres avancés : catégorie, liste, enfant, favoris, recherche
- Vue liste ou grille
- Métadonnées : tags, âge, lieu, prix, note (étoiles), ingrédients
- Extraction automatique d'images (Open Graph)

### Collaboration et partage
- Listes privées, partagées ou publiques
- Système de collaborateurs avec rôles (viewer/editor)
- Partage par lien (token)
- Communauté : explorer les listes publiques d'autres utilisateurs
- Affichage du nom du propriétaire et badge admin

### Synchronisation
- Synchronisation automatique entre mobile et web
- Pull-to-refresh sur toutes les pages (Accueil, Explorer, Listes)
- Mode hors ligne : création locale puis sync à la connexion
- Gestion intelligente des conflits (backend = source de vérité)

### Profils enfants
- Création de profils avec date de naissance
- Filtrage automatique des idées par âge
- Calcul dynamique de l'âge en mois

### Génération IA (Tribbae+)
- Génération d'idées par IA locale (Ollama)
- Petit modèle pour un VPS sans GPU : `qwen3:1.7b` (téléchargé au démarrage)
- Prompts suggérés : anniversaire, activités, recettes, cadeaux
- Génération structurée avec métadonnées complètes
- Badge "Expérimental" sur la fonctionnalité

### Autres fonctionnalités
- Agenda des événements avec rappels
- Liste de courses (extraction des ingrédients)
- Système de likes sur les idées et listes publiques
- Favoris (cœur) pour marquer ses idées préférées
- Sauvegarde d'idées publiques dans ses propres listes
- App mobile Android native + web responsive
- Authentification JWT avec gestion de session
- Interface Material Design 3 (mobile) et Tailwind CSS (web)

## Variables d'environnement

Copier `backend/.env.example` en `backend/.env` et adapter :

```env
MONGO_URI=mongodb://localhost:27017
MONGO_DB=tribbae
JWT_SECRET=change-me-in-production
PORT=8080
GRPC_PORT=9090
BASE_URL=http://localhost:8080

# IA - Ollama (modèle local)
OLLAMA_URL=http://localhost:11434
OLLAMA_MODEL=qwen3:1.7b
```

## Structure du projet

```
tribbae/
├── backend/          # API Go (gRPC + REST gateway)
│   ├── cmd/server/   # Point d'entrée
│   ├── internal/     # Logique métier (auth, link, folder, ai, admin...)
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

## Déploiement

Le projet inclut des workflows GitHub Actions pour le CI/CD :

- `.github/workflows/test.yml` : Tests et validation
- `.github/workflows/release.yml` : Build et déploiement automatique

Les images Docker sont publiées sur Docker Hub :
- `jplanckeel/tribbae` : Backend + Frontend

Déploiement avec Docker Compose :

```bash
docker-compose up -d
```

## Licence

MIT
