# Tribbae — Vue d'ensemble du projet

Tribbae est une application familiale de gestion d'idées (liens, recettes, activités, cadeaux, événements).
Le projet comporte 3 parties qui doivent rester cohérentes entre elles.

## Architecture

| Couche | Techno | Dossier |
|--------|--------|---------|
| App mobile Android | Kotlin Multiplatform + Jetpack Compose | `composeApp/` |
| Frontend web | React + TypeScript + Tailwind CSS + Vite | `frontend/` |
| Backend API | Go + gRPC + grpc-gateway REST + MongoDB | `backend/` |

## Règle fondamentale

Toute nouvelle feature doit être implémentée sur les DEUX clients (mobile + web).
Le backend expose une API REST via grpc-gateway ; les deux clients consomment la même API.

## Modèle de données

L'entité principale est `Link` avec les champs :
- `id`, `ownerId`, `folderId`, `title`, `url`, `description`
- `category` : LINK_CATEGORY_IDEE | LINK_CATEGORY_CADEAU | LINK_CATEGORY_ACTIVITE | LINK_CATEGORY_EVENEMENT | LINK_CATEGORY_RECETTE
- `tags` (liste), `ageRange`, `location`, `price`, `imageUrl`
- `eventDate`, `reminderEnabled`, `rating` (0-5 étoiles), `ingredients` (liste)
- `favorite` (boolean, mobile uniquement pour l'instant)

Entités secondaires : `Folder`, `Child`, `AuthResponse`.

## Catégories et couleurs

| Catégorie | Couleur | Icône |
|-----------|---------|-------|
| Idée | #FFD700 | 💡 |
| Cadeau | #FF8C00 | 🎁 |
| Activité | #4FC3F7 | 🏃 |
| Événement | #FF7043 | 📅 |
| Recette | #81C784 | 🍳 |

Ces valeurs sont définies dans :
- Mobile : `composeApp/src/commonMain/kotlin/data/Link.kt`
- Web : `frontend/src/types.ts`
- Proto : `backend/proto/tribbae/v1/link.proto`

## Commandes (Taskfile)

- `task backend` — lancer le backend
- `task frontend:dev` — lancer le frontend web
- `task build` — compiler l'app mobile Android
- `task backend:proto` — régénérer le code protobuf
