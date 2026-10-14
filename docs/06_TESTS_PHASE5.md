# Phase 5 — Tests sur téléphone

## Installer

APK : `apk/IGN-Trail-phase5.apk`. Elle remplace la version précédente, sans perte de données. Elle inclut aussi la Phase 4, pas encore testée : voir [05_TESTS_PHASE4.md](05_TESTS_PHASE4.md).

```bash
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb" install -r apk/IGN-Trail-phase5.apk
```

**Le calcul d'itinéraire et les altitudes nécessitent Internet.** Préparez donc vos parcours à la maison.

## Scénarios

| # | Scénario | Résultat attendu |
|---|---|---|
| 1 | Onglet **Créer** | La carte reste affichée et cesse de suivre le GPS. Le panneau indique « Touchez la carte pour placer le départ ». |
| 2 | Toucher la carte 3 fois | Départ (D, vert), étape (1, violet) et arrivée (A, noir). Le tracé violet suit les chemins et sentiers (calcul IGN, chemin le plus court à pied). |
| 3 | Estimation | Distance, D+, D-, durée « ≈ h mm ». Le tout est présenté comme une **estimation** et non comme une mesure. |
| 4 | Point placé loin d'un chemin (au milieu d'un champ, d'un pierrier) | Le dernier tronçon jusqu'au point est tracé **en orange** et signalé « en ligne directe : non vérifié sur le terrain ». |
| 5 | Bouton « Profil » | Courbe altitude / distance. Toucher ou glisser sur la courbe affiche l'altitude et la distance. |
| 6 | Toucher un point existant | Il passe en bleu, et les boutons « Déplacer » et « Supprimer » apparaissent. |
| 7 | **Déplacer** | Une cible + apparaît au centre de la carte. Faites glisser la carte, puis « Valider la position » : seules les deux liaisons du point sont recalculées. |
| 8 | **Annuler / Rétablir** (flèches) | Les modifications sont défaites et refaites instantanément. |
| 9 | Liste des points | Départ, étapes et arrivée, avec la distance depuis le départ. Les flèches ↑↓ réorganisent les points, la corbeille les supprime. |
| 10 | Inverser le sens (⇅) | Le départ et l'arrivée sont échangés. |
| 11 | « Ligne droite » | Tout le parcours passe en liaisons directes orange. « Suivre les chemins » remet le calcul (déjà en mémoire, donc instantané). |
| 12 | **Mode avion** puis ajout d'un point | La liaison est tracée en orange, avec le message « Calcul impossible… connexion requise » et « Profil indisponible ». |
| 13 | **Enregistrer** | Nom et activité (pour la durée). Un avertissement s'affiche s'il reste des liaisons directes. Ensuite : « Suivre maintenant », « Carte hors ligne » ou « Fermer ». |
| 14 | Parcours › Itinéraires | Le parcours créé apparaît (« Créé sur la carte ») avec sa distance et le D+ / D- prévu. Il peut être suivi, en sens inverse aussi. |
| 15 | Durée estimée | Après au moins 3 sorties de plus d'1 km dans une activité, la mention devient « d'après vos sorties ». |

## À remonter

- La qualité des tracés calculés sur vos sentiers habituels : l'IGN emprunte-t-il bien les sentiers de randonnée, ou préfère-t-il les routes ?
- La durée estimée comparée à votre durée réelle.
- La facilité de placement et de déplacement des points au doigt.
