---
inclusion: fileMatch
filatchPattern: "composeApp/**/*.kt"
---

# Développement Mobile Android - Tribbae

## Stack Technique
- **Framework**: Kotlin Multiplatform + Jetpack Compose
- **Architecture**: MVVM avec ViewModel et Repository
- **UI**: Material Design 3 avec design moderne personnalisé
- **Navigation**: Navigation par états (SubScreen)
- **Stockage local**: SharedPreferences via SessionManager
- **Réseau**: HttpURLConnection pour les appels API REST

## Structure du Projet Mobile

```
composeApp/
├── src/commonMain/kotlin/
│   ├── data/              # Modèles de données et repositories
│   │   ├── Link.kt        # Entité principale
│   │   ├── Folder.kt      # Dossiers
│   │   ├── LinkRepository.kt
│   │   ├── AuthRepository.kt
│   │   ├── SessionManager.kt
│   │   └── ApiClient.kt
│   ├── viewmodel/         # ViewModels
│   │   └── LinkViewModel.kt
│   ├── ui/                # Composables UI
│   │   ├── NewHomeScreen.kt
│   │   ├── ExploreScreen.kt
│   │   ├── MyIdeasScreen.kt
│   │   ├── ProfileModernScreen.kt
│   │   ├── ModernAddLinkScreen.kt
│   │   ├── ModernEditLinkScreen.kt
│   │   └── components/    # Composants réutilisables
│   └── AppModern.kt       # Point d'entrée principal
```

## Règles de Développement

### 1. Composables
- Toujours utiliser `@Composable` pour les fonctions UI
- Préférer les composables sans état (stateless) quand possible
- Utiliser `remember` pour les états locaux
- Utiliser `collectAsState()` pour observer les StateFlow du ViewModel

### 2. Gestion d'État
- Les données métier sont dans le `LinkViewModel`
- Les états UI locaux sont dans les composables avec `remember`
- Utiliser `StateFlow` pour exposer les données du ViewModel
- Toujours utiliser `by` pour déléguer les StateFlow

### 3. Navigation
- Navigation gérée par états `SubScreen` dans `AppModern.kt`
- Utiliser `BackHandler` pour gérer le bouton retour
- Passer les callbacks `onNavigate*` pour la navigation

### 4. Design System

#### Couleurs
```kotlin
val Orange = Color(0xFFF97316)
val OrangeLight = Color(0xFFFFF7ED)
val TextPrimary = Color(0xFF111827)
val TextSecondary = Color(0xFF6B7280)
val CardColor = Color(0xFFFAFAFA)
val SurfaceColor = Color(0xFFF9FAFB)
```

#### Catégories avec couleurs
- Idée: #FFD700 💡
- Cadeau: #FF8C00 🎁
- Activité: #4FC3F7 🏃
- Événement: #FF7043 📅
- Recette: #81C784 🍳
- Livre: #9C27B0 📚
- Décoration: #E91E63 🎨

#### Composants Standards
- Cards: `RoundedCornerShape(16.dp)` avec `shadowElevation = 2.dp`
- Boutons: `RoundedCornerShape(12.dp)` ou `RoundedCornerShape(16.dp)`
- Spacing: 8.dp, 12.dp, 16.dp, 20.dp
- Padding: 16.dp pour les conteneurs, 20.dp pour les écrans

### 5. API et Backend
- URL Backend: `https://tribbae.bananaops.cloud`
- Format JSON: **camelCase** (userId, displayName, etc.)
- Authentification: JWT token dans header `Authorization: Bearer <token>`
- Timeout: 10 secondes pour connexion et lecture

### 6. Gestion des Erreurs
- Toujours utiliser try-catch pour les appels réseau
- Logger les erreurs avec `println("DEBUG: ...")`
- Afficher des messages d'erreur clairs à l'utilisateur
- Gérer les cas HTTP 401, 404, 409, 500

### 7. Pull-to-Refresh
- Utiliser `PullToRefreshBox` avec `viewModel.forceSync()`
- Gérer l'état `isRefreshing` depuis le ViewModel
- Implémenter dans tous les écrans de liste

### 8. Formulaires
- Tags: ajout un par un avec suggestions
- Dossiers: menu déroulant avec icônes
- Images: galerie, caméra ou URL
- Validation: désactiver le bouton si champs requis vides

### 9. Performance
- Utiliser `remember` pour éviter les recalculs
- Utiliser `LazyColumn` pour les listes longues
- Éviter les recompositions inutiles
- Utiliser `key` dans les listes pour optimiser

### 10. Tests
- Toujours tester sur un appareil réel ou émulateur
- Vérifier les logs avec `println("DEBUG: ...")`
- Tester la connexion réseau
- Tester les cas d'erreur

## Patterns Communs

### Écran avec Liste
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyScreen(
    viewModel: LinkViewModel,
    modifier: Modifier = Modifier
) {
    val items by viewModel.items.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()
    
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.forceSync() },
        state = pullRefreshState,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn {
            items(items) { item ->
                ItemCard(item = item)
            }
        }
    }
}
```

### Formulaire avec Validation
```kotlin
@Composable
fun FormScreen(
    viewModel: LinkViewModel,
    onBack: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val isValid = title.isNotBlank()
    
    Column {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Titre") }
        )
        
        Button(
            onClick = { /* save */ },
            enabled = isValid
        ) {
            Text("Enregistrer")
        }
    }
}
```

## Debugging
- Utiliser `println("DEBUG: ...")` pour logger
- Vérifier logcat dans Android Studio
- Filtrer par "System.out" ou "DEBUG"
- Toujours logger les exceptions avec `e.printStackTrace()`

