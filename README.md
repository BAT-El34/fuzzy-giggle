# DraftWA Mobile

Branche canonique de préproduction de **DraftWA Mobile 0.8.2**.

Le mini-site historique du dépôt reste intact sur la branche par défaut `codespace-fuzzy-giggle-v6g5vgq6rwrpcpx7`. Ne pas remplacer cette branche par le projet Android.

## Version actuellement publiée

- Version : **0.8.2**
- versionCode : **802**
- Package Android : `com.draftwa.mobile`
- Cible : WhatsApp Business `com.whatsapp.w4b`
- minSdk : **26**
- targetSdk / compileSdk : **35**
- Build : **Gradle / Android Gradle Plugin / D8 standard**
- Aucun patch DEX manuel.

La 0.8.2 conserve le moteur 0.8.1 validé et intègre le module Carte & Prospection / Opportunity avec API serveur sur le canal officiel DraftWA.

## Validation 0.8.2

Pull Request de validation : **#4 — DraftWA 0.8.2 — release validation**.

GitHub Actions run **32580964549** :

- contrôles statiques : **PASS** ;
- modèle conversationnel : **PASS** ;
- règles métier / fuzzing : **PASS** ;
- Android API 30 : **PASS** ;
- Android API 34 : **PASS** ;
- Android API 35 : **PASS** ;
- AccessibilityService : **PASS** ;
- transformation diagnostique : **PASS** ;
- restauration du brouillon : **PASS** ;
- aucun envoi en diagnostic : **PASS** ;
- aucun crash / ANR DraftWA : **PASS** ;
- release candidate exact : **PASS**.

Commit de release fusionné :

`d8bebb2108c12ccaf4086e5d3ef7b19daf44f0f0`

Branches :

- `draftwa-0.8.2` : source figée de la release ;
- `draftwa-latest` : pointe sur la release 0.8.2 validée ;
- `preproduction` : reprend les développements après la release.

## APK publiée

L'APK finale est issue du candidat CI, alignée et signée dans l'environnement privé avec le certificat permanent DraftWA.

- package : `com.draftwa.mobile`
- versionCode : `802`
- versionName : `0.8.2`
- SHA-256 :

`b199b5315b5b53f9b7c4b61a72be271438009dd3158378529ed4c27a39972f0e`

Le fichier Drive permanent reste `DraftWA-latest.apk` et conserve son ID de distribution.

## Canal officiel

Installation / mise à jour :

https://draftwa-mobile-five.vercel.app/

Manifeste updater :

https://draftwa-mobile-five.vercel.app/latest.json

Carte & Prospection :

https://draftwa-mobile-five.vercel.app/opportunities/

Health API :

https://draftwa-mobile-five.vercel.app/api/opportunities/health

La production Vercel sert `latest.json`, le téléchargement APK et Carte & Prospection via fonctions Vercel afin d'éviter les erreurs de packaging statique observées pendant la publication 0.8.2.

## Sécurité updater

Le mécanisme de mise à jour vérifie notamment :

- HTTPS pour le manifeste et l'APK ;
- SHA-256 ;
- package Android attendu ;
- versionCode strictement supérieur ;
- continuité du certificat de signature ;
- limite de taille APK ;
- installation via `PackageInstaller` avec confirmation Android.

La clé privée release et ses secrets ne doivent jamais être commités, transférés à Vercel ni écrits dans les logs.

## Limite de validation

La release 0.8.2 est validée statiquement, par simulation et sur émulateurs Android API 30/34/35. Une validation sur appareil Android physique doit être documentée séparément et ne doit pas être prétendue sans preuve réelle.
