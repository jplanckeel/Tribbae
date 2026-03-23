# Design Document - Social Features Fixes

## Overview

Ce document décrit la conception technique pour corriger les bugs existants et implémenter les fonctionnalités sociales manquantes dans l'application Tribbae. Les correctionst la persistance de la visibilité des dossiers, la suppression de dossiers, l'affichage correct des pseudos, et l'implémentation complète des systèmes de suivi (follow) et de commentaires.

## Architecture

### Backend (Go)

Le backend expose une API REST via grpc-gateway. Les modifications incluent :

1. **Folder Service** : Correction de la persistance de la visibilité
2. **Link Service** : Ajout du champ visibility pour les liens publics
3. **Follow Service** : Nouveau service pour gérer les relations de suivi
4. **Comment Service** : Nouveau service pour gérer les commentaires
5. **User Service** : Extension pour inclure les statistiques sociales

### Mobile App (Kotlin Multiplatform)

L'application mobile consomme l'API REST. Les modifications incluent :

1. **FolderRepository** : Correction des appels API pour la visibilité et suppression
2. **LinkRepository** : Ajout de la gestion de la visibilité des liens
3. **FollowRepository** : Nouveau repository pour le système de suivi
4. **CommentRepository** : Nouveau repository pour les commentaires
5. **UI Components** : Mise à jour des écrans Explorer, Home, et détails

## Components and Interfaces

### Backend Components

#### 1. Follow Service

```go
type Follow struct {
    ID          primitive.ObjectID `bson:"_id,omitempty"`
    FollowerID  string             `bson:"follower_id"`  // Qui suit
    FollowingID string             `bson:"following_id"` // Qui est suivi
    CreatedAt   time.Time          `bson:"created_at"`
}

type FollowService struct {
    col     *mongo.Collection
    userCol *mongo.Collection
}

// Follow crée une relation de suivi
func (s *FollowService) Follow(ctx context.Context, followerID, followingID string) error

// Unfollow supprime une relation de suivi
func (s *FollowService) Unfollow(ctx context.Context, followerID, followingID string) error

// IsFollowing vérifie si followerID suit followingID
func (s *FollowService) IsFollowing(ctx context.Context, followerID, followingID string) (bool, error)

// GetFollowers retourne la liste des followers d'un utilisateur
func (s *FollowService) GetFollowers(ctx context.Context, userID string) ([]UserProfile, error)

// GetFollowing retourne la liste des utilisateurs suivis
func (s *FollowService) GetFollowing(ctx context.Context, userID string) ([]UserProfile, error)

// GetFollowerCount retourne le nombre de followers
func (s *FollowService) GetFollowerCount(ctx context.Context, userID string) (int32, error)

// GetFollowingCount retourne le nombre d'utilisateurs suivis
func (s *FollowService) GetFollowingCount(ctx context.Context, userID string) (int32, error)
```

#### 2. Comment Service

```go
type Comment struct {
    ID        primitive.ObjectID `bson:"_id,omitempty"`
    LinkID    string             `bson:"link_id"`
    UserID    string             `bson:"user_id"`
    Text      string             `bson:"text"`
    CreatedAt time.Time          `bson:"created_at"`
    UpdatedAt time.Time          `bson:"updated_at"`
}

type CommentService struct {
    col     *mongo.Collection
    linkCol *mongo.Collection
    userCol *mongo.Collection
}

// CreateComment crée un nouveau commentaire
func (s *CommentService) CreateComment(ctx context.Context, linkID, userID, text string) (*Comment, error)

// GetComments retourne tous les commentaires d'un lien
func (s *CommentService) GetComments(ctx context.Context, linkID string) ([]CommentWithUser, error)

// DeleteComment supprime un commentaire
func (s *CommentService) DeleteComment(ctx context.Context, commentID, userID string) error

// GetCommentCount retourne le nombre de commentaires d'un lien
func (s *CommentService) GetCommentCount(ctx context.Context, linkID string) (int32, error)
```

#### 3. Link Service Extensions

Ajout du champ `visibility` au modèle Link :

```go
type Link struct {
    // ... champs existants
    Visibility string `bson:"visibility"` // "private" | "public"
}
```

