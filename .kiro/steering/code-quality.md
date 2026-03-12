---
inclusion: always
---

# Qualité de Code - Tribbae

## Principes Généraux

### 1. Lisibilité
- Code clair et explicite
- Noms de variables et fonctions descriptifs
- Commentaires uniquement pour expliquer le "pourquoi", pas le "quoi"
- Éviter les abréviations obscures

### 2. Simplicité
- KISS (Keep It Simple, Stupid)
- Éviter la sur-ingénierie
- Une fonction = une responsabilité
- Code minimal et efficace

### 3. Cohérence
- Suivre les conventions du langage
- Style uniforme dans tout le projet
- Patterns similaires pour des problèmes similaires

### 4. Maintenabilité
- Code facile à modifier
- Dépendances minimales
- Tests pour les fonctionnalités critiques
- Documentation à jour

## Standards par Langage

### Kotlin (Mobile)
```kotlin
// ✅ BON
fun calculateTotalPrice(items: List<Item>): Double {
    return items.sumOf { it.price }
}

// ❌ MAUVAIS
fun calc(i: List<Item>): Double {
    var t = 0.0
    for (x in i) {
        t += x.price
    }
    return t
}
```

### TypeScript (Frontend)
```typescript
// ✅ BON
interface UserProfile {
  id: string;
  displayName: string;
  email: string;
}

function getUserProfile(userId: string): Promise<UserProfile> {
  return fetch(`/api/users/${userId}`).then(res => res.json());
}

// ❌ MAUVAIS
function getUser(id: any): any {
  return fetch(`/api/users/${id}`).then(res => res.json());
}
```

### Go (Backend)
```go
// ✅ BON
func (s *Service) CreateLink(ctx context.Context, userID string, req *pb.CreateLinkRequest) (*pb.Link, error) {
    if req.Title == "" {
        return nil, errors.New("title is required")
    }
    
    link := &Link{
        ID:      generateID(),
        OwnerID: userID,
        Title:   req.Title,
    }
    
    if err := s.repo.Insert(ctx, link); err != nil {
        return nil, fmt.Errorf("failed to insert link: %w", err)
    }
    
    return link.ToProto(), nil
}

// ❌ MAUVAIS
func (s *Service) CreateLink(ctx context.Context, u string, r *pb.CreateLinkRequest) (*pb.Link, error) {
    l := &Link{ID: generateID(), OwnerID: u, Title: r.Title}
    s.repo.Insert(ctx, l)
    return l.ToProto(), nil
}
```

## Gestion des Erreurs

### Mobile (Kotlin)
```kotlin
// ✅ BON
try {
    val response = authRepository.login(email, password)
    sessionManager.saveSession(response.userId, response.token, response.displayName)
    onSuccess()
} catch (e: Exception) {
    println("DEBUG: Login failed - ${e.message}")
    e.printStackTrace()
    onError(when {
        e.message?.contains("401") == true -> "Email ou mot de passe incorrect"
        e.message?.contains("timeout") == true -> "Timeout de connexion"
        else -> "Erreur: ${e.message}"
    })
}

// ❌ MAUVAIS
try {
    val response = authRepository.login(email, password)
    sessionManager.saveSession(response.userId, response.token, response.displayName)
    onSuccess()
} catch (e: Exception) {
    onError("Erreur")
}
```

### Frontend (TypeScript)
```typescript
// ✅ BON
async function login(email: string, password: string): Promise<AuthResponse> {
  try {
    const response = await fetch(`${API_URL}/v1/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password })
    });
    
    if (!response.ok) {
      const error = await response.text();
      throw new Error(`HTTP ${response.status}: ${error}`);
    }
    
    return await response.json();
  } catch (error) {
    console.error('Login failed:', error);
    throw error;
  }
}

