# DeboPoly (app Android)

App Android « coque » **DeboPoly** pour **Lexis Poly**, le logiciel web de LexisNexis pour les cabinets d'avocats. Elle affiche
**uniquement l'interface web officielle** (`https://www.lexispoly.fr/lexispoly/`) dans une WebView sécurisée :
aucune API privée, aucun traitement des données du cabinet par l'app.

> Application indépendante, non éditée par LexisNexis. « Lexis Poly » et « LexisNexis » sont des marques de
> leurs propriétaires ; l'icône de l'app reprend le logo LexisNexis.

Construite à partir du modèle `lieluinit` (Kotlin, Compose, même CI, même mise à jour intégrée depuis les
releases GitHub). La sauvegarde automatique du modèle est **retirée** : rien ne doit quitter l'appareil.

## Configuration

Tout est dans [`app/src/main/java/com/lielu/debopoly/config/AppConfig.kt`](app/src/main/java/com/lielu/debopoly/config/AppConfig.kt).

| Constante | Rôle | Valeur livrée |
| --- | --- | --- |
| `BASE_URL` | Page chargée au lancement (redirige vers la connexion si besoin). HTTPS obligatoire, domaine autorisé. | `https://www.lexispoly.fr/lexispoly/` |
| `ALLOWED_DOMAINS` | **Liste blanche** : ces domaines **et leurs sous-domaines** s'ouvrent dans l'app. Tout autre lien part dans le navigateur. | `lexispoly.fr`, `lexisnexis.fr`, `lexisnexis.com` |
| `SSO_DOMAINS` | Fournisseurs d'identité à garder dans l'app si le cabinet se connecte via un SSO externe (ex. `login.microsoftonline.com`). | vide |
| `DEFAULT_LOCK_TIMEOUT_MINUTES` / `LOCK_TIMEOUT_CHOICES` | Délai de reverrouillage par défaut et choix proposés. | 5 min ; 0, 1, 5, 15, 30 |

Règle de correspondance : `lexispoly.fr` autorise `www.lexispoly.fr` et `authcore.lexispoly.fr`, mais pas
`evil-lexispoly.fr` ni `lexispoly.fr.example.com`. Si la connexion de votre cabinet redirige ailleurs, l'app
affiche « Ouvert dans le navigateur » avec le domaine en cause : c'est lui qu'il faut ajouter à `SSO_DOMAINS`.

## Fonctionnement

| Sujet | Comportement |
| --- | --- |
| Session | Cookies persistants (y compris tiers, pour le SSO), vidés sur disque à chaque mise en arrière-plan : la session survit au redémarrage. |
| Navigation | Bouton retour : historique de la page, puis sortie. Tirer pour actualiser (seulement en haut de la page), barre de progression fine, écran hors ligne avec « Réessayer » et nouvel essai automatique au retour du réseau. |
| Rotation / pliage | `configChanges` : rotation, pliage/dépliage (Galaxy Z Fold), multi-fenêtre, clair/sombre ne recréent pas l'écran ; la page et la saisie en cours sont conservées. Après une mort du processus, l'historique est restauré. |
| Agenda sur téléphone | Sur les pages `/Agenda/`, le script `res/raw/agenda_mobile.js` fait passer le calendrier (Kendo UI Scheduler), en portrait, dans sa vue « agenda » standard (rendez-vous regroupés par jour), ouverte sur aujourd'hui, sur toute la largeur sous le menu du site (qui reste visible). Bouton « Filtres » pour revenir à la page normale ; vue d'origine en paysage. Purement côté navigateur (aucune requête ajoutée). Désactivable dans les réglages. Test : `node tools/agenda-test/test-agenda.js` (maquette Kendo + Playwright). |
| Version ordinateur | Menu ⋮ → « Version ordinateur » (ou Réglages) : user-agent de bureau et page de 1280 px, zoomable. Pour les écrans prévus pour ordinateur (agenda…). |
| Envoi de fichiers | Sélecteur de fichiers Android et appareil photo, sélection multiple, filtre `accept` respecté. Pas de permission CAMERA (l'app Appareil photo prend la photo). |
| Téléchargements | `https:` → DownloadManager, avec les cookies de session, le user-agent et le Referer. `data:` décodé localement ; `blob:` relu dans la page et transmis par morceaux via `WebViewCompat.addWebMessageListener` (limité aux domaines autorisés, jeton aléatoire par transfert). Les fichiers vont dans l'espace **privé** de l'app ; « Ouvrir » les passe à l'app choisie (PDF, DOCX…) via FileProvider, en lecture seule. Liste et suppression dans Réglages → Documents téléchargés. |
| Mode sombre | La WebView expose `prefers-color-scheme` d'après le thème système (si le site le gère). Option « Assombrir les pages sans thème sombre » (désactivée par défaut). |
| User-agent | Celui de Chrome mobile par défaut (sans `; wv`, `Version/4.0`, `Build/…`, ni la marque « Android WebView » dans les Client Hints). Désactivable dans les réglages. Test : `node tools/agenda-test/test-agenda.js` (maquette Kendo + Playwright). |

## Sécurité

- **HTTPS uniquement** : contenu mixte interdit, trafic en clair refusé (`network_security_config`), liens
  `http://` d'un domaine autorisé rechargés en `https://`, `onReceivedSslError` → `cancel()` (jamais
  `proceed()`), seules les autorités de certification du système sont reconnues.