### Mobile Components

#### 1. Follow Repository

```kotlin
class FollowRepository(private val apiClient: AuthenticatedApiClient) {
    
    suspend fun follow(userId: String): Result<Unit>
    
    suspend fun unfollow(userId: String): Result<Unit>
    
    suspend fun isFollowing(userId: String): Result<Boolean>
    
    suspend fun getFollowers(userId: String): Result<List<UserProfile>>
    
    suspend fun getFollowing(userId: String): Result<List<UserProfile>>
    
    suspend fun getFollowerCount(userId: String): Result<Int>
    
    suspend fun getFollowingCount(userId: String): Result<Int>
}

data class UserProfile(
    val id: String,
    val displayName: String,
    val email: String,
    val isAdmin: Boolean,
    val followerCount: Int,
    val followingCount: Int
)
```

#### 2. Comment Repository

```kotlin
class CommentRepository(private val apiClient: AuthenticatedApiClient) {
    
    suspend fun createComment(linkId: String, text: String): Result<Comment>
    
    suspend fun getComments(linkId: String): Result<List<CommentWithUser>>
    
    suspend fun deleteComment(commentId: String): Result<Unit>
    
    suspend fun getCommentCount(linkId: String): Result<Int>
}

data class Comment(
    val id: String,
    val linkId: String,
    val userId: String,
    val text: String,
    val createdAt: String
)

data class CommentWithUser(
    val id: String,
    val linkId: String,
    val userId: String,
    val userDisplayName: String,
    val userIsAdmin: Boolean,
    val text: String,
    val createdAt: String
)
```

## Data Models

### Proto Definitions

#### follow.proto

```protobuf
syntax = "proto3";

package tribbae.v1;

import "google/api/annotations.proto";
import "google/protobuf/timestamp.proto";

message UserProfile {
  string id = 1;
  string display_name = 2;
  string email = 3;
  bool is_admin = 4;
  int32 follower_count = 5;
  int32 following_count = 6;
}

message FollowRequest {
  string user_id = 1;
}

message FollowResponse {}

message UnfollowRequest {
  string user_id = 1;
}

message UnfollowResponse {}

message IsFollowingRequest {
  string user_id = 1;
}

message IsFollowingResponse {
  bool is_following = 1;
}

message GetFollowersRequest {
  string user_id = 1;
}

message GetFollowersResponse {
  repeated UserProfile followers = 1;
}

message GetFollowingRequest {
  string user_id = 1;
}

message GetFollowingResponse {
  repeated UserProfile following = 1;
}

service FollowService {
  rpc Follow(FollowRequest) returns (FollowResponse) {
    option (google.api.http) = {
      post: "/v1/users/{user_id}/follow"
      body: "*"
    };
  }
  rpc Unfollow(UnfollowRequest) returns (UnfollowResponse) {
    option (google.api.http) = {
      delete: "/v1/users/{user_id}/follow"
    };
  }
  rpc IsFollowing(IsFollowingRequest) returns (IsFollowingResponse) {
    option (google.api.http) = {
      get: "/v1/users/{user_id}/following"
    };
  }
  rpc GetFollowers(GetFollowersRequest) returns (GetFollowersResponse) {
    option (google.api.http) = {
      get: "/v1/users/{user_id}/followers"
    };
  }
  rpc GetFollowing(GetFollowingRequest) returns (GetFollowingResponse) {
    option (google.api.http) = {
      get: "/v1/users/{user_id}/following"
    };
  }
}
```

#### comment.proto

```protobuf
syntax = "proto3";

package tribbae.v1;

import "google/api/annotations.proto";
import "google/protobuf/timestamp.proto";

message Comment {
  string id = 1;
  string link_id = 2;
  string user_id = 3;
  string user_display_name = 4;
  bool user_is_admin = 5;
  string text = 6;
  google.protobuf.Timestamp created_at = 7;
}

message CreateCommentRequest {
  string link_id = 1;
  string text = 2;
}

message CreateCommentResponse {
  Comment comment = 1;
}

message GetCommentsRequest {
  string link_id = 1;
}

message GetCommentsResponse {
  repeated Comment comments = 1;
}

message DeleteCommentRequest {
  string comment_id = 1;
}

message DeleteCommentResponse {}

service CommentService {
  rpc CreateComment(CreateCommentRequest) returns (CreateCommentResponse) {
    option (google.api.http) = {
      post: "/v1/links/{link_id}/comments"
      body: "*"
    };
  }
  rpc GetComments(GetCommentsRequest) returns (GetCommentsResponse) {
    option (google.api.http) = {
      get: "/v1/links/{link_id}/comments"
    };
  }
  rpc DeleteComment(DeleteCommentRequest) returns (DeleteCommentResponse) {
    option (google.api.http) = {
      delete: "/v1/comments/{comment_id}"
    };
  }
}
```