// ❌ MAUVAIS
async function login(email: string, password: string) {
  const response = await fetch(`${API_URL}/v1/auth/login`, {
    method: 'POST',
    body: JSON.stringify({ email, password })
  });
  return response.json();
}
```

### Backend (Go)
```go
// ✅ BON
func (h *Handler) CreateLink(ctx context.Context, req *pb.CreateLinkRequest) (*pb.Link, error) {
    userID := getUserIDFromContext(ctx)
    if userID == "" {
        return nil, status.Error(codes.Unauthenticated, "not authenticated")
    }
    
    if req.Title == "" {
        return nil, status.Error(codes.InvalidArgument, "title is required")
    }
    
    link, err := h.svc.CreateLink(ctx, userID, req)
    if err != nil {
        log.Printf("Failed to create link: %v", err)
        return nil, status.Error(codes.Internal, "failed to create link")
    }
    
    return link, nil
}

// ❌ MAUVAIS
func (h *Handler) CreateLink(ctx context.Context, req *pb.CreateLinkRequest) (*pb.Link, error) {
    userID := getUserIDFromContext(ctx)
    link, _ := h.svc.CreateLink(ctx, userID, req)
    return link, nil
}
```

## Logging et Debugging

### Règles de Logging
1. Logger les erreurs avec contexte
2. Logger les opérations importantes (login, création, suppression)
3. Utiliser des préfixes clairs (`DEBUG:`, `ERROR:`, `INFO:`)
4. Ne pas logger de données sensibles (mots de passe, tokens complets)

### Mobile (Kotlin)
```kotlin
println("DEBUG: Login attempt for email=$email")
println("DEBUG: Response received - userId=${response.userId}, displayName=${response.displayName}")
println("ERROR: Login failed - ${e.message}")
e.printStackTrace()
```

### Frontend (TypeScript)
```typescript
console.log('DEBUG: Fetching links for user', userId);
console.error('ERROR: Failed to fetch links:', error);
```

### Backend (Go)
```go
log.Printf("INFO: User %s logged in", userID)
log.Printf("ERROR: Failed to create link: %v", err)
```

## Tests

### Quand Tester
- Fonctionnalités critiques (auth, paiement)
- Logique métier complexe
- Fonctions utilitaires
- API endpoints

### Ce qu'il faut tester
- Cas nominaux (happy path)
- Cas d'erreur
- Cas limites (edge cases)
- Validation des entrées

## Performance

### Mobile
- Utiliser `remember` pour éviter les recalculs
- `LazyColumn` pour les listes longues
- Éviter les recompositions inutiles
- Charger les images de manière asynchrone

### Frontend
- Lazy loading des composants
- Mémoïsation avec `useMemo`/`useCallback`
- Optimiser les images (compression, formats modernes)
- Code splitting

### Backend
- Index sur les champs fréquemment recherchés
- Pagination pour les listes
- Cache pour les données fréquemment accédées
- Connection pooling pour la base de données

## Sécurité

### Règles de Base
1. Ne jamais stocker de mots de passe en clair
2. Valider toutes les entrées utilisateur
3. Utiliser HTTPS pour toutes les communications
4. Tokens JWT avec expiration
5. Pas de données sensibles dans les logs

### Validation
```kotlin
// Mobile
if (email.isBlank() || !email.contains("@")) {
    onError("Email invalide")
    return
}

if (password.length < 8) {
    onError("Mot de passe trop court")
    return
}
```

```go
// Backend
if req.Email == "" || !strings.Contains(req.Email, "@") {
    return nil, status.Error(codes.InvalidArgument, "invalid email")
}

if len(req.Password) < 8 {
    return nil, status.Error(codes.InvalidArgument, "password too short")
}
```

## Revue de Code

### Checklist
- [ ] Le code compile sans erreur
- [ ] Les tests passent
- [ ] Le code suit les conventions du projet
- [ ] Les erreurs sont gérées correctement
- [ ] Les logs sont appropriés
- [ ] Pas de données sensibles exposées
- [ ] La documentation est à jour
- [ ] Le code est lisible et maintenable

## Git

### Messages de Commit
```
feat: ajout du filtre par catégorie dans ExploreScreen
fix: correction du mapping JSON pour displayName
refactor: simplification de la gestion des erreurs
docs: mise à jour du README avec les nouvelles commandes
```

### Branches
- `main` - production
- `develop` - développement
- `feature/nom-feature` - nouvelles fonctionnalités
- `fix/nom-bug` - corrections de bugs
