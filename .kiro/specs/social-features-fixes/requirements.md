# Requirements Document

## Introduction

Ce document définit les exigences pour corriger et développer les fonctionnalités sociales de l'application mobile Tribbae. Les problèmes identifiés incluent des bugs dans la gestion des dossiers (visibilité, suppression), l'affichage des informations utilisateur, et des fonctionnalités sociales non implémentées (suivre, commentaires). L'objectif est de rendre l'application pleinement fonctionnelle avec des capacités sociales complètes.

## Glossary

- **Backend**: Le serveur API Go qui gère la logique métier et la persistance des données
- **Mobile App**: L'application mobile Kotlin Multiplatform avec Jetpack Compose
- **Folder**: Un dossier contenant des liens, avec une visibilité (private, shared, public)
- **Link**: Une idée/lien sauvegardé par un utilisateur
- **Visibility**: Le niveau de partage d'un dossier (private, shared, public)
- **Explorer**: L'écran permettant de découvrir les idées publiques partagées par d'autres utilisateurs
- **Home Screen**: L'écran d'accueil avec les encarts statistiques
- **Follow System**: Le système permettant de suivre d'autres utilisateurs
- **Comment System**: Le système permettant de commenter des liens/idées
- **Display Name**: Le pseudo affiché pour identifier un utilisateur
- **Owner**: Le propriétaire/créateur d'un dossier ou d'un lien

## Requirements

### Requirement 1

**User Story:** En tant qu'utilisateur, je veux que le changement de visibilité d'un dossier soit correctement enregistré, afin que mes dossiers publics soient visibles par la communauté.

#### Acceptance Criteria

1. WHEN a user updates a folder visibility to public THEN the Backend SHALL persist the visibility value as "public" in the database
2. WHEN a user updates a folder visibility to private THEN the Backend SHALL persist the visibility value as "private" in the database
3. WHEN a user updates a folder visibility to shared THEN the Backend SHALL persist the visibility value as "shared" in the database
4. WHEN a user refreshes the folder list THEN the Mobile App SHALL display the correct visibility status for each folder
5. WHEN a folder is set to public THEN the Backend SHALL include it in community folder listings

### Requirement 2

**User Story:** En tant qu'utilisateur, je veux pouvoir supprimer mes dossiers, afin de nettoyer mon espace de travail.

#### Acceptance Criteria

1. WHEN a user requests to delete a folder THEN the Backend SHALL remove the folder from the database
2. WHEN a folder is deleted THEN the Backend SHALL verify the requesting user is the owner
3. WHEN a folder deletion succeeds THEN the Mobile App SHALL remove the folder from the local list
4. WHEN a folder deletion fails THEN the Mobile App SHALL display an error message to the user
5. WHEN a folder is deleted THEN the Backend SHALL handle associated links appropriately

### Requirement 3

**User Story:** En tant qu'utilisateur explidées publiques, je veux voir le pseudo du créateur de chaque idée, afin de savoir qui partage du contenu intéressant.

#### Acceptance Criteria

1. WHEN the Backend returns a public link THEN the response SHALL include the owner's display name
2. WHEN the Mobile App displays a link in Explorer THEN the UI SHALL show the owner's display name
3. WHEN a link owner has no display name set THEN the Backend SHALL return an empty string instead of "anonyme"
4. WHEN the Mobile App receives an empty display name THEN the UI SHALL display "Anonyme" as fallback
5. WHEN the Backend fetches owner information THEN the query SHALL retrieve the display_name field from the users collection

### Requirement 4

**User Story:** En tant qu'utilisateur, je veux suivre d'autres utilisateurs qui partagent des idées intéressantes, afin de voir facilement leur contenu.

#### Acceptance Criteria

1. WHEN a user clicks the follow button on another user's profile THEN the Backend SHALL create a follow relationship
2. WHEN a user unfollows another user THEN the Backend SHALL remove the follow relationship
3. WHEN a user views their following list THEN the Mobile App SHALL display all users they follow
4. WHEN a user views their followers list THEN the Mobile App SHALL display all users following them
5. WHEN a user views a profile THEN the Mobile App SHALL indicate if they are already following that user
6. WHEN a user follows another user THEN the Backend SHALL increment the follower count for the followed user
7. WHEN a user unfollows another user THEN the Backend SHALL decrement the follower count for the followed user

### Requirement 5

**User Story:** En tant qu'utilisateur, je veux commenter les idées publiques, afin de partager mon avis et échanger avec la communauté.

#### Acceptance Criteria

1. WHEN a user submits a comment on a link THEN the Backend SHALL store the comment with user ID, link ID, text, and timestamp
2. WHEN a user views a link detail THEN the Mobile App SHALL display all comments for that link
3. WHEN a comment is displayed THEN the Mobile App SHALL show the commenter's display name and timestamp
4. WHEN a user deletes their own comment THEN the Backend SHALL remove the comment from the database
5. WHEN a link owner views comments THEN the Mobile App SHALL allow them to delete any comment on their link
6. WHEN comments are loaded THEN the Backend SHALL return them sorted by creation date (newest first)

### Requirement 6

**User Story:** En tant qu'utilisateur, je veux que mes idées publiques restent visibles après rafraîchissement, afin de maintenir ma présence dans la communauté.

#### Acceptance Criteria

1. WHEN a user sets a link visibility to public THEN the Backend SHALL persist the visibility value in the database
2. WHEN the Mobile App refreshes link data THEN the Backend SHALL return the correct visibility status for each link
3. WHEN a link is public THEN the Backend SHALL include it in community link listings
4. WHEN a user views their shared links THEN the Mobile App SHALL display all links with public visibility
5. WHEN a link visibility is updated THEN the Backend SHALL update the updated_at timestamp

### Requirement 7

**User Story:** En tant qu'utilisateur consultant mes propres idées, je veux voir mon pseudo correctement affiché, afin de confirmer que je suis l'auteur.

#### Acceptance Criteria

1. WHEN the Backend returns a link owned by the requesting user THEN the response SHALL include the user's own display name
2. WHEN the Mobile App displays a link in "Mes idées" THEN the UI SHALL show the correct owner display name
3. WHEN a user has not set a display name THEN the Backend SHALL return an empty string
4. WHEN the Mobile App receives an empty display name for own links THEN the UI SHALL display the user's email or "Moi"
5. WHEN the Backend fetches link data THEN the query SHALL join with users collection to retrieve owner information

### Requirement 8

**User Story:** En tant qu'utilisateur sur l'écran d'accueil, je veux que les encarts statistiques affichent les bonnes valeurs, afin de suivre mon activité.

#### Acceptance Criteria

1. WHEN the Home Screen displays "Idées sauvegardées" THEN the count SHALL equal the number of links marked as favorite by the user
2. WHEN the Home Screen displays "Partagées" THEN the count SHALL equal the number of links with public visibility owned by the user
3. WHEN the Home Screen displays "Ma tribu" THEN the count SHALL equal the number of users the current user is following
4. WHEN the user adds a favorite THEN the "Idées sauvegardées" count SHALL increment immediately
5. WHEN the user makes a link public THEN the "Partagées" count SHALL increment immediately
6. WHEN the user follows someone THEN the "Ma tribu" count SHALL increment immediately

