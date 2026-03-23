---
inclusion: fileMatch
fileMatchPattern: "backend/**/*.go"
---
 API - Tribbae

## Stack Technique
- **Langage**: Go 1.21+
- **Framework RPC**: gRPC avec grpc-gateway pour REST
- **Base de données**: MongoDB
- **Authentification**: JWT
- **Génération de code**: Protocol Buffers (protobuf)

## Structure du Backend

```
backend/
├── proto/tribbae/v1/      # Définitions protobuf
│   ├── auth.proto
│   ├── link.proto
│   ├── folder.proto
│   └── child.proto
├── gen/tribbae/v1/        # Code généré (ne pas modifier)
├── internal/
│   ├── auth/              # Service d'authentification
│   ├── link/              # Service de gestion des liens
│   ├── folder/            # Service de gestion des dossiers
│   └── child/             # Service de gestion des enfants
├── cmd/server/            # Point d'entrée
└── scripts/               # Scripts utilitaires
```

## Endpoints API REST

### Authentification
- `POST /v1/auth/register` - Inscription
- `POST /v1/auth/login` - Connexion
- `POST /v1/auth/refresh` - Rafraîchir le token

### Links (Idées)
- `GET /v1/links` - Liste des liens
- `POST /v1/links` - Créer un lien
- `GET /v1/links/{id}` - Détails d'un lien
- `PUT /v1/links/{id}` - Modifier un lien
- `DELETE /v1/links/{id}` - Supprimer un lien
- `POST /v1/links/{id}/like` - Liker un lien
- `DELETE /v1/links/{id}/like` - Unliker un lien

### Folders (Dossiers)
- `GET /v1/folders` - Liste des dossiers
- `POST /v1/folders` - Créer un dossier
- `GET /v1/folders/{id}` - Détails d'un dossier
- `PUT /v1/folders/{id}` - Modifier un dossier
- `DELETE /v1/folders/{id}` - Supprimer un dossier
- `POST /v1/folders/{id}/share` - Partager un dossier
- `POST /v1/folders/{id}/collaborators` - Ajouter un collaborateur
- `DELETE /v1/folders/{id}/collaborators/{userId}` - Retirer un collaborateur

### Children (Enfants)
- `GET /v1/children` - Liste des enfants
- `POST /v1/children` - Créer un enfant
- `PUT /v1/children/{id}` - Modifier un enfant
- `DELETE /v1/children/{id}` - Supprimer un enfant

## Format des Données

### JSON Response Format
Le backend REST (via grpc-gateway) renvoie du JSON en **camelCase** :
```json
{
  "userId": "123",
  "displayName": "John",
  "createdAt": "2024-01-01T00:00:00Z"
}
```

### Catégories
Les catégories sont préfixées par `LINK_CATEGORY_` dans les protos :
- `LINK_CATEGORY_IDEE`
- `LINK_CATEGORY_CADEAU`
- `LINK_CATEGORY_ACTIVITE`
- `LINK_CATEGORY_EVENEMENT`
- `LINK_CATEGORY_RECETTE`
- `LINK_CATEGORY_LIVRE`
- `LINK_CATEGORY_DECORATION`

## Règles de Développement

### 1. Protobuf
- Toujours définir les messages dans `proto/tribbae/v1/`
- Utiliser `snake_case` pour les noms de champs
- Ajouter les annotations HTTP pour grpc-gateway
- Régénérer le code avec `task backend:proto`

### 2. Services
- Un service par domaine (auth, link, folder, child)
- Handler pour la couche gRPC
- Service pour la logique métier
- Repository pour l'accès aux données

### 3. Authentification
- JWT avec expiration de 30 jours
- Token dans header `Authorization: Bearer <token>`
- Middleware pour vérifier le token
- User ID extrait du token

### 4. Base de Données
- MongoDB avec collections séparées
- Index sur les champs fréquemment recherchés
- Utiliser `bson.M` pour les requêtes
- Gérer les erreurs `mongo.ErrNoDocuments`

### 5. Erreurs
- Utiliser `status.Error` pour les erreurs gRPC
- Codes appropriés: `codes.NotFound`, `codes.InvalidArgument`, etc.
- Messages d'erreur clairs et en anglais
- Logger les erreurs avec contexte

### 6. Validation
- Valider les entrées dans les handlers
- Vérifier les champs requis
- Valider les formats (email, etc.)
- Retourner `codes.InvalidArgument` si invalide

## Commandes Utiles

```bash
# Lancer le backend
task backend

# Régénérer le code protobuf
task backend:proto

# Tester un endpoint
curl -X POST https://tribbae.bananaops.cloud/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password"}'
```

## Exemple de Service

```go
package link

import (
    "context"
    pb "github.com/tribbae/backend/gen/tribbae/v1"
    "google.golang.org/grpc/codes"
    "google.golang.org/grpc/status"
)

type Handler struct {
    pb.UnimplementedLinkServiceServer
    svc *Service
}

func (h *Handler) CreateLink(ctx context.Context, req *pb.CreateLinkRequest) (*pb.Link, error) {
    // Extraire l'user ID du contexte (JWT)
    userID := getUserIDFromContext(ctx)
    if userID == "" {
        return nil, status.Error(codes.Unauthenticated, "not authenticated")
    }
    
    // Valider les entrées
    if req.Title == "" {
        return nil, status.Error(codes.InvalidArgument, "title is required")
    }
    
    // Appeler le service
    link, err := h.svc.CreateLink(ctx, userID, req)
    if err != nil {
        return nil, status.Error(codes.Internal, err.Error())
    }
    
    return link, nil
}
```

## Déploiement
- Backend déployé sur `https://tribbae.bananaops.cloud`
- MongoDB hébergé sur MongoDB Atlas
- Variables d'environnement pour la configuration
- Logs centralisés
