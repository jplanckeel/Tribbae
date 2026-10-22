# Configuration des releases automatiques

## Déclenchement

Les releases sont gérées par [release-please](https://github.com/googleapis/release-please) (`.github/workflows/release-please.yml`) à partir des messages [Conventional Commits](https://www.conventionalcommits.org/) des commits arrivés sur `main` :

1. À chaque push sur `main`, release-please ouvre ou met à jour une PR `chore(main): release x.y.z`. Elle calcule la prochaine version (`feat` → minor, `fix` → patch, `!` → major ; tant qu'on est en `0.x`, un breaking change ne passe qu'en minor), met à jour `CHANGELOG.md`, `.release-please-manifest.json`, `frontend/package.json` et `versionName` dans `composeApp/build.gradle.kts`.
2. Merger cette PR crée le tag `vx.y.z` et la release GitHub avec son changelog, puis lance `release.yml` (APK + images Docker).

Configuration : `release-please-config.json` (sections du changelog, fichiers versionnés) et `.release-please-manifest.json` (version courante).

Prérequis côté dépôt : *Settings > Actions > General > Workflow permissions* → cocher **Allow GitHub Actions to create and approve pull requests**. Les PR ouvertes par release-please avec `GITHUB_TOKEN` ne déclenchent pas la CI `Test` ; ce n'est pas bloquant, elles ne touchent qu'aux numéros de version et au changelog.

Un tag poussé à la main reste possible (la release est alors créée avec des notes générées automatiquement) :

```bash
git tag v1.0.0
git push origin v1.0.0
```

## Secrets GitHub requis

Configurez ces secrets dans `Settings > Secrets and variables > Actions` :

### Docker Hub (obligatoire)
- `DOCKER_USERNAME` : Nom d'utilisateur Docker Hub (ex: `jplanckeel`)
- `DOCKER_PASSWORD` : Token d'accès Docker Hub

### Signature APK (optionnel)
Si vous souhaitez signer l'APK automatiquement :

- `KEYSTORE_FILE` : Fichier keystore encodé en base64
  ```bash
  base64 -w 0 keystore.jks
  ```
- `KEYSTORE_PASSWORD` : Mot de passe du keystore
- `KEY_PASSWORD` : Mot de passe de la clé
- `KEY_ALIAS` : Alias de la clé (ex: `tribbae`)

> Si ces secrets ne sont pas configurés, l'APK sera généré non signé (debug).

## Résultat

Chaque tag déclenche :

1. **Build APK Android** → Ajouté à la release GitHub (`tribbae-{version}.apk`)
2. **Build Docker images** → Poussées sur Docker Hub avec tags :
   - `jplanckeel/tribbae:latest` et `jplanckeel/tribbae:{version}`

## Génération d'un keystore (première fois)

```bash
keytool -genkey -v -keystore keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias tribbae
```

Conservez précieusement le fichier `keystore.jks` et les mots de passe.
