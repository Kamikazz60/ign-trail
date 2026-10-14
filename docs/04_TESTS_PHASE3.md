# Phase 3 — Tests sur téléphone

## Installer

APK : `apk/IGN-Trail-phase3.apk`. Elle remplace la version précédente, et les randonnées enregistrées sont conservées.

```bash
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb" install -r apk/IGN-Trail-phase3.apk
```

## Où trouver les cartes hors ligne

- Bouton **couches** (en haut à droite), puis **« Cartes hors ligne… »**.
- Onglet **Parcours** : lien **« Carte hors ligne autour de ce parcours »** sous chaque randonnée.

## Scénarios

| # | Scénario | Résultat attendu |
|---|---|---|
| 1 | Cartes hors ligne → **Une zone** | Un cadre bleu apparaît sur la carte. Déplacez et zoomez pour y placer la zone : l'estimation (taille, km², tuiles) se met à jour. |
| 2 | Niveaux de détail | Essentiel (zoom 14), Randonnée (zoom 15), Détaillé (zoom 16) : la taille estimée change. |
| 3 | Télécharger | Retour à la liste avec la barre de progression, et une notification « Téléchargement des cartes ». |
| 4 | Quitter l'application pendant le téléchargement | La notification reste affichée et le téléchargement continue. |
| 5 | **Fermer de force l'application** pendant un téléchargement, puis la rouvrir | Le téléchargement reprend tout seul là où il s'était arrêté (vérifié sur émulateur : 48 %, puis 63 %, puis terminé). |
| 6 | Fin du téléchargement | « ✓ Disponible hors ligne · X Mo ». |
| 7 | **Mode avion** (test B, partie carte) | La zone téléchargée s'affiche normalement, relief compris, avec la pastille « Hors connexion · carte téléchargée ici ». En dehors de la zone : « carte non téléchargée ici ». |
| 8 | Zoom maximal hors ligne | La carte reste nette (agrandissement du zoom 16). |
| 9 | Parcours → « Carte hors ligne autour de ce parcours » | Choix du parcours, de la marge (1, 2 ou 5 km) et du détail. L'estimation s'affiche, ainsi que la disponibilité actuelle (partielle si une zone existe déjà). |
| 10 | Bouton carte d'une zone (« Voir ») | La carte se cadre sur la zone. |
| 11 | Bouton corbeille | Confirmation, puis la zone disparaît et l'espace utilisé diminue. |
| 12 | « Démarrer ma randonnée » | La ligne « Carte disponible / partiellement disponible / indisponible hors ligne » concerne les 2 km autour de vous. |
| 13 | Vignette **Autre** | Quatrième activité dans la grille. L'enregistrement fonctionne (nom « Activité du … »), avec un filtrage adapté aux activités plus rapides (vélo, ski…). |
| 14 | Fonds « standard » ou « photos aériennes » | Mention « Nécessite Internet ». Seul le Plan IGN classique se télécharge. |

## À remonter

- La taille réelle d'une zone comparée à l'estimation : l'estimation est volontairement prudente.
- La durée de téléchargement en Wi-Fi pour une zone d'environ 100 km².
- La lisibilité de la carte hors ligne en montagne (relief, courbes, sentiers).
