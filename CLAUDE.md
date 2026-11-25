# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Ce dépôt

App Android « coque » WebView **DeboPoly** pour Lexis Poly (`https://www.lexispoly.fr/lexispoly/`), dérivée du modèle
`lieluinit` (même Gradle/CI/scripts/mise à jour). La sauvegarde automatique du modèle a été RETIRÉE à dessein
(données sous secret professionnel : `allowBackup="false"`, rien ne quitte l'appareil) — ne pas la réintroduire.
Nom de l'app : DeboPoly (`com.lielu.debopoly`). Configuration (URL, liste blanche, SSO) : `config/AppConfig.kt`. Voir README pour le comportement et la sécurité.

Règles à ne jamais casser : pas d'`addJavascriptInterface`, `onReceivedSslError` → `cancel()`, pas de
`Log` d'URL ni de contenu, pas de SDK tiers / analytics, FLAG_SECURE posé avant tout affichage.

## Commandes

```
./gradlew ktlintCheck               # lint Kotlin (ktlint 1.3.1, échoue le build si violations)
./gradlew ktlintFormat              # auto-fix
./gradlew test                      # tests unitaires (staging + production)
./gradlew testStagingDebugUnitTest --tests "com.lielu.debopoly.web.UrlPolicyTest"   # un seul test
./gradlew assembleDebug             # APK debug (staging + production)
./gradlew assembleRelease           # APK release signés (staging + production)
```

APK produit : `app/build/outputs/apk/<staging|production>/release/app-<flavor>-release.apk`.

### Gradle en session Claude Code sur le web

Le hook `SessionStart` (`.claude/hooks/session-start.sh`) installe ktlint **et**, si `dl.google.com` est
accessible, le SDK Android (`scripts/setup-android-sdk.sh`, dans `~/android-sdk`) : `./gradlew` y est alors
utilisable (compilation, tests, `assembleRelease` vérifiés — c'est ainsi que ce modèle a été validé). Si la
politique réseau bloque `dl.google.com`, seuls `scripts/ktlint.sh` (Maven Central) et `scripts/verify.sh --fast`
marchent : annoncer alors que compilation et tests ne sont validés que par la CI, ne jamais présenter un
changement non compilé comme vérifié. Ne pas ajouter `ktlint_code_style` au `.editorconfig`.

## Avant de pousser

```
scripts/verify.sh            # ktlint, marqueurs de conflit, migrations Room (si présentes), version catalog, Gradle
scripts/verify.sh --fast     # idem sans Gradle
scripts/ci-status.sh --wait <branche>   # attend et affiche le verdict de la CI pour ce commit
```

Le hook `.githooks/pre-push` (activé par le hook `SessionStart` ; sur une machine de dev :
`git config core.hooksPath .githooks`) lance `verify.sh` à chaque push et refuse un push vers `staging`/`main`
tant que le commit n'a pas de run CI vert (`SKIP_VERIFY=1`, `SKIP_CI_GUARD=1` en dernier recours).
Ordre : `verify.sh` → pousser la branche de travail → `ci-status.sh --wait` → seulement ensuite merger dans
`staging`/`main`.

## CI et releases

`.github/workflows/build.yml`, sur chaque push de **n'importe quelle branche** : `ktlintCheck` → tests unitaires
→ `assembleRelease` (valide que l'APK release se construit, lint vital inclus). Sur `main` et `staging`
uniquement, l'APK est ensuite publié :
- `main` → release `v<version>` avec `<NomApp>-<version>.apk` (« Latest release » GitHub) ;
- `staging` → pre-release `staging-v<version>` avec `<NomApp>-Staging-<version>.apk`.

`<version>` = `<appVersionBase>.<run_number>` ; le run number est aussi le `versionCode` (`BUILD_NUMBER`), condition
pour que la mise à jour intégrée voie la release comme plus récente. Une release **par build**, jamais un tag
écrasé. Les releases sont signées par la clé de publication DeboPoly (secrets `KEYSTORE_BASE64`/`KEYSTORE_PASSWORD`/
`KEY_ALIAS`/`KEY_PASSWORD`, `CN=DeboPoly`, empreinte SHA-256 `F8:03:F0:…:5B:09:B2`, README « Signature »).
`app/debug.keystore` ne sert qu'aux builds locaux sans secrets.
R8 est désactivé (`isMinifyEnabled = false`) : ne l'activer qu'après l'avoir testé sur un appareil.

## Architecture

Package `com.lielu.debopoly`. DI manuelle (`AppContainer`), Compose pour l'interface, Views pour la WebView.

- `config/AppConfig` — BASE_URL, domaines autorisés, SSO, délais de verrouillage.
- `web/` — `BrowserController` (possède la WebView : réglages, clients, popups SSO, erreurs, effacement),
  `UrlPolicy` (liste blanche, décisions de navigation, testée), `UserAgents`, `LexisWebView` +
  `WebSwipeRefreshLayout` (tirer pour actualiser seulement sur dépassement en haut), `ExternalLinks`.
  `res/raw/agenda_mobile.js` : en portrait, l'agenda (Kendo UI Scheduler) passe dans sa vue « agenda »
  STANDARD, ouverte sur aujourd'hui, en position fixe sous le menu du site (bouton Filtres ↔ Agenda, bouton Retour = history.back()) ;
  semaine lundi → dimanche via l'événement standard `navigate` (flèches, Aujourd'hui, mini-calendrier → lundi
  de la semaine), titre « Semaine du … » réécrit au `dataBound`, lignes du lundi suivant masquées (la vue liste
  de Kendo inclut le jour 8 à minuit) ; ajout de
  la vue via l'API interne `_views()` si absente (repli : vue « jour »). Ne PAS dériver de vue Kendo maison :
  essayé en v1.0.14, aucune donnée affichée sur le vrai site.
  Test : `node tools/agenda-test/test-agenda.js` (maquette Kendo fr-FR + Playwright).
- `download/` — `DownloadController` (`https:`, `data:`, `blob:` via `addWebMessageListener`),
  `HttpsDownload` (redirections et en-têtes de session), `DownloadStore` (espace privé, FileProvider),
  `FileNames`, `DataUrl`, `FileOpener`.
- `security/` — `AppLock` (état de verrouillage du processus, testé), `BiometricGate` (BiometricPrompt).
- `data/settings` — DataStore : FLAG_SECURE, délai, user-agent, version ordinateur, assombrissement.
- `ui/` — `main/MainScreen` (barre, WebView, panneaux erreur/verrouillage), `settings/` (activité séparée qui
  renvoie `RESULT_WIPE`), `update/` (modèle), `theme/`.

`MainActivity` déclare `configChanges` (rotation, pliage, uiMode…) : ne pas compter sur sa recréation.

### Mise à jour automatique

`UpdateManager` (lielugit-updater, dépôt Maven local `libs/lielugit-maven`), active seulement pour `production`
en `release` (`AppContainer.updatesEnabled`).

### Configuration Gradle notable

- AGP 9.5 (Kotlin intégré : pas de plugin `kotlin-android` dans `app/`, déclaré `apply false` à la racine pour
  fixer la version), `compileSdk` 37.2 (exigé par Compose 1.13), `targetSdk` 37, `minSdk` 26, JVM 17.
- `resValues = true` (désactivé par défaut dans AGP 9, nécessaire au nom par flavor).
- `fragment-ktx` imposé : biometric 1.1.0 tire fragment 1.2.5, refusé par lint (Activity Result API).
- Versions dans `gradle/libs.versions.toml`.

## Style

- `ktlint` (config par défaut, `android.set(true)`, `ignoreFailures.set(false)`), `.editorconfig` : 4 espaces,
  ligne max 120, exception de nommage pour les fonctions `@Composable`. `kotlin.code.style=official`.
- Aucune couleur en dur dans un écran : tout passe par `ui/theme/Theme.kt`.
- Textes de l'interface en français.
