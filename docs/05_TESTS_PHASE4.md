# Phase 4 — Tests sur téléphone

## Installer

APK : `apk/IGN-Trail-phase4.apk`. Elle remplace la version précédente. Les randonnées et les cartes hors ligne sont conservées : la base de données est migrée automatiquement.

```bash
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb" install -r apk/IGN-Trail-phase4.apk
```

Pour tester, récupérez un fichier GPX de randonnée proche de chez vous (sites de randonnée, offices de tourisme, export d'une autre application).

## Scénarios

| # | Scénario | Résultat attendu |
|---|---|---|
| 1 | Parcours → **Itinéraires** → « Importer un GPX » | Le sélecteur de fichiers s'ouvre. Une fois le fichier choisi : « Itinéraire importé », avec d'éventuelles remarques (points invalides, plusieurs traces, etc.). |
| 2 | Fichier invalide (photo, PDF…) | Message clair : « Ce fichier n'est pas au format GPX » ou « Fichier illisible ». |
| 3 | **Ouvrir un GPX depuis une autre application** (Fichiers, Gmail, WhatsApp, navigateur) : « Ouvrir avec » ou « Partager » → IGN Trail | L'application s'ouvre et importe le fichier : c'est le cas d'un itinéraire partagé par un ami. |
| 4 | « Voir sur la carte » | Tracé violet, départ en vert, arrivée en noir, repères nommés, et la carte se cadre sur l'itinéraire. Le panneau affiche « Suivre ce parcours » et « Sens inverse ». |
| 5 | **Suivre** | Bandeau en haut avec une flèche violette, « Reste X km », la distance au tracé et une barre de progression. Une notification « Guidage en cours » s'affiche. |
| 6 | Marcher sur le parcours | La distance restante diminue. **Avant un virage, la flèche tourne vers la suite du chemin**, et non vers l'arrivée. |
| 7 | **Quitter le tracé** de plus de 40 m | Après quelques secondes, le bandeau passe au rouge (« Hors parcours »), avec vibration, bip et notification. La flèche et un trait fin indiquent le chemin le plus court vers le tracé. Une seule alerte, pas de répétition. |
| 8 | Revenir sur le tracé | Court signal, puis le bandeau redevient normal. |
| 9 | Marcher le long d'une route ou sous des arbres, au plus près de la limite | Pas d'alertes intempestives (une hystérésis évite les fausses alertes). |
| 10 | Bouton réglages du bandeau | Tolérance de 20, 40 ou 80 m, vibration et son activables. Le changement s'applique immédiatement. |
| 11 | **Écran verrouillé** pendant le guidage | La vibration et la notification arrivent quand même en cas de sortie de parcours. |
| 12 | **Mode avion** (test B complet, avec une carte téléchargée en Phase 3) | Le guidage fonctionne entièrement sans réseau. |
| 13 | Suivre en **sens inverse** | Le départ devient l'arrivée et la distance restante est calculée dans l'autre sens. |
| 14 | Parcours → Randonnées → « Suivre » | Une randonnée enregistrée devient un itinéraire à suivre. |
| 15 | Pendant un enregistrement, déplier le panneau → **Retour sur mes pas** | Guidage le long de votre propre trace, à l'envers. |
| 16 | Pendant un enregistrement → **Direction du départ** | Flèche, distance et cap à vol d'oiseau, avec la mention « ni relief ni obstacles pris en compte ». |
| 17 | Arrivée au bout de l'itinéraire | « Arrivée ! » avec un signal. |
| 18 | Itinéraire → « Carte hors ligne » | Téléchargement de la bande autour de l'itinéraire importé (Phase 3). |
| 19 | Bouton ✕ du bandeau ou de la notification | Le guidage s'arrête. L'enregistrement éventuel continue. |

## À remonter

- Si la flèche est **correcte en marchant** (cap GPS) et **à l'arrêt** (boussole). Elle indique la direction à prendre par rapport à l'orientation du téléphone.
- Si la tolérance de 40 m est adaptée à vos sentiers.
- Les applications ou sites dont l'import GPX échoue.
