# Phase 1 — Tests sur téléphone

## Installer l'APK

APK fournie : `apk/IGN-Trail-phase1.apk`, version optimisée de 33 Mo.

**Méthode 1 : par câble USB (recommandée)**

1. Sur le téléphone, ouvrez Paramètres › À propos › appuyez 7 fois sur « Numéro de build », puis activez Options pour développeurs › **Débogage USB**.
2. Branchez le téléphone et acceptez l'autorisation de débogage.
3. Sur le PC, lancez :

   ```bash
   "$LOCALAPPDATA/Android/Sdk/platform-tools/adb" install -r apk/IGN-Trail-phase1.apk
   ```

**Méthode 2 : sans câble**

Copiez le fichier `.apk` sur le téléphone (câble en mode fichiers, ou votre propre cloud), ouvrez-le et autorisez « Installer des applications inconnues » pour l'application utilisée.

> Une version précédente installée depuis une APK de **debug** porte la même signature : la mise à jour se fait sans désinstaller.

## Scénarios à vérifier

Prévoir une sortie de 10 à 15 minutes, à pied et en extérieur.

| # | Scénario | Résultat attendu |
|---|---|---|
| 1 | Premier lancement | L'application s'ouvre sur l'onglet Carte et demande l'autorisation de localisation. Choisir « Pendant l'utilisation » et « Position exacte ». |
| 2 | Attente du GPS (à l'intérieur) | Bandeau orange « Position approximative… » ou « Recherche du signal GPS… ». La carte est centrée sur la dernière position connue. |
| 3 | Premier point GPS (dehors) | La carte se cadre sur votre position, au niveau de détail des rues. Le bandeau devient vert : « GPS ± X m ». |
| 4 | **Noms de lieux** | Les noms de rues, de villages et de sommets s'affichent sur la carte (impossible à vérifier sur l'émulateur). |
| 5 | Indicateur | Point bleu cerclé de blanc. À l'arrêt, la flèche suit l'orientation du téléphone. En marchant, elle indique votre direction de déplacement. |
| 6 | Suivi en marchant | La carte défile toute seule, le nord reste en haut et le zoom ne change pas. |
| 7 | Déplacer la carte au doigt | Le suivi s'arrête, le bouton bleu « Revenir au suivi GPS » apparaît, et la carte **ne bouge plus** pendant que vous marchez. |
| 8 | Zoomer à deux doigts | Même comportement : le cadrage choisi est conservé. |
| 9 | « Revenir au suivi GPS » | La carte se recentre sur vous **en gardant votre zoom**. |
| 10 | Boutons + / − | Le zoom change. Si le suivi était actif, il le reste. |
| 11 | Bouton « couches » | Choix entre Plan IGN classique, standard et photos aériennes, avec l'ombrage du relief activable. Le choix est conservé au redémarrage. |
| 12 | Onglets Créer / Parcours / Statistiques | Écran « Disponible en Phase X ». Le bouton retour ramène à la carte, au même cadrage. |
| 13 | Rotation de l'écran | La carte garde son cadrage et l'état du suivi. |
| 14 | Mode sombre du téléphone | Les boutons et panneaux passent en sombre. |
| 15 | Localisation désactivée | Une carte « La localisation du téléphone est désactivée » s'affiche, avec un bouton « Activer ». |

## À me remonter

- Les numéros des scénarios en échec, avec une capture d'écran si possible.
- Le modèle du téléphone et sa version d'Android.
- Votre avis sur la lisibilité de la carte (style classique, ombrage) et sur la taille des boutons en extérieur.