- **WebView restreinte** : pas d'`addJavascriptInterface`, `allowFileAccess`/`allowContentAccess` à `false`,
  géolocalisation, caméra et micro refusés aux pages, Safe Browsing actif, débogage distant désactivé,
  télémétrie WebView refusée (`MetricsOptOut`). Les liens `intent:` sont relus sans composant explicite.
- **Aucune sauvegarde** : `allowBackup="false"`, `fullBackupContent="false"`, règles d'extraction qui excluent tout.
- **Verrouillage** : BiometricPrompt (biométrie forte sur Android 11+, repli sur le code de l'appareil) au
  lancement et au retour après plus de 5 min (réglable). Sans verrouillage d'écran configuré sur
  l'appareil, l'app refuse d'afficher les données.
- **FLAG_SECURE** activé par défaut (désactivable) : pas de capture, aperçu masqué dans les apps récentes.
  Posé avant le premier affichage, aussi sur les fenêtres de connexion (popups).
- **Notifications** : le nom des documents n'apparaît pas dans la notification de téléchargement (écran de verrouillage).
- **Aucun analytics, aucun SDK tiers, aucun log** d'URL ni de contenu. Seule dépendance hors AndroidX :
  `lielugit-updater` (bibliothèque maison du modèle), qui interroge l'API publique GitHub des releases de ce
  dépôt, uniquement dans l'APK release `production`. Le dépôt doit donc être **public** : l'app l'interroge
  sans jeton, et un dépôt privé répond 404 (« Aucune version publiée trouvée »).
- **Se déconnecter et effacer les données** (Réglages) : cookies, cache, WebStorage (localStorage, IndexedDB,
  Cache Storage…), formulaires, identifiants HTTP, historique, documents téléchargés et photos.

À noter : le site Lexis Poly charge lui-même des scripts tiers (bandeau cookies OneTrust, Pendo). Ils font
partie de l'interface officielle et ne sont pas bloqués par l'app.

## Build

```
./gradlew ktlintCheck                  # lint Kotlin
./gradlew test                         # tests unitaires (staging + production)
./gradlew assembleDebug                # APK debug
./gradlew assembleRelease              # APK release (staging + production)
scripts/verify.sh                      # tout ce qui précède avant un push
```

Sans Android Studio : `scripts/setup-android-sdk.sh` installe le SDK (plateforme 37.2, build-tools 37.0.0).

Versions (dernières stables au 25/11/2026, vérifiées sur Google Maven / Maven Central) : AGP 9.5.3 (Kotlin
intégré), Kotlin 2.4.21, Gradle 9.9.2, androidx.webkit 1.18.0, androidx.biometric 1.1.0, Compose BOM
2026.11.01. `minSdk` 26, `targetSdk` 37, `compileSdk` 37.2. JDK 17+.

### CI

`.github/workflows/build.yml`, sur chaque push : ktlint → tests → `assembleRelease` → **`assembleDebug`, APK
debug publiés en artefact** (`DeboPoly-debug-<branche>-<run>`, onglet *Actions* du run). Sur `main` /
`staging`, l'APK release est en plus publié en release GitHub (convention du modèle).

### Signature

Les APK release publiés (`main`, `staging`) sont signés par la **clé de publication DeboPoly**, fournie à la CI
par les secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` :

- certificat : `CN=DeboPoly, O=Lielu, C=FR` (alias `debopoly`) ;
- empreinte SHA-256 attendue :
  `F8:03:F0:E0:90:7C:9E:35:B0:0F:1C:4B:9C:94:D6:3B:0B:58:DF:C5:BD:D8:ED:EE:7F:F4:3D:6F:57:5B:09:B2`.

Vérifier un APK : `apksigner verify --print-certs <fichier.apk>`. Le keystore versionné `app/debug.keystore`
(public, mots de passe `android`) ne sert plus qu'aux builds locaux et aux branches sans secrets. Perdre la clé ou
en changer impose de désinstaller l'app une fois.

## Permissions

| Permission | Usage |
| --- | --- |
| `INTERNET` | Afficher Lexis Poly ; mise à jour intégrée (API GitHub). |
| `ACCESS_NETWORK_STATE` | Distinguer « hors ligne » et recharger au retour du réseau. |
| `USE_BIOMETRIC` | Verrouillage. |
| `REQUEST_INSTALL_PACKAGES` (lielugit-updater) | Installer une mise à jour. |

## Licence

GNU General Public License v3.0 (GPL-3.0) — voir [`LICENSE`](LICENSE).
