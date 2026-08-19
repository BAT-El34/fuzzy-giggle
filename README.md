# DraftWA Android Testbed

Branche dédiée au développement et aux tests de **DraftWA Mobile**. Le mini-site historique reste intact sur la branche `codespace-fuzzy-giggle-v6g5vgq6rwrpcpx7`.

## Objectif

Ce banc de test reconstruit l'APK DraftWA source, applique les correctifs DEX validés, la signe avec une clé CI temporaire puis l'installe sur de vrais Android Emulator GitHub Actions. Il vérifie automatiquement :

- intégrité SHA-256 de l'APK source ;
- correction structurelle de `classes2.dex` ;
- installation de l'APK corrigée ;
- lancement de `com.draftwa.mobile` ;
- maintien du processus après démarrage ;
- présence de `FATAL EXCEPTION` liée à DraftWA ;
- collecte de `adb logcat` ;
- capture d'écran ;
- `dumpsys activity` et `dumpsys package`.

## Matrice Android

Smoke tests : API Android 30, 34 et 35.

## Correctifs DEX validés

Deux défauts du générateur DEX initial ont été isolés :

1. `outs_size` était fixé à `8` même lorsque `registers_size` était inférieur ;
2. les nibbles `argument_count` et `G` du format Dalvik `invoke-* / 35c` étaient inversés.

Le patch CI corrige les deux défauts, recalcule la signature SHA-1 et le checksum Adler-32 du DEX, puis re-signe l'APK pour le test.

## Dernier résultat validé

Run GitHub Actions `32285225103` :

- Android 11 / API 30 : **PASS** ;
- Android 14 / API 34 : **PASS** ;
- Android 15 / API 35 : **PASS**.

Dans les trois cas, l'APK corrigée s'installe, se lance et le processus `com.draftwa.mobile` reste vivant pendant la fenêtre de contrôle sans `FATAL EXCEPTION` détectée.

## APK source

`DraftWA_Mobile_Drafts_v0.6.1_SAFE.apk`

SHA-256 source : `106ab80a592e80649c983707b08785c9806db25ed5b1ed87c999c6cc748a4df3`
