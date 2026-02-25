# LinkKeeper

Application Android Kotlin Multiplatform pour gérer des listes de liens organisés.

## Fonctionnalités

- Créer des liens avec titre, URL, description
- Catégories: Idées, Cadeaux, Activités, Événements, Recettes
- Tags personnalisés
- Informations: âge, lieu, prix
- Recherche dans tous les champs
- Interface Material Design 3

## Build

```bash
./gradlew :composeApp:assembleDebug
```

## Structure

- `data/` - Modèles et repository
- `ui/` - Écrans Compose
- `viewmodel/` - Logique métier
