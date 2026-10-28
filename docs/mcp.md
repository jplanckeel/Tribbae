# Serveur MCP de Tribbae (connecteur Claude)

Tribbae expose un serveur MCP distant sur `https://tribbae.fr/mcp`. On peut l'ajouter comme **connecteur personnalisé** dans claude.ai, puis demander à Claude, y compris dans une tâche planifiée, d'ajouter des idées dans ses dossiers.

Le serveur est conçu pour être appelé par un agent qui lit des pages web non fiables. Il ne fait donc que trois choses : lister les dossiers, lister les idées d'un dossier et **ajouter une idée privée**. Aucun outil ne modifie, ne supprime ou ne publie quoi que ce soit.

## Ce que l'agent peut faire

| Outil | Droit (scope) | Effet |
|-------|---------------|-------|
| `list_folders` | `folders:read` | Dossiers du compte, de sa tribu et ceux partagés avec lui. `writable` indique où l'agent peut ajouter une idée. |
| `list_links` | `links:read` | Idées d'un dossier du compte (titre, lien, catégorie, date), 100 au plus, avec une recherche facultative. Sert à éviter les doublons. |
| `create_link` | `links:write` | Ajoute une idée dans un dossier où le compte peut écrire. |

Règles appliquées par le serveur, quoi que l'agent envoie :

- **Idée toujours privée.** Seul le titulaire du compte peut la publier, depuis l'app.
- **Pas d'écriture dans un dossier public**, parce que l'idée y deviendrait visible de tous.
- **Mêmes droits que dans l'app** : dossiers de la tribu, ou partagés avec le compte comme éditeur.
- **Adresse en `https` uniquement**, vers un site public : pas d'IP privée, pas de `localhost`, pas d'identifiants dans l'adresse.
- **Validation des champs** :
  - catégorie parmi `idee`, `cadeau`, `activite`, `sortie`, `recette`, `livre`, `deco` ;
  - 10 tags au plus, et des longueurs maximales pour chaque champ ;
  - date d'événement comprise entre un an avant et trois ans après aujourd'hui.
- **Pas d'image fournie par l'agent.** Tribbae va chercher lui-même l'image de la page, comme pour toute idée.
- **30 idées par jour et par compte** au plus (réglable), et 60 appels par minute par connexion.
- **Idées marquées** `created_via: "mcp"`, avec l'identifiant du client OAuth, pour les retrouver et les supprimer en masse.
- **Chaque appel est journalisé** dans `mcp_audit` : compte, application, outil, résultat et date, sans les arguments ni le jeton. Le journal est effacé au bout de 90 jours.

## Variables d'environnement

| Variable | Défaut | Rôle |
|----------|--------|------|
| `MCP_ENABLED` | `false` | Interrupteur général. Désactivé, `/mcp`, `/oauth/*` et `/.well-known/oauth-*` répondent 404, et plus aucun jeton n'est accepté. |
| `OAUTH_REDIRECT_URIS` | `https://claude.ai/api/mcp/auth_callback,https://claude.com/api/mcp/auth_callback` | Seules adresses de retour OAuth acceptées, comparées exactement. Aucune adresse en boucle locale : Claude Code n'est pas pris en charge. |
| `MCP_DAILY_CREATE_QUOTA` | `30` | Idées qu'un compte peut ajouter par le connecteur sur 24 h glissantes. |
| `MCP_ALLOWED_ORIGINS` | `https://claude.ai,https://claude.com` | En-têtes `Origin` acceptés sur `/mcp`, en plus de `SITE_URL`. Une requête sans `Origin` (de serveur à serveur, le cas de Claude) passe toujours. |

L'adresse de la ressource est `SITE_URL/mcp`. `SITE_URL` doit donc être exactement l'adresse publique (`https://tribbae.fr`), puisque Claude compare la ressource à l'URL saisie.

## Déploiement sur Dokploy

1. Dans l'application Tribbae de Dokploy, onglet **Environment**, ajouter `MCP_ENABLED=true`. Les autres variables ont des valeurs par défaut adaptées.
2. **Redéployer** ou redémarrer le conteneur. Aucun nouveau code n'est nécessaire : la variable est lue au démarrage.
3. Vérifier :
   ```bash
   curl -s https://tribbae.fr/.well-known/oauth-protected-resource/mcp
   curl -s https://tribbae.fr/.well-known/oauth-authorization-server
   curl -si -X POST https://tribbae.fr/mcp | head -5   # 401 avec WWW-Authenticate: Bearer resource_metadata=…
   ```

Traefik route déjà tout `tribbae.fr` vers l'application, et rien de plus n'est à configurer. Le serveur refuse les requêtes arrivées en HTTP clair : il s'appuie sur l'en-tête `X-Forwarded-Proto` posé par Traefik. Si un pare-feu filtre les entrées, les requêtes de Claude viennent de `160.79.104.0/21`.

Pour **tout couper** en urgence, mettre `MCP_ENABLED=false` puis redémarrer. Les accès déjà donnés restent en base, mais ne servent plus à rien tant que l'interrupteur est coupé.

## Ajouter le connecteur dans claude.ai

1. Dans claude.ai, ouvrir **Paramètres › Connecteurs**, puis choisir **Ajouter un connecteur personnalisé**.
2. Nom : `Tribbae`. URL : `https://tribbae.fr/mcp`. Laisser les paramètres avancés (client OAuth) vides : Claude s'enregistre tout seul.
3. Cliquer sur **Se connecter**. Claude ouvre la page Tribbae « Connecter une application ». Se connecter avec son compte Tribbae, vérifier que le site indiqué est bien `claude.ai`, relire les droits demandés puis cliquer sur **Autoriser**.
4. Dans une conversation ou une tâche planifiée, activer le connecteur Tribbae et demander par exemple : « Ajoute dans mon dossier Sorties les trois meilleures activités pour enfants à Lyon ce week-end ».

L'accès dure 90 jours au plus, et il faut le renouveler en reconnectant le connecteur. Sans usage pendant 30 jours, il expire aussi. Un changement de mot de passe le coupe immédiatement.

## Révocation

- **Depuis Claude** : déconnecter ou supprimer le connecteur.
- **Depuis Tribbae** : changer son mot de passe ferme tous les accès des applications. L'écran « Applications connectées », pour voir et révoquer chaque accès, arrive dans une PR séparée.
- **En base**, pour l'admin :
  ```js
  // Tous les accès d'un compte
  db.oauth_grants.deleteMany({ user_id: "<id>" }); db.oauth_tokens.deleteMany({ user_id: "<id>" })
  // Les idées ajoutées par une application
  db.links.deleteMany({ owner_id: "<id>", created_via: "mcp", oauth_client_id: "<client>" })
  ```
- **Tout le service** : `MCP_ENABLED=false`.

## Sécurité OAuth, en bref

- **Enregistrement dynamique (RFC 7591)** limité aux clients publics dont l'adresse de retour est dans `OAUTH_REDIRECT_URIS`. Un client que personne n'a autorisé est effacé au bout d'un jour.
- **PKCE S256 obligatoire**, et ressource liée à `/mcp` (RFC 8707).
- **Code valable 60 s, à usage unique.** Un code rejoué révoque l'accès qu'il a ouvert.
- **Access token de 15 min** et refresh token remplacé à chaque usage. Réutiliser un ancien refresh token révoque tout l'accès.
- **Jetons opaques**, stockés hachés, jamais écrits dans les journaux. Un jeton de l'app ne marche pas sur `/mcp`, et un jeton MCP ne marche pas sur l'API de l'app.
