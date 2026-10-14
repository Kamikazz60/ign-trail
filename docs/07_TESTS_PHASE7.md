# Recette V1 — Phase 7 (stabilisation)

APK : `apk/IGN-Trail-1.0.apk` (version 1.0.0, signée avec la clé de production).
Elle s'installe **par-dessus** la version actuelle, comme une mise à jour : vos sorties sont conservées.

> Par précaution, avant l'installation : Statistiques › ⚙ Paramètres › Sauvegarde › **Exporter**.

## Déjà vérifié automatiquement

| Test du cahier des charges | Vérification | Résultat |
|---|---|---|
| C — Enregistrement prolongé | Test unitaire : 6 h de randonnée simulée (12 arrêts, montées/descentes, pertes de signal) | Distance à ±3 %, chronomètres cohérents à la seconde, D+ à ±10 % |
| C — Performances | Test unitaire : 6 h à vitesse élevée, 21 600 points, un instantané du tracé à chaque position | < 1 s de calcul au total (au lieu d'une copie du tracé complet à chaque position) |
| C — Écran verrouillé | Émulateur : écran éteint + mode Doze forcé, 5 min de marche simulée avec un arrêt | 312 m enregistrés pour 312 m simulés, pause détectée |
| H — Reprise après incident | Émulateur : plantage provoqué en pleine sortie | Service relancé par Android en 1 s, enregistrement poursuivi, coupure non reliée et comptée en « Interruption » ; rapport de plantage visible dans Paramètres › Diagnostic |
| B — Sans connexion | Émulateur : mode avion, cache temporaire vidé | Carte détaillée affichée depuis la zone téléchargée, mention « Hors connexion · carte téléchargée ici » |
| G — Export/import GPX | Tests unitaires (aller-retour, schéma officiel GPX 1.1) + import réel validé par l'utilisateur | Coordonnées (1 cm), altitudes et heures conservées |
| E — Pauses | Tests unitaires (pause auto, manuelle, reprise, trois chronomètres) | OK |
| I — Statistiques | Tests unitaires sur traces de référence (distance, vitesses, allure, dénivelé, vitesse max après retour du signal) | OK |
| Mise à jour signée | Émulateur : APK de production installée par-dessus l'ancienne version | Acceptée, données conservées |

Total : 121 tests unitaires, 0 échec.

## À faire sur le téléphone

### 1. Installation
Installer `IGN-Trail-1.0.apk` par-dessus l'application. Vérifier que les sorties sont toujours là.

### 2. Test A — Sortie immédiate
Ouvrir l'application, attendre « GPS ± x m », Démarrer, marcher 5 min, Terminer et enregistrer.

### 3. Test C — Longue sortie écran verrouillé (le plus important)
Batterie chargée, IGN Trail retiré de l'optimisation de batterie (lien proposé au démarrage).
Enregistrer une vraie randonnée de 2 h ou plus, téléphone verrouillé dans la poche.
À noter : pourcentage de batterie au départ et à l'arrivée, distance affichée par rapport à la distance connue.
**Repère** : moins de 8 à 10 % de batterie par heure, écran éteint.

### 4. Test B — Navigation hors ligne
Télécharger la zone (bouton ⬇ de la carte), créer ou importer un itinéraire, passer en mode avion,
« Suivre » l'itinéraire : carte affichée, guidage et alertes de sortie de parcours.

### 5. Test H — Reprise après incident
Pendant une sortie, fermer l'application de force (Paramètres Android › Applications › IGN Trail › Forcer l'arrêt),
puis la rouvrir : la sortie doit être proposée à la reprise, la trace déjà enregistrée intacte.

### 6. Tests D, E, F
- D : déplacer/zoomer la carte, vérifier qu'elle reste au cadrage, puis « Revenir au suivi GPS ».
- E : pause manuelle, pause automatique à l'arrêt, reprises ; vérifier les trois chronomètres.
- F : créer un itinéraire avec plusieurs points, en déplacer un, l'enregistrer, le suivre.

### 7. En cas de problème
Paramètres › Diagnostic indique un éventuel plantage : « Partager le rapport » (il ne contient ni position ni trace).