### Database Schema

#### follows collection

```json
{
  "_id": ObjectId,
  "follower_id": "user_id_qui_suit",
  "following_id": "user_id_qui_est_suivi",
  "created_at": ISODate
}
```

Index : `{ follower_id: 1, following_id: 1 }` (unique)

#### comments collection

```json
{
  "_id": ObjectId,
  "link_id": "link_id",
  "user_id": "user_id",
  "text": "Commentaire...",
  "created_at": ISODate,
  "updated_at": ISODate
}
```

Index : `{ link_id: 1, created_at: -1 }`

#### links collection (modification)

Ajout du champ :
```json
{
  "visibility": "private" | "public"
}
```

## 

## Error 

### Backend Error Handling

1. **Validation Errors** : Retourner `codes.InvalidArgument` avec un message descriptif
2. **Authentication Errors** : Retourner `codes.Unauthenticated` si le token est invalide
3. **Authorization Errors** : Retourner `codes.PermissionDenied` si l'utilisateur n'a pas les droits
4. **Not Found Errors** : Retourner `codes.NotFound` pour les ressources inexistantes
5. **Database Errors** : Logger l'erreur complète, retourner `codes.Internal` avec un message générique

### Mobile Error Handling

1. **Network Errors** : Afficher un message "Erreur de connexion" avec option de réessayer
2. **401 Unauthorized** : Rediriger vers l'écran de login
3. **403 Forbidden** : Afficher "Vous n'avez pas les permissions nécessaires"
4. **404 Not Found** : Afficher "Ressource introuvable"
5. **500 Internal Server Error** : Afficher "Erreur serveur, veuillez réessayer"

## Testing Strategy

### Unit Tests

#### Backend Unit Tests

1. **Folder Service**
   - Test de la persistance de la visibilité (private, public, shared)
   - Test de la suppression de dossier avec vérification du propriétaire
   - Test de la récupération des informations du propriétaire

2. **Link Service**
   - Test de la persistance de la visibilité des liens
   - Test du filtrage des liens publics
   - Test de la récupération des informations du propriétaire

3. **Follow Service**
   - Test de la création d'une relation de suivi
   - Test de la suppression d'une relation de suivi
   - Test de la vérification d'une relation existante
   - Test du comptage des followers/following
   - Test de la prévention des doublons

4. **Comment Service**
   - Test de la création d'un commentaire
   - Test de la récupération des commentaires d'un lien
   - Test de la suppression d'un commentaire par son auteur
   - Test de la suppression d'un commentaire par le propriétaire du lien
   - Test du tri des commentaires par date

#### Mobile Unit Tests

1. **FollowRepository**
   - Test des appels API follow/unfollow
   - Test de la gestion des erreurs réseau
   - Test du parsing des réponses

2. **CommentRepository**
   - Test de la création de commentaire
   - Test de la récupération des commentaires
   - Test de la suppression de commentaire

3. **UI State Management**
   - Test de la mise à jour des compteurs après follow/unfollow
   - Test de la mise à jour de la liste de commentaires après ajout/suppression

### Integration Tests

1. **Folder Visibility Flow**
   - Créer un dossier → Changer la visibilité → Vérifier la persistance → Rafraîchir → Vérifier l'affichage

2. **Folder Deletion Flow**
   - Créer un dossier → Supprimer → Vérifier la suppression → Rafraîchir → Vérifier l'absence

3. **Follow Flow**
   - Suivre un utilisateur → Vérifier le compteur → Rafraîchir → Vérifier la persistance → Ne plus suivre → Vérifier le compteur

