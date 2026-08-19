# DraftWA Android Testbed

Branche dédiée au développement et aux tests de **DraftWA Mobile**. Le mini-site historique reste intact sur la branche `codespace-fuzzy-giggle-v6g5vgq6rwrpcpx7`.

## Objectif

Ce banc de test installe l'APK DraftWA sur de vrais Android Emulator GitHub Actions et vérifie automatiquement :

- installation de l'APK ;
- lancement de `com.draftwa.mobile` ;
- maintien du processus après démarrage ;
- présence de `FATAL EXCEPTION` liée à DraftWA ;
- collecte de `adb logcat` ;
- capture d'écran ;
- `dumpsys activity` et `dumpsys package`.

## Matrice Android

Les smoke tests sont exécutés sur les API Android 30, 34 et 35.

## APK testée

`apk/DraftWA_Mobile_Drafts_v0.6.1_SAFE.apk`

SHA-256 attendu : `106ab80a592e80649c983707b08785c9806db25ed5b1ed87c999c6cc748a4df3`
