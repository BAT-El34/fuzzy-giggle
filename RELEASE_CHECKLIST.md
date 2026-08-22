# DraftWA — Release Checklist

Cette checklist s'applique à toute future release publiée sur le canal stable DraftWA.

## 1. Source et build

- [ ] La release part de `preproduction` ou d'une branche de release issue de `preproduction`.
- [ ] Le package reste `com.draftwa.mobile`.
- [ ] Le build utilise Gradle / Android Gradle Plugin / D8-R8 standard.
- [ ] Aucun DEX n'est généré ou patché manuellement.
- [ ] Le `versionCode` est strictement supérieur à celui déjà publié.
- [ ] Le `versionName` correspond à la release prévue.

## 2. Sécurité et secrets

- [ ] Aucune clé de signature, mot de passe ou secret n'est présent dans GitHub.
- [ ] Aucun secret de signature n'est envoyé à Vercel.
- [ ] Les logs CI ne contiennent aucun secret.
- [ ] L'APK finale est signée uniquement dans l'environnement privé avec la clé release permanente DraftWA.
- [ ] Le certificat de l'APK finale correspond au certificat permanent déjà utilisé par les releases installables comme mises à jour.

## 3. Tests statiques et règles

- [ ] `tools/static_review.py` PASS.
- [ ] `tools/engine_model_test.py` PASS.
- [ ] `RuleSimulation` PASS.
- [ ] Les tests supplémentaires du backend/opportunity éventuel PASS.

## 4. Matrice Android obligatoire

Pour API 30, API 34 et API 35 :

- [ ] Build DraftWA PASS.
- [ ] Build Fake WhatsApp Business PASS.
- [ ] Installation des deux APK PASS.
- [ ] Démarrage DraftWA PASS.
- [ ] Process DraftWA vivant après lancement.
- [ ] Aucun `FATAL EXCEPTION` attribué à `com.draftwa.mobile`.
- [ ] Aucun ANR attribué à `com.draftwa.mobile`.
- [ ] AccessibilityService activé dans le banc de test.
- [ ] Ouverture du Fake WhatsApp Business PASS.
- [ ] Scan de `conversation_list` PASS.
- [ ] Brouillon hors écran détecté après scroll.
- [ ] `draft_indicator` ou fallback valide détecté.
- [ ] Bonne conversation ouverte.
- [ ] Texte de l'éditeur lu.
- [ ] Condition `Soko` vérifiée.
- [ ] Transformation de `,Enregistré,` vérifiée.
- [ ] Transformation diagnostique vérifiée.
- [ ] Texte original restauré.
- [ ] `RESTORED_OK` présent.
- [ ] Aucun envoi effectué en diagnostic.
- [ ] Artifacts disponibles : logcat, screenshots, UI hierarchy, résultats et APK testée.

## 5. Release candidate

- [ ] Le candidat release est produit seulement après réussite de la matrice Android.
- [ ] Le candidat est construit par `:app:assembleRelease`.
- [ ] Le binaire signé provient exactement du candidat CI validé.
- [ ] `zipalign` et `apksigner` sont utilisés avec une toolchain Android normale.
- [ ] Package final vérifié.
- [ ] versionCode final vérifié.
- [ ] Certificat final vérifié.
- [ ] SHA-256 final calculé.

## 6. Canal de mise à jour

- [ ] Le domaine de production est HTTPS.
- [ ] `latest.json` est valide.
- [ ] `latest.json.versionCode` correspond exactement à l'APK finale.
- [ ] `latest.json.versionName` correspond exactement à l'APK finale.
- [ ] `latest.json.sha256` correspond exactement au SHA-256 de l'APK finale.
- [ ] L'URL de l'APK est HTTPS après toutes les redirections.
- [ ] Le téléchargement de l'APK fonctionne.
- [ ] L'APK téléchargée a le bon package.
- [ ] L'APK téléchargée a le bon certificat.
- [ ] L'APK téléchargée a le bon SHA-256.
- [ ] La page utilisateur affiche la version, les release notes et le bouton Installer / Mettre à jour.

## 7. Rollback

- [ ] La dernière APK stable précédente reste récupérable.
- [ ] `latest.json` peut être repointé vers une version validée si nécessaire.
- [ ] Aucun downgrade automatique n'est autorisé par DraftWA.

## 8. Gate finale

La publication n'est autorisée que si :

`STATIC PASS + RULES PASS + API30 PASS + API34 PASS + API35 PASS + NO FATAL + NO ANR + DIAGNOSTIC FLOW PASS + SIGNATURE PASS + PACKAGE PASS + VERSIONCODE PASS + HASH PASS`

Une validation sur appareil physique est documentée séparément : elle ne doit jamais être prétendue si seule la CI émulateur a été exécutée.
