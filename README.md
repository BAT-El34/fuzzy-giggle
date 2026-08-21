# DraftWA Mobile

Branche de release de **DraftWA Mobile 0.7.1**.

Le mini-site historique du dépôt reste intact sur la branche par défaut `codespace-fuzzy-giggle-v6g5vgq6rwrpcpx7`.

## Version actuelle

- Version : **0.7.1**
- versionCode : **701**
- Package Android : `com.draftwa.mobile`
- Cible : WhatsApp Business `com.whatsapp.w4b`
- minSdk : **26**
- targetSdk / compileSdk : **35**
- Build : **Gradle / Android Gradle Plugin / D8 standard**
- Aucun patch DEX manuel n'est utilisé pour cette release.

## Fonctionnement principal

DraftWA utilise un `AccessibilityService` avec `flagReportViewIds` pour détecter les brouillons WhatsApp Business, parcourir la liste des conversations et appliquer les règles configurées.

Le mode diagnostic est le mode de validation privilégié :

1. détecter un brouillon ;
2. vérifier les règles de confirmation ;
3. appliquer temporairement la transformation ;
4. vérifier la transformation ;
5. restaurer le texte original ;
6. vérifier la restauration ;
7. ne jamais appuyer sur **Envoyer**.

La 0.7.1 ajoute notamment :

- parcours guidé « Tester en diagnostic » ;
- reprise automatique après activation de l'accessibilité ;
- état de préparation plus clair ;
- blocage sûr si WhatsApp Business est absent ;
- résumé de diagnostic partageable sans contenu des brouillons ;
- affichage dynamique de la version ;
- maintien des préférences existantes lors de la mise à jour.

## Validation de release

GitHub Actions run **32422003845** :

- contrôles statiques / modèle : **PASS** ;
- Android API 30 : **PASS** ;
- Android API 34 : **PASS** ;
- Android API 35 : **PASS** ;
- transformation diagnostique : **PASS** ;
- restauration du brouillon : **PASS** ;
- aucun envoi en diagnostic : **PASS** ;
- aucun crash DraftWA détecté : **PASS** ;
- aucun ANR DraftWA détecté : **PASS** ;
- release candidate exact : **PASS**.

Le banc API 35 neutralise uniquement les overlays/ANR du launcher Android Emulator lorsqu'ils apparaissent, puis échoue explicitement si un crash ou ANR de `com.draftwa.mobile` est détecté.

## Release publiée

APK signé avec le certificat permanent DraftWA.

SHA-256 :

`20b11da6db28e41c8e9f8241df826099916e945d99bf77079f17b358f5b8a9ce`

Canal officiel d'installation / mise à jour :

https://draftwa-mobile-five.vercel.app

Le manifeste de mise à jour est disponible via :

https://draftwa-mobile-five.vercel.app/latest.json

## Branches utiles

- `draftwa-0.7.1` : source exacte de la release 0.7.1 ;
- `draftwa-latest` : pointeur stable vers la dernière release validée ;
- `draftwa-0.7.0` : release précédente ;
- `draftwa-testbed` : historique du banc de test.

## Sécurité de distribution

Le mécanisme de mise à jour vérifie notamment :

- HTTPS pour le manifeste et l'APK ;
- SHA-256 ;
- package Android attendu ;
- versionCode strictement supérieur ;
- continuité de la signature de l'application ;
- taille maximale de l'APK ;
- permission Android d'installation lorsque nécessaire.
