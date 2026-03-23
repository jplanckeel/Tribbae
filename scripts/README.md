# Scripts de génération de données - Tribbae

Ce dossier contient des scripts pour générer des données de test dans l'application Tribbae.

## Installation de k6

### macOS
```bash
bre
```

### Linux (Debian/Ubuntu)
```bash
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update
sudo apt-get install k6
```

### Windows
```powershell
choco install k6
```

Ou télécharger depuis : https://k6.io/docs/get-started/installation/

## Utilisation

### 1. Lancer le backend

Assurez-vous que le backend et MongoDB sont lancés :

```bash
# Terminal 1 - Backend
task backend

# Ou tout l'environnement
task dev
```

### 2. Exécuter le script de seed

```bash
# Depuis la racine du projet
k6 run scripts/seed-data.js

# Ou avec une URL personnalisée
k6 run -e BASE_URL=http://localhost:8080 scripts/seed-data.js
```

### 3. Vérifier les données

Ouvrez l'application web :
```bash
task frontend:dev
```

Puis allez sur http://localhost:5173 et connectez-vous avec un des comptes créés :
- `alice.martin0@tribbae.test` / `Test1234!`
- `bob.dubois1@tribbae.test` / `Test1234!`
- `charlie.bernard2@tribbae.test` / `Test1234!`

## Ce que le script génère

### Utilisateurs (10 par défaut)
- Prénoms : Alice, Bob, Charlie, Diana, Emma, Frank, Grace, Henry, Iris, Jack
- Noms : Martin, Dubois, Bernard, Thomas, Robert, Petit, Durand, Leroy, Moreau, Simon
- Email : `prenom.nom{index}@tribbae.test`
- Mot de passe : `Test1234!`

### Dossiers (3 par utilisateur)
Types de dossiers :
- Recettes de famille
- Idées cadeaux Noël
- Activités week-end
- Restaurants à tester
- Voyages en Europe
- Bricolage maison
- Jeux pour enfants
- Livres à lire
- Films à voir
- Sorties culturelles
- Recettes rapides
- Cadeaux anniversaire
- Activités pluvieuses
- Recettes végétariennes
- Idées déco

Chaque dossier a :
- Des tags appropriés (ex: "recette", "famille", "cuisine")
- Une visibilité (70% PUBLIC, 30% PRIVATE)
- 3 à 5 liens

### Liens (3-5 par dossier)
Catégories :
- **Recettes** : Gâteau au chocolat, Quiche lorraine, Salade César, etc.
  - Avec ingrédients
- **Cadeaux** : Lego, Livres, Jeux de société, etc.
  - Avec prix et âge recommandé
- **Activités** : Parc Astérix, Musée, Accrobranche, etc.
  - Avec lieu, prix et âge
- **Idées** : Pique-nique, Soirée jeux, Bricolage, etc.
- **Événements** : Anniversaire, Noël, Vacances, etc.
  - Avec date d'événement

Chaque lien a :
- Titre et description
- URL
- Note (3-5 étoiles)
- Tags du dossier parent
- Champs spécifiques selon la catégorie

### Likes
- Chaque utilisateur like 3-8 dossiers publics aléatoires
- Permet de tester le système de popularité

## Configuration

Vous pouvez modifier les constantes en haut du fichier `seed-data.js` :

```javascript
const NUM_USERS = 10;              // Nombre d'utilisateurs
const NUM_FOLDERS_PER_USER = 3;    // Dossiers par utilisateur
const NUM_LINKS_PER_FOLDER = 5;    // Liens par dossier (max)
```

## Nettoyage des données

Pour supprimer toutes les données de test :

```bash
# Arrêter le backend
# Puis supprimer la base MongoDB
docker exec -it tribbae-mongo mongosh tribbae --eval "db.dropDatabase()"

# Ou recréer complètement
docker compose -f docker-compose.local.yml down -v
docker compose -f docker-compose.local.yml up -d
```

## Résolution de problèmes

### Erreur "connection refused"
Le backend n'est pas lancé. Vérifiez avec :
```bash
curl http://localhost:8080/v1/community/top
```

### Erreur "user already exists"
Les utilisateurs existent déjà. Nettoyez la base ou modifiez les emails dans le script.

### Timeout
Le script prend du temps (2-5 minutes). C'est normal. Vous pouvez :
- Réduire `NUM_USERS` ou `NUM_FOLDERS_PER_USER`
- Augmenter le `maxDuration` dans les options k6

### Erreur 401/403
Problème d'authentification. Vérifiez que le JWT_SECRET est le même dans le backend.

## Exemples de tests

### Test de charge
```bash
# 10 utilisateurs virtuels pendant 30 secondes
k6 run --vus 10 --duration 30s scripts/seed-data.js
```

### Test de stress
```bash
# Augmenter progressivement jusqu'à 50 VUs
k6 run --stages "0s:0,10s:10,20s:50,30s:0" scripts/seed-data.js
```

## Intégration avec Taskfile

Ajoutez dans `Taskfile.yml` :

```yaml
seed:
  desc: "Générer des données de test"
  cmds:
    - k6 run scripts/seed-data.js

seed:clean:
  desc: "Nettoyer les données de test"
  cmds:
    - docker exec -it tribbae-mongo mongosh tribbae --eval "db.dropDatabase()"
    - echo "Base de données nettoyée"
```

Puis utilisez :
```bash
task seed        # Générer les données
task seed:clean  # Nettoyer
```

## Données générées - Aperçu

Après exécution, vous aurez :
- ✅ 10 utilisateurs avec des noms réalistes
- ✅ ~30 dossiers (70% publics, 30% privés)
- ✅ ~120 liens avec données complètes
- ✅ ~50 likes sur les dossiers publics
- ✅ Tags appropriés pour chaque catégorie
- ✅ Données variées (recettes, cadeaux, activités, etc.)

Parfait pour :
- Tester l'interface utilisateur
- Démonstrations
- Tests de performance
- Développement de nouvelles fonctionnalités

