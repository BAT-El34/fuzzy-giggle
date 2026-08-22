# DraftWA Mobile

Branche de préproduction de **DraftWA Mobile 0.8.1**.

Le mini-site historique du dépôt reste intact sur la branche par défaut `codespace-fuzzy-giggle-v6g5vgq6rwrpcpx7`. Ne pas remplacer cette branche par le projet Android.

## Version actuellement publiée

- Version : **0.8.1**
- versionCode : **801**
- Package Android : `com.draftwa.mobile`
- Cible : WhatsApp Business `com.whatsapp.w4b`
- minSdk : **26**
- targetSdk / compileSdk : **35**
- Build : **Gradle / Android Gradle Plugin / D8 standard**
- Aucun patch DEX manuel n'est utilisé.

## Fonctionnement principal

DraftWA utilise un `AccessibilityService` avec `flagReportViewIds` pour détecter les brouillons WhatsApp Business, parcourir la liste des conversations, ouvrir le bon chat et appliquer les règles configurées.

Le mode diagnostic est la première gate fonctionnelle :

1. détecter un brouillon ;
2. vérifier les règles de confirmation ;
3. calculer la transformation ;
4. appliquer temporairement la transformation lorsque le scénario le requiert ;
5. vérifier le texte transformé ;
6. restaurer le texte original ;
7. vérifier la restauration ;
8. ne jamais appuyer sur **Envoyer** en diagnostic.

La 0.8.1 renforce notamment :

- le recentrage vers la liste si WhatsApp est déjà ouvert dans un chat ;
- le retour fiable à la liste après traitement ;
- la reprise du scan après temporisation ;
- la continuité du mode diagnostic non destructif ;
- l'updater HTTPS avec vérification SHA-256, package, versionCode et certificat.

## Validation de release 0.8.1

GitHub Actions run **32528293529** (source exacte `draftwa-0.8.1`) :

- contrôles statiques : **53/53 PASS** ;
- modèle conversationnel : **3 000 simulations PASS** ;
- règles métier : **10 017/10 017 PASS** ;
- Android API 30 : **PASS** ;
- Android API 34 : **PASS** ;
- Android API 35 : **PASS** ;
- transformation diagnostique : **PASS** ;
- restauration du brouillon : **PASS** ;
- aucun envoi en diagnostic : **PASS** ;
- aucun crash DraftWA détecté : **PASS** ;
- aucun ANR DraftWA détecté : **PASS** ;
- release candidate exact : **PASS**.

## Gate canonique de préproduction

Le workflow canonique `.github/workflows/android-integration.yml` a été revalidé sur `preproduction` par le run **32579199707** au commit `d1f177da2c49cb3fe262fef71919d3fb44d7325b`.

Entre la source publiée 0.8.1 et ce commit de préproduction, seuls le workflow CI, le README et `RELEASE_CHECKLIST.md` ont changé ; le code Android reste celui de 0.8.1 / versionCode 801.

Résultats de la gate canonique :

- contrôles statiques : **53/53 PASS** ;
- modèle conversationnel : **3 000 simulations PASS** ;
- règles métier : **10 017/10 017 PASS** ;
- Android API 30 : **PASS** ;
- Android API 34 : **PASS** ;
- Android API 35 : **PASS** ;
- transformation puis restauration du brouillon : **PASS** ;
- aucun envoi en diagnostic : **PASS** ;
- aucun crash / ANR DraftWA détecté : **PASS** ;
- candidat release non signé : **PASS** ;
- artifacts API 30/34/35 et release candidate : **présents**.

## Release publiée

APK signé avec le certificat permanent DraftWA.

SHA-256 :

`6aa9c03472aba680c62a34f89927bc5cc02bc990347970a42ef62678dea8fc75`

Canal officiel d'installation / mise à jour :

https://draftwa-mobile-five.vercel.app

Manifeste de mise à jour :

https://draftwa-mobile-five.vercel.app/latest.json

Le fichier distribué reste le fichier Drive permanent `DraftWA-latest.apk`.

## Branches utiles

- `preproduction` : branche canonique pour les prochaines validations ;
- `draftwa-0.8.1` : source de la release actuellement publiée ;
- `draftwa-latest` : pointeur stable vers la dernière release validée ;
- `draftwa-testbed` : historique du banc de test ;
- branches `draftwa-0.x.y` antérieures : historique des releases.

## CI canonique

`.github/workflows/android-integration.yml` est le workflow de gate de `preproduction`.

Les workflows versionnés historiques (`android-integration-070.yml`, `android-integration-080.yml`, etc.) sont conservés pour la traçabilité mais ne doivent pas servir de référence pour une future release sans vérification.

## Sécurité de distribution

Le mécanisme de mise à jour vérifie notamment :

- HTTPS pour le manifeste et l'APK ;
- SHA-256 ;
- package Android attendu ;
- versionCode strictement supérieur ;
- continuité de la signature de l'application ;
- taille maximale de l'APK ;
- confirmation Android pour l'installation ;
- reprise après autorisation de la source lorsque nécessaire.

La clé de signature release et ses secrets ne doivent jamais être commités, copiés dans Vercel ou écrits dans les logs CI.