4. **Comment Flow**
   - Créer un commentaire → Vérifier l'affichage → Rafraîchir → Vérifier la persistance → Supprimer → Vérifier la suppression

5. **Public Link Flow**
   - Créer un lien public → Vérifier dans Explorer → Rafraîchir → Vérifier la persistance

## Implementation Notes

### Bug Fixes Priority

1. **Haute priorité** (bloquants)
   - Visibilité des dossiers non persistée
   - Suppression de dossiers ne fonctionne pas
   - Pseudos affichés comme "anonyme"

2. **Moyenne priorité** (fonctionnalités manquantes)
   - Système de suivi non implémenté
   - Système de commentaires non implémenté
   - Liens publics disparaissent après rafraîchissement

3. **Basse priorité** (améliorations UX)
   - Encarts statistiques Home Screen

### Database Indexes

Pour optimiser les performances, créer les index suivants :

```javascript
// follows collection
db.follows.createIndex({ follower_id: 1, following_id: 1 }, { unique: true })
db.follows.createIndex({ following_id: 1 })

// comments collection
db.comments.createIndex({ link_id: 1, created_at: -1 })
db.comments.createIndex({ user_id: 1 })

// links collection (ajout)
db.links.createIndex({ visibility: 1, updated_at: -1 })

// folders collection (ajout)
db.folders.createIndex({ visibility: 1, like_count: -1 })
```

### API Endpoints Summary

#### Nouveaux endpoints

```
POST   /v1/users/{user_id}/follow          - Suivre un utilisateur
DELETE /v1/users/{user_id}/follow          - Ne plus suivre
GET    /v1/users/{user_id}/following       - Vérifier si on suit
GET    /v1/users/{user_id}/followers       - Liste des followers
GET    /v1/users/{user_id}/following       - Liste des following

POST   /v1/links/{link_id}/comments        - Créer un commentaire
GET    /v1/links/{link_id}/comments        - Lister les commentaires
DELETE /v1/comments/{comment_id}           - Supprimer un commentaire
```

#### Endpoints modifiés

```
PUT    /v1/folders/{folder_id}             - Correction : persister visibility
DELETE /v1/folders/{folder_id}             - Correction : vérifier owner_id
GET    /v1/links                           - Correction : inclure owner_display_name
GET    /v1/community/links                 - Correction : filtrer par visibility="public"
```

### Mobile UI Updates

#### ExploreScreen

- Afficher `link.ownerDisplayName` au lieu de "anonyme"
- Ajouter un bouton "Suivre" sur chaque carte d'idée
- Ajouter un bouton "Commentaires" avec le compteur
- Filtrer correctement les idées publiques

#### LinkDetailScreen

- Afficher la section commentaires
- Permettre d'ajouter un commentaire
- Afficher le bouton "Suivre" pour le créateur
- Afficher le pseudo du créateur

#### NewHomeScreen

- Corriger le compteur "Idées sauvegardées" : `links.count { it.favorite }`
- Corriger le compteur "Partagées" : `links.count { it.visibility == "public" }`
- Corriger le compteur "Ma tribu" : appeler `followRepository.getFollowingCount()`

#### ModernEditFolderScreen

- S'assurer que le changement de visibilité appelle correctement l'API
- Afficher un message de confirmation après la mise à jour

### Backward Compatibility

- Les liens existants sans champ `visibility` seront considérés comme "private" par défaut
- Les dossiers existants conservent leur visibilité actuelle
- Aucune migration de données n'est nécessaire

### Security Considerations

1. **Follow System**
   - Un utilisateur ne peut pas se suivre lui-même
   - Les relations de suivi sont publiques (tout le monde peut voir qui suit qui)

2. **Comment System**
   - Seul l'auteur peut supprimer son commentaire
   - Le propriétaire du lien peut supprimer n'importe quel commentaire sur son lien
   - Les commentaires sont publics si le lien est public

3. **Visibility**
   - Seul le propriétaire peut changer la visibilité d'un dossier ou lien
   - Les liens/dossiers privés ne sont jamais exposés dans les endpoints communautaires



## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property Reflection

