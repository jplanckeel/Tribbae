# Tribbae Backend

API REST · Go · MongoDB

## Prérequis

- Go 1.22+
- MongoDB

## Lancer le serveur

```bash
cd backend
cp .env.example .env
# éditer .env

go mod tidy
go run ./cmd/server
```

## Routes

### Auth (public)
| Méthode | Route | Description |
|---|---|---|
| POST | `/auth/register` | Créer un compte |
| POST | `/auth/login` | Se connecter |
| POST | `/auth/refresh` | Rafraîchir le token |

### Folders (JWT requis)
| Méthode | Route | Description |
|---|---|---|
| POST | `/folders` | Créer un dossier |
| GET | `/folders` | Lister ses dossiers |
| GET | `/folders/{id}` | Détail d'un dossier |
| PUT | `/folders/{id}` | Modifier un dossier |
| DELETE | `/folders/{id}` | Supprimer un dossier |
| POST | `/folders/{id}/share` | Générer un lien de partage |

### Partage (public)
| Méthode | Route | Description |
|---|---|---|
| GET | `/share/{token}` | Voir un dossier partagé |

### Links (JWT requis)
| Méthode | Route | Description |
|---|---|---|
| POST | `/links` | Créer un lien |
| GET | `/links?folder_id=xxx` | Lister ses liens |
| GET | `/links/{id}` | Détail d'un lien |
| PUT | `/links/{id}` | Modifier un lien |
| DELETE | `/links/{id}` | Supprimer un lien |

## Authentification

Header : `Authorization: Bearer <token>`

## Exemples

```bash
# Register
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"secret","display_name":"Alice"}'

# Login
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"secret"}'

# Créer un dossier
curl -X POST http://localhost:8080/folders \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"name":"Recettes","icon":"RESTAURANT","color":"ORANGE","visibility":"public"}'
```
