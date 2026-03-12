# Configuration des releases automatiques

## Déclenchement

Les releases sont automatiquement créées lors du push d'un tag Git au format `v*` (ex: `v1.0.0`, `v1.2.3`).

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
   - `jplanckeel/tribbae-searxng:latest` et `jplanckeel/tribbae-searxng:{version}`

## Génération d'un keystore (première fois)

```bash
keytool -genkey -v -keystore keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias tribbae
```

Conservez précieusement le fichier `keystore.jks` et les mots de passe.