After analyzing all acceptance criteria, several properties can be combined to eliminate redundancy:

- Properties 1.1, 1.2, 1.3 can be combined into a single property about visibility persistence
- Properties 4.6 and 4.7 can be combined into a round-trip property about follower counts
- Properties 3.1 and 7.1 can be combined into a single property about owner display names
- Properties 8.1, 8.2, 8.3 test the same pattern (count equals filter) and can be kept separate as they test different features

### Backend Properties

Property 1: Folder visibility persistence
*For any* folder and any visibility value (private, public, shared), when the visibility is updated, querying the folder from the database should return the same visibility value
**Validates: Requirements 1.1, 1.2, 1.3**

Property 2: Public folders in community listings
*For any* set of folders with mixed visibilities, the community folder listing should include all and only folders with visibility="public"
**Validates: Requirements 1.5**

Property 3: Folder deletion removes from database
*For any* folder, when deleted by its owner, the folder should no longer exist in the database
**Validates: Requirements 2.1**

Property 4: Folder deletion authorization
*For any* folder owned by user A, when user B (B ≠ A) attempts to delete it, the operation should fail with an authorization error
**Validates: Requirements 2.2**

Property 5: Folder deletion handles links
*For any* folder with associated links, when the folder is deleted, the links should either be orphaned (folder_id set to null) or deleted
**Validates: Requirements 2.5**

Property 6: Link responses include owner display name
*For any* link returned by the backend, the response should include the owner_display_name field populated from the users collection
**Validates: Requirements 3.1, 7.1**

Property 7: Follow relationship creation
*For any* two distinct users A and B, when A follows B, a follow relationship should exist in the database with follower_id=A and following_id=B
**Validates: Requirements 4.1**

Property 8: Follow/unfollow round trip
*For any* two distinct users A and B, following B then immediately unfollowing B should result in no follow relationship existing between A and B
**Validates: Requirements 4.2**

Property 9: Follower count consistency
*For any* user, the follower count should equal the number of follow relationships where that user is the following_id
**Validates: Requirements 4.6, 4.7**

Property 10: Comment storage completeness
*For any* comment created on a link, the stored comment should contain all required fields: user_id, link_id, text, and created_at timestamp
**Validates: Requirements 5.1**

Property 11: Comment deletion by author
*For any* comment, when deleted by its author (user_id matches), the comment should no longer exist in the database
**Validates: Requirements 5.4**

Property 12: Comment deletion by link owner
*For any* comment on a link, when deleted by the link's owner, the comment should no longer exist in the database
**Validates: Requirements 5.5**

Property 13: Comment sorting by date
*For any* link with multiple comments, the comments returned should be sorted by created_at in descending order (newest first)
**Validates: Requirements 5.6**

Property 14: Link visibility persistence
*For any* link and any visibility value (private, public), when the visibility is updated, querying the link from the database should return the same visibility value
**Validates: Requirements 6.1**

Property 15: Public links in community listings
*For any* set of links with mixed visibilities, the community link listing should include all and only links with visibility="public"
**Validates: Requirements 6.3**

Property 16: Link update timestamp
*For any* link, when any field is updated, the updated_at timestamp should be greater than its previous value
**Validates: Requirements 6.5**

Property 17: Favorite count accuracy
*For any* user, the count of favorite links should equal the number of links where favorite=true and owner_id equals the user's id
**Validates: Requirements 8.1**

Property 18: Public links count accuracy
*For any* user, the count of shared links should equal the number of links where visibility="public" and owner_id equals the user's id
**Validates: Requirements 8.2**

Property 19: Following count accuracy
*For any* user, the following count should equal the number of follow relationships where that user is the follower_id
**Validates: Requirements 8.3**

### Edge Cases

Edge Case 1: Empty display name handling
*When* a user has no display_name set in the database, the backend should return an empty string (not null, not "anonyme")
**Validates: Requirements 3.3, 7.3**

Edge Case 2: Self-follow prevention
*When* a user attempts to follow themselves, the operation should fail with a validation error
**Security consideration**

Edge Case 3: Duplicate follow prevention
*When* a user attempts to follow someone they already follow, the operation should be idempotent (no error, no duplicate relationship)
**Data integrity consideration**
