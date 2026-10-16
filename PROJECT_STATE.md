# PROJECT_STATE — IGN Trail

Dernière mise à jour : 16/10/2026 (version 1.0.4, à valider sur téléphone)

## Avancement

| Phase | Statut |
|---|---|
| 0 — Analyse de faisabilité et architecture | ✅ Validée |
| 1 — Fondations (carte IGN, GPS, suivi/recentrage) | ✅ Validée sur téléphone par l'utilisateur |
| 2 — Enregistrement sportif | ✅ Validée sur téléphone par l'utilisateur |
| 3 — Cartes hors ligne | ✅ Validée sur téléphone par l'utilisateur |
| 4 — Navigation | 🧪 Livrée, **non testée sur téléphone** (l'utilisateur a demandé d'avancer sans la tester) |
| 5 — Création de parcours | 🧪 **Livrée, en attente de validation sur téléphone** (avec la Phase 4) |
| 6 — Fonctions avancées V1 | 🧪 Testée en partie sur téléphone (14/10/2026) ; corrections et évolutions livrées en 0.6.5 (`apk/IGN-Trail-phase6-5.apk`), à revalider |
| 7 — Stabilisation | 🧪 **Livrée (1.0.0, `apk/IGN-Trail-1.0.apk`)**, recette sur téléphone à faire (`docs/07_TESTS_PHASE7.md`) |

## Décisions de l'utilisateur à respecter

- **Coloration du tracé selon la vitesse (Phase 6) : dégradé INVERSÉ par rapport au cahier des charges.**
  - **Rouge = passages les plus lents** (souvent les montées, quand le cœur est « dans le rouge ») ;
  - **vert = passages les plus rapides** ;
  - teintes intermédiaires entre les deux.
  - Le §7 du cahier des charges (bleu = lent, rouge = très rapide) est donc remplacé par cette décision (13/10/2026).
- **Activité « Autre »** ajoutée aux trois profils du §5.1 (13/10/2026).
- **Validation par phase :** livrer une APK testable à la fin de chaque phase et ne pas commencer la suivante sans validation. Exception : le 13/10/2026, l'utilisateur ne pouvait pas tester et a demandé d'enchaîner la Phase 5 ; les Phases 4 et 5 seront testées ensemble.

## Décisions techniques

Le détail est dans [docs/01_ANALYSE_FAISABILITE_ET_ARCHITECTURE.md](docs/01_ANALYSE_FAISABILITE_ET_ARCHITECTURE.md).

**Phase 1**

- **Fond de carte :** Plan IGN vectoriel « classique » avec ombrage, sous licence Etalab 2.0. Mise en cache et téléchargement autorisés par l'IGN.
- **Moteur de carte :** MapLibre 13.6.1 en OpenGL ES.
- **Localisation :** Fused Location, avec repli sur le GNSS natif.
- **Indicateur de position :** entièrement vectoriel (cercles et triangle géographique, sans image).
- **Chaîne de compilation :** AGP 9.4.1, Gradle 9.8.1, Kotlin 2.4.21, JDK 25. Compilation SDK 37, cible 36, minimum 29.

**Phase 2**

- **Moteur d'enregistrement `core:tracking`, en Kotlin pur et déterministe :**
  - **Filtre de Kalman** position/vitesse sur chaque axe. Il exploite la vitesse Doppler : il suit les virages et immobilise la position à l'arrêt.
  - **Doppler non fiable :** si un récepteur annonce une vitesse nulle en se déplaçant (3 contradictions), son Doppler est ignoré pour la session.
  - **Points :** rejet des points aberrants (vitesse maximale par activité). Un point n'est ajouté que s'il s'éloigne de plus de 2 σ du précédent (entre 4 et 15 m).
  - **Perte de signal de plus de 60 s :** nouveau segment, jamais de ligne droite.
  - **Pause automatique :** déclenchée après une immobilité confirmée par le Doppler. Le délai est réglable (désactivée, 10 s, 30 s, 1 min ou 2 min) et la période d'arrêt est requalifiée en pause rétroactivement. La reprise exige 2 mesures hors du rayon d'arrêt.
  - **Pause manuelle :** jamais levée automatiquement. La reprise ouvre un nouveau segment.
  - **Chronomètres :** total = déplacement + pause + interruption.
  - **Vitesse max :** mesurée sur une fenêtre glissante de 15 s.
  - **Dénivelé :** altitude au-dessus du niveau de la mer (`AltitudeConverterCompat`), moyenne glissante sur 15 mesures, hystérésis de 8 m.
- **Stockage (Room, base privée en WAL) :** **toutes les mesures brutes** (§5.2 : précision, altitude, vitesse, source, décision du filtre, état de la session) et les événements (pauses, interruptions, arrêt). Écriture par lots de 20 points ou toutes les 10 s. Les statistiques sont **recalculables par rejeu** des données brutes (test de rejeu identique).
- **Service au premier plan de type `location` :**
  - notification persistante avec chronomètre système et bouton Pause / Reprendre ;
  - en `START_STICKY` : après un plantage, le système le relance et l'enregistrement reprend automatiquement (testé) ;
  - pas d'autorisation de localisation en arrière-plan.
- **Après un redémarrage du téléphone :** notification « Randonnée interrompue », puis reprise manuelle (décision validée). La coupure est comptée comme interruption et n'est pas reliée.
- **Injection de dépendances :** `RecordingManager`, singleton du conteneur, sur un fil unique. C'est lui qui pilote le moteur, la base et le service.

**Phase 3**

- **Cartes hors ligne :** gestionnaire hors ligne intégré de MapLibre, derrière l'interface `OfflineMaps`, que l'on peut remplacer sans toucher au GPS.
  - **Zone libre :** rectangle choisi dans un cadre sur la carte.
  - **Autour d'un parcours :** bande de 1, 2 ou 5 km, faite de rectangles le long du tracé simplifié (`Corridor`, en Kotlin pur et testé).
  - **Téléchargement :** zooms 4 à 14, 15 ou 16 selon le niveau de détail.
- **Style Plan IGN adapté à la volée (`IgnStyleInterceptor` sur le client OkHttp de MapLibre) :**
  1. **Ajout de l'ombrage du relief dans le style :** il est ainsi inclus dans les téléchargements hors ligne (MapLibre ne télécharge que les sources du style, et seulement par HTTP).
  2. **Source vectorielle plafonnée au zoom 16 :** agrandissement au-delà, rendu identique en ligne et hors ligne, sans télécharger les zooms 17-18 (numéros de rue).
  - **Méthode :** modification **textuelle ciblée** (`JsonScanner`), car le style IGN contient des clés en double qu'une réécriture JSON complète altérerait. Un test vérifie que le reste du style est conservé à l'octet près.
- **Droits :** la FAQ IGN autorise la mise en cache et le téléchargement de Plan IGN. Le téléchargement se fait uniquement à la demande, limité à 150 000 tuiles, avec au plus 12 requêtes simultanées vers l'IGN.
- **Reprise et intégrité :**
  - les téléchargements en cours sont mémorisés et repris automatiquement au lancement suivant ;
  - une zone n'est « disponible » que si `completedResourceCount == requiredResourceCount`.
- **Disponibilité « disponible / partielle / indisponible » :** calculée par recouvrement des tuiles au zoom 14 avec les zones complètes. Elle est affichée avant le départ, dans les feuilles de téléchargement et sur la carte quand il n'y a plus de réseau.
- **Service au premier plan de type `dataSync` :** le téléchargement continue application quittée, avec une notification de progression.
- **Estimation de la taille :** moyennes mesurées par zoom sur la Géoplateforme, divisées par 2 après comparaison avec des téléchargements réels (elle reste prudente).
- **OkHttp 4.12.0 :** même version que celle embarquée par MapLibre (la 5.x évitée pour ne pas créer de conflit).

**Phase 4**

- **Nouveaux modules en Kotlin pur et testés :**
  - **`core:gpx` :** lecture SAX des formats GPX 1.0 et 1.1 (traces, routes, points nommés), protégée contre les entités externes (XXE). Elle signale les points invalides et les traces multiples, et allège les fichiers de plus de 50 000 points. Un module d'écriture GPX 1.1 est prêt pour l'export de la Phase 6.
  - **`core:navigation` :** projection d'une position sur l'itinéraire, distance restante, distance au tracé, et flèche visant un point **30 m plus loin sur le tracé** (pas la destination). Hors parcours, la flèche vise le point le plus proche du tracé.
- **Sortie de parcours :**
  - **Détection :** confirmée après 3 mesures au-delà de la tolérance (20, 40 ou 80 m, réglable).
  - **Retour sur le parcours :** sous 70 % de la tolérance, confirmé par 2 mesures. Cette hystérésis évite les alertes répétées.
- **Recherche de la position sur le tracé :** elle se fait autour de la progression connue, et à égalité c'est la branche située devant qui l'emporte. On ne saute donc pas sur le retour d'un aller-retour.
- **Base de données version 2 :** tables d'itinéraires (`routes`, `route_points`, `route_waypoints`), avec **migration automatique** depuis la version 1. Vérifiée sur l'émulateur avec les données de la Phase 3.
- **`NavigationManager`, singleton :** il est alimenté par le même service au premier plan que l'enregistrement. Le service reste actif tant qu'un enregistrement **ou** un guidage est en cours, et sa notification combine les deux, avec un bouton « Arrêter le guidage ».
- **Alertes (`GuidanceAlerts`) :** vibration, bip (`ToneGenerator`) et notification prioritaire en cas de sortie de parcours. Elles fonctionnent écran verrouillé.
- **Ouverture de fichiers GPX par « Ouvrir avec » ou « Partager » :** filtres d'intention larges (beaucoup d'applications envoient `application/octet-stream`), puis validation du contenu. La lecture est limitée à 30 Mo.
- **Retour au départ :**
  - « Retour sur mes pas » = guidage sur la trace en cours, inversée ;
  - « Direction du départ » = flèche, distance et cap à vol d'oiseau, avec un avertissement explicite.
- **Affichage :** l'itinéraire est en **violet**, distinct du tracé réalisé. Le trait vers le tracé est **continu**, car les pointillés MapLibre ne s'affichaient pas sur le rendu de l'émulateur.

**Phase 5**

- **Nouveau module `core:planning`, en Kotlin pur et testé :**
  - `PlanEditor` : ajout, insertion, déplacement, suppression, réorganisation et inversion des points, avec Annuler / Rétablir (historique limité à 100) ;
  - `Leg` / `Section` : chaque liaison est formée de tronçons **ROUTED** ou **STRAIGHT** ;
  - `Sampling` : rééchantillonnage du tracé ;
  - `ElevationProfile` : D+ / D- avec un seuil de 2 m (modèle de terrain lisse) et comblement des altitudes manquantes ;
  - `DurationEstimator` : **règle DIN 33466** (marche horizontale et dénivelé : le plus long des deux plus la moitié de l'autre), avec la vitesse personnelle de l'utilisateur (médiane des sorties de l'activité, si au moins 3 sorties de plus d'1 km).
- **Calcul d'itinéraire IGN** (`bdtopo-osrm`, profil piéton, chemin le plus court), derrière l'interface `RoutingProvider`, que l'on peut remplacer :
  - **chaque liaison est calculée séparément et mise en cache** : déplacer un point ne recalcule que deux liaisons, et Annuler est instantané ;
  - **limite de l'IGN respectée :** une requête toutes les 250 ms au plus.
- **Point découvert pendant les tests :** le service IGN raccroche un point au chemin le plus proche **quelle que soit la distance** (un point en mer est relié à une route bretonne).
  - Au-delà de 300 m d'écart, le calcul est refusé et remplacé par une liaison directe signalée.
  - En deçà, l'écart entre le point et le chemin est ajouté comme liaison directe, en orange.
- **Liaisons directes (§8.2) :**
  - affichées en **orange**, avec un trait continu translucide sous les pointillés (les pointillés MapLibre ne s'affichent pas partout) ;
  - leur longueur est indiquée dans le panneau, avec un avertissement à l'enregistrement.
- **Mode global :** « Suivre les chemins » / « Ligne droite » s'applique à tout le parcours.
- **Altimétrie IGN** (RGE ALTI®) : points tous les 20 m au minimum (4 000 points au plus), par lots de 2 000 en POST, interrogée 600 ms après la dernière modification. La valeur -99999 signifie hors zone.
- **Enregistrement :** le parcours devient un `Route` de type CREATED. Chaque sommet reçoit l'altitude du modèle de terrain, et les étapes intermédiaires deviennent des repères « Étape n ». Le parcours se suit ensuite comme un itinéraire importé (Phase 4).
- **Interface :** dans l'onglet Créer, la même carte est utilisée sans suivi GPS. Un appui place un point, ou sélectionne un point existant s'il est à moins de 28 dp. Le déplacement se fait avec une cible au centre de la carte, ce qui est plus fiable au doigt que le glisser d'un marqueur. Le profil est repliable.

## Fonctionnalités opérationnelles

**Phase 1 :** carte IGN, GPS, suivi et recentrage, indicateur de position, fonds de carte (détail dans [docs/02_TESTS_PHASE1.md](docs/02_TESTS_PHASE1.md)).

**Phase 2 :**

- Démarrage immédiat avec choix de l'activité (dernier profil présélectionné) et du délai de pause automatique, plus un conseil sur l'optimisation de batterie.
- Panneau compact (distance, durée, vitesse ou allure en course, D+) qui se déploie pour afficher toutes les statistiques du §5.2.
- Mode plein écran. Pause, Reprendre et Terminer, avec confirmation avant de terminer ou de supprimer.
- Tracé rouge sur la carte, avec des segments jamais reliés.
- Résumé de fin avec nom modifiable. L'onglet Parcours liste les sorties enregistrées.
- Reprise après plantage (automatique) ou après redémarrage (manuelle).

**Phase 3 :**

- Écran « Cartes hors ligne », accessible depuis le menu des fonds de carte. Il montre l'espace utilisé et libre, et chaque zone avec son état, sa progression, Pause / Reprendre, Voir et Supprimer (avec confirmation). Un bouton vide aussi le cache temporaire.
- Téléchargement d'une zone libre (cadre sur la carte) ou autour d'une randonnée enregistrée (aussi depuis l'onglet Parcours).
- Estimation avant téléchargement, avec avertissements (zone trop grande, espace insuffisant, données mobiles, hors connexion).
- Affichage hors connexion de la carte et du relief, avec la pastille « Hors connexion · carte téléchargée ici / non téléchargée ici ».
- Vignette d'activité **Autre**, avec une grille d'activités en 2 × 2.

**Phase 4 :**

- Onglet Parcours avec deux onglets, Randonnées et Itinéraires. Pour chacun : Voir, Suivre, Sens inverse (itinéraires), Carte hors ligne et Supprimer (itinéraires).
- Import GPX par le sélecteur de fichiers, « Ouvrir avec » ou « Partager ».
- Aperçu de l'itinéraire sur la carte avant de le suivre.
- Bandeau de guidage, réglages de tolérance et d'alertes.
- Retour sur mes pas et Direction du départ pendant un enregistrement.
- La bande hors ligne autour d'un parcours accepte aussi les itinéraires importés.

**Phase 5 :**

- Onglet Créer : placement des points sur la carte, calcul sur les chemins, estimations (distance, D+, D-, durée), profil interactif, déplacement, suppression, liste réorganisable, inversion, Annuler / Rétablir, mode ligne droite.
- Enregistrement, puis « Suivre maintenant » ou « Carte hors ligne ».

## Tests

**Tests unitaires : 97 tests, tous réussis** :

- Phase 5 : `PlanningTest` (10 : édition, Annuler / Rétablir, raccrochage au réseau accepté ou refusé, assemblage, rééchantillonnage, profil, durée DIN 33466).
- Phase 4 : `GpxTest` (8 : GPX 1.0 et 1.1, erreurs, XXE, **aller-retour export / import avec coordonnées, altitudes et heures identiques**) et `NavigatorTest` (11 : flèche le long du tracé, sortie confirmée et alerte unique, hystérésis contre le bruit, retour, aller-retour, sens inverse, arrivée, direction du départ).
- Phase 3 : `TilesTest` (8 : tuiles, bande autour d'un parcours, simplification, couverture) et `StyleInjectionTest` (6 : relief, conservation du style à l'octet près, zoom 16, idempotence).
- Détail des Phases 1 et 2 ci-dessous.

- `TrackRecorderTest` (19) :
  - distance à 3 % près sous bruit GPS ;
  - pause automatique et reprise ;
  - pause manuelle jamais levée ;
  - point aberrant ;
  - pertes de signal courte et longue ;
  - virages en épingle ;
  - Doppler absent ou toujours nul ;
  - vitesse max ;
  - interruption ;
  - **rejeu identique**.
- `ElevationTest` (5) : terrain plat bruité, montée de 300 m, aller-retour, pause.
- `RecordingStatsTest` (3) : valeurs de la maquette (12,6 km, 4,3 km/h, 13'51"/km).
- Phase 1 : `GeoMathTest`, `ScaleBarTest`, `CameraFollowControllerTest`, `HeadingSelectorTest`.
- `FormatTest` (3) : formats français.

**Validé sur émulateur (Android 15) :**

- démarrage, service au premier plan avec sa notification ;
- marche écran verrouillé et application en arrière-plan ;
- pause manuelle non levée malgré 1 min de marche ;
- pause automatique, puis reprise automatique ;
- plein écran ;
- arrêt avec confirmation, résumé, liste ;
- processus tué en plein enregistrement : boîte de reprise, puis Reprendre ou Terminer ;
- plantage forcé (`am crash`) : relance du service par le système en 1 s et reprise automatique, avec 6 s d'interruption comptées ;
- APK optimisée R8 : enregistrement complet sans plantage.

**Phase 5, validé sur émulateur :**

- placement de 3 points, avec un tracé IGN qui suit les rues et chemins réels de Chamonix ;
- profil (min 1038 m, max 1082 m) ;
- liaisons directes visibles et signalées ;
- passage en ligne droite, puis retour au calcul sur les chemins (cache) ;
- Annuler ;
- déplacement d'une étape et liste des points ;
- enregistrement puis « Suivre maintenant » : guidage actif sur le parcours créé ;
- APK optimisée.

**Phase 4, validé sur émulateur :**

- migration de la base de la Phase 3 ;
- import par le sélecteur d'un fichier annoncé comme « BIN » ;
- aperçu, suivi, distance restante ;
- sortie de parcours : bandeau rouge, notification « Vous avez quitté le parcours », puis retour ;
- sens inverse ;
- Direction du départ et Retour sur mes pas pendant un enregistrement ;
- APK optimisée.

**Non vérifié sur émulateur :** l'ouverture d'un GPX depuis une autre application (« Ouvrir avec » / « Partager »), la vibration et le son, faute de matériel.

**Phase 3, validé sur émulateur :**

- sélection et téléchargement d'une zone ;
- **mode avion + cache vidé : carte et relief affichés depuis la zone téléchargée**, jusqu'au zoom maximal ;
- bande autour d'un parcours avec estimation et disponibilité partielle ;
- **application tuée à 48 % : reprise automatique au relancement, puis zone complète** ;
- suppression ;
- grille d'activités avec « Autre » ;
- APK optimisée R8 hors connexion.

## Problèmes connus / limites

- **Dénivelé en GPS seul :** l'hystérésis de 8 m ne compte pas une dernière variation inférieure à 8 m (exemple : 12 m de montée comptés 8 m). Le baromètre et l'altimétrie IGN sont prévus en Phase 6.
- **Fréquence GPS :** 1 mesure/s, 2/s au-delà de 30 km/h (0.6.2). Pas de ralentissement à l'arrêt : il retarderait la reprise automatique. Consommation réelle à mesurer sur téléphone (test C).
- **Émulateur :** il annonce une vitesse Doppler nulle, d'où une distance sous-estimée d'environ 10 % en simulation, que le moteur corrige en partie. Ce défaut n'existe pas sur un vrai GPS (voir les tests unitaires).
- **Coupure brutale :** une écriture terminée moins de deux secondes avant une coupure brutale (batterie arrachée) peut être perdue. La session est alors proposée à la reprise.
- **Hors périmètre de cette phase :**
  - boutons Photo et Point d'intérêt de la maquette (Phase 6) ;
  - coloration du tracé selon la vitesse (Phase 6) ;
  - fiche détaillée, renommage et suppression depuis la liste (Phase 6).
- Une seule fois, en début de test sur l'émulateur, la carte n'a pas reçu de positions alors que le service en recevait. Le problème ne s'est pas reproduit, y compris avec des journaux de diagnostic. **À surveiller sur téléphone.**
- Signature : clé de production depuis la 1.0.0 (voir Phase 7). Les APK 0.x (clé de debug) ne s'installent plus par-dessus.

## Reprise de la Phase 6 (à faire en premier)

Écrit et compilé : couleurs du tracé selon la vitesse (rouge = lent, vert = rapide ; seuils fixes ou personnels),
baromètre pour le dénivelé, base de données v3 (pression, points d'intérêt, photos), points d'intérêt (bouton,
appui long, catégories, photos appareil/galerie), fiche de randonnée (mini-carte, statistiques recalculées,
profil relié à la carte, points, photos, partage GPX), recherche et tri de l'historique, onglet « Points »,
tableau de bord statistique, écran Paramètres (unité, couleurs, guidage, stockage, confidentialité),
sauvegarde/restauration ZIP. APK : `apk/IGN-Trail-phase6-non-testee.apk`.

Reste à faire : test complet sur émulateur (migration v2→v3, coloration, POI + photo, fiche, export GPX,
sauvegarde/restauration), corrections éventuelles, guide `docs/07_TESTS_PHASE6.md`, mise à jour du README.

## Retours terrain du 14/10/2026 et corrections (version 0.6.1)

| Retour | Analyse | Correction |
|---|---|---|
| Points d'intérêt | OK | — |
| Temps, distance, tracé coloré | OK | Les seuils de couleur sont bien calculés séparément par activité (confirmé). |
| Vitesse max 73 km/h pour 55 réels | Calcul par distance sur 15 s, gonflé par les sauts de position (activité « Autre » : jusqu'à 90 km/h acceptés). | Vitesse max = **médiane sur 5 s de la vitesse Doppler** de la puce GPS ; repli sur la distance (fenêtre de 30 s) sans Doppler fiable. Test reproduisant 54 km/h avec sauts de 40-60 m. Les sorties existantes sont corrigées à l'affichage de leur fiche (recalcul depuis les mesures brutes). |
| Poubelle de l'onglet Créer efface tout | Bouton « tout effacer » sans confirmation. | Confirmation obligatoire ; « Annuler » reste possible ensuite. |
| GPX refusé par calculitineraires.fr | **Coordonnées proches de 0 écrites en notation scientifique** (« 1.0E-5 »), interdite par le format GPX (longitudes autour du méridien de Greenwich, ouest de la France). | Nombres en notation décimale (7 décimales = 1 cm). **Nouveau test : chaque export est validé contre le schéma officiel GPX 1.1** (topografix.com). |
| Tracé à côté du chemin dans un lacet à ~50 km/h (vélo) | Simulation : avec la vitesse Doppler, le filtre reste à moins de 5,5 m du vrai tracé ; sans Doppler 7-8 m. Cordes de 14 m entre deux positions à 1 Hz. **Défaut grave découvert** : en Marche/Randonnée/Course, à 50 km/h, 80 % des points étaient rejetés comme aberrants (trace perdue si l'on monte en voiture ou à vélo sans changer d'activité). | (0.6.2) **Détection de manœuvre** dans le filtre de Kalman (incertitude élargie quand la mesure s'écarte de la trajectoire prévue) : épingle de 10 m de rayon à 50 km/h, écart 5,4 → 3,7 m. **Un saut confirmé par la vitesse Doppler n'est plus rejeté** (trace conservée quelle que soit l'activité ; un vrai point aberrant à pied reste rejeté). **2 positions/s au-delà de 30 km/h** (retour à 1/s sous 20 km/h). 3 tests ajoutés. |

## Évolutions demandées le 14/10/2026 (version 0.6.3, `apk/IGN-Trail-phase6-3.apk`)

| Demande | Réalisation |
|---|---|
| Une seule couleur pendant l'activité | Tracé en direct bleu uni ; plus de légende pendant l'enregistrement. |
| Couleurs calculées à la fin, d'après la moyenne de la sortie, puis figées | À l'enregistrement : moyenne = jaune, lent (rouge) = min(20e centile, moy. × 0,92), rapide (vert) = max(80e centile, moy. × 1,08), sur des tronçons ≥ 60 m et 20 s. Seuils stockés dans la sortie (base v4 : `color_slow_mps`, `color_usual_mps`, `color_fast_mps`) et réutilisés tels quels dans la fiche. Anciennes sorties : calcul unique à la première ouverture. Les modes « seuils fixes / personnels » des Paramètres sont supprimés. Moins de 300 m ou de 3 tronçons : tracé uni. |
| Gestion des cartes hors ligne depuis la carte | Bouton « téléchargement » dans la colonne de droite + « Cartes hors ligne… » dans le menu des fonds. |
| Voir sur la carte ce qui est déjà téléchargé | Calque « Zones téléchargées » (carrés de tuiles zoom 14 : vert = complet, orange = incomplet), activable dans le menu des fonds, toujours affiché pendant la sélection d'une zone. |
| Ne télécharger que ce qui manque | MapLibre réutilise les tuiles déjà présentes dans sa base (vérifié dans le code source : recherche de ressource commune à toutes les régions). L'estimation affiche la part déjà disponible et la taille restante à télécharger. |
| Import d'un parcours GPX | Menu des fonds › « Importer un parcours (GPX)… » (aperçu immédiat, « Suivre », « Sens inverse ») ; aussi Parcours › Itinéraires et « Ouvrir avec / Partager » depuis une autre application. |
| Activité « Autre » à nommer | Champ « Nom de l'activité » + étiquettes déjà utilisées (réutilisables) ; nom par défaut « Ski du 14 octobre 2026 » ; filtre par étiquette dans les statistiques. Base v4 : `activity_label`. |
| Corrigé au passage | Le point GPS ne suivait plus la carte pendant un enregistrement (la carte reçoit maintenant aussi les positions du service). Changer d'onglet ferme la fiche ouverte. Titres neutres « sortie » (au lieu de « randonnée ») et onglet « Sorties ». |

Vérifié sur émulateur : tracé uni en direct et suivi du point, couleurs + légende après « Terminer et enregistrer », fiche avec couleurs figées et nom personnalisé, calque des zones, menu des fonds, import GPX, build release.

## Retour du 14/10/2026 après-midi (version 0.6.4, `apk/IGN-Trail-phase6-4.apk`)

Import/export GPX validés sur téléphone par l'utilisateur.

| Retour | Correction |
|---|---|
| Carte de la fiche difficile à déplacer au doigt | Cause : la mini-carte est dans une page qui défile, les deux se disputaient le geste. La mini-carte est désormais un aperçu (le défilement de la page reste fluide). |
| Carte en plein écran au toucher | Un appui sur l'aperçu (badge « Agrandir ») ouvre la carte en plein écran, sans barre d'onglets : déplacement et zoom libres, bouton « Recadrer sur le parcours », fermeture par ✕ ou Retour. |
| Échelle de vitesse gênante sur la carte | Légende déplacée sous la carte (fiche et plein écran). |

## Vitesse max 99,5 km/h sur un trajet en voiture (version 0.6.5, `apk/IGN-Trail-phase6-5.apk`)

Trace GPX analysée (trajet en voiture, activité « Autre », vitesse réelle ≤ ~80 km/h) : à 07:46:31,
perte de signal de 5 s, la position revient 190 m plus loin (138 km/h apparents) et le tracé est coupé en
deux segments. Au retour du signal, la puce annonce des vitesses fausses pendant quelques secondes ; la
médiane Doppler démarrait dès 3 mesures, donc sur ces seules valeurs.

Correction (`TrackRecorder`) : la vitesse max n'est plus mesurée pendant 5 s après un retour du signal
(trou > 2,5 s ou nouveau segment) ; la médiane exige une fenêtre continue d'au moins 3 s ; elle est
plafonnée par le déplacement brut mesuré (ligne droite + précision, × 1,1). La fiche recalcule les
statistiques depuis les mesures brutes : les anciennes sorties sont corrigées à leur réouverture.
2 tests ajoutés (118 tests, 0 échec).


## Phase 7 — Stabilisation (version 1.0.0, `apk/IGN-Trail-1.0.apk`)

Guide de recette : `docs/07_TESTS_PHASE7.md`.

| Sujet | Réalisation |
|---|---|
| Longues sorties (performances, batterie) | Le tracé n'est plus recopié à chaque position : `AppendOnlyList` (core/tracking), instantanés gratuits. Tracé en direct simplifié à 1 m par blocs de 200 points mis en cache, sans calcul de couleurs, rafraîchi au plus 1 fois/s. Test : 6 h / 21 600 points en < 1 s de calcul total. |
| Tests longue durée | `LongRecordingTest` : 6 h de randonnée (arrêts, dénivelé, pertes de signal) — distance ±3 %, chronomètres, D+ ; performance ; instantanés figés. |
| Écran verrouillé / Doze | Émulateur, Doze forcé : 312 m enregistrés pour 312 m simulés. |
| Plantage en cours de sortie | Émulateur : service relancé par Android en 1 s, enregistrement poursuivi, coupure comptée en interruption. |
| Hors connexion | Émulateur, mode avion + cache vidé : carte détaillée depuis la zone téléchargée. |
| Journal de plantage local | `diagnostic/CrashLog` : dernier plantage enregistré dans le stockage privé (version, Android, modèle, pile d'appels ; ni position ni trace), Paramètres › Diagnostic › Partager / Effacer. Aucun envoi automatique. |
| Clé de production | `../IGN-Trail-cle-signature/` (hors projet, **à sauvegarder**) : clé RSA 4096 + lignée de rotation APK v3 depuis la clé de debug → la 1.0.0 s'installe comme une mise à jour (vérifié sur émulateur). Signature : `scripts/signer-production.sh` (README). |
| Divers | Libellés « Sorties » au lieu de « Randonnées » dans les Paramètres. |

Tests : 121 tests unitaires, 0 échec.

Limites connues :
- Consommation de batterie non mesurée sur un vrai téléphone (l'émulateur ne le permet pas) : test C à faire.
- Sur émulateur seulement (Doppler nul), la vitesse max est estimée par la distance : +15 % constaté.
- Après un plantage, la distance parcourue pendant la seconde de redémarrage n'est pas comptée (coupure non reliée, par principe).
- Une APK signée avec la seule clé de debug (`assembleRelease`/`assembleDebug` non re-signée) est désormais refusée par un téléphone qui a la 1.0.0 : toujours passer par le script.

Prochaine étape : recette sur téléphone (`docs/07_TESTS_PHASE7.md`), puis corrections éventuelles. Les fonctions V2
(mode fantôme, comparaison, routage hors ligne) restent hors périmètre.

## Retour terrain du 15/10/2026 : chemin inventé en forêt (version 1.0.1, `apk/IGN-Trail-1.0.1.apk`)

Trace analysée (activité personnalisée, ~30-45 km/h, traversée du bois du Fay) : 59 s sans signal sous les
arbres, reliées par une **ligne droite de 470 m à travers bois** (le seuil de coupure était de 60 s) ; au retour
du signal, 7 s de positions décalées vers le nord-ouest (80-95 km/h apparents, acceptés car < 90 km/h, la limite
de l'activité « Autre »), puis 15 s de silence et retour sur le chemin.

Corrections (`TrackRecorder`) :
- **Pas de ligne droite à travers une perte de signal** : après plus de 10 s sans mesure, si l'on s'est déplacé
  de plus de 50 m, le tracé est coupé. La distance en ligne droite (minimum certain) reste comptée si la perte
  dure moins de 3 min et que la vitesse correspondante est plausible.
- **Période d'essai au retour du signal** : pendant 30 s après une coupure de plus de 10 s, seules les mesures
  précises à 20 m ou mieux sont acceptées.
- **Contrôle par la vitesse Doppler** : un saut plus rapide que 1,5 × la vitesse mesurée par la puce + 8 m/s est
  rejeté, même s'il reste sous la vitesse maximale de l'activité.

2 tests ajoutés (forêt à vélo, positions décalées précises) ; 123 tests, 0 échec. Les fiches des sorties déjà
enregistrées sont recalculées avec ces règles à leur ouverture (mesures brutes conservées).

## Tracé décalé à travers les maisons (version 1.0.2, `apk/IGN-Trail-1.0.2.apk`, à valider sur téléphone)

Même trace, après le bois : tracé parallèle aux rues mais décalé de 30 à 50 m, à travers les maisons, jusqu'à
l'arrivée. Décalage régulier typique de positions calculées par Wi-Fi / antennes : le mode « fused » des services
Google les fournit quand les satellites sont mal captés.

Correction :
- **Enregistrement et guidage en satellites seuls** (`LocationSources.createSatellite` → `PlatformLocationSource`,
  fournisseur GPS d'Android). Sans satellite, aucune position : le moteur coupe le tracé (1.0.1) au lieu d'en
  inventer un. La carte garde le mode « fused » avant le départ (position rapide).
- Pendant une sortie, la carte suit les positions de l'enregistrement et ignore les siennes (`FixArbiter`, testé),
  pour que le point ne saute pas entre positions satellites et Wi-Fi.

127 tests, 0 échec. **Non vérifié sur émulateur** : l'émulateur est devenu inutilisable sur ce PC (interface
système bloquée, services Android absents après redémarrage). Hypothèse Wi-Fi à confirmer avec les mesures brutes
(précision de chaque point) d'une sauvegarde.

## Trous de 15 s sur le tracé (version 1.0.3, `apk/IGN-Trail-1.0.3.apk`, à valider sur téléphone)

Analyse des mesures brutes (sauvegarde de l'utilisateur, deux trajets du 15/10) :
- Matin, mode « fused » : précision médiane 17,6 m (22 m dans le bourg), jusqu'à 68 m → positions Wi-Fi, d'où le
  tracé décalé. Confirme le choix de la 1.0.2.
- Soir, satellites seuls (1.0.2) : précision médiane 5,9 m (jamais plus de 19 m), mais **14 trous de 15,0 s
  exactement**, chacun au passage au-dessus de 30 km/h ou sous 20 km/h : le changement de fréquence (0.6.2) obligeait
  à se réabonner au GPS, qui ne renvoie alors rien pendant 15 s. La puce ne fournit de toute façon qu'une position
  par seconde (aucun intervalle de 0,5 s observé, dans aucun des deux modes).
- En campagne, matin et soir sur la même route : 5,4 m d'écart médian.

Correction : fréquence fixe d'une position par seconde, un seul abonnement GPS pour toute la sortie
(`TrackingService`). 127 tests, 0 échec. Non vérifié sur émulateur (inutilisable sur ce PC).

## Rotation de la carte et boussole (version 1.0.4, `apk/IGN-Trail-1.0.4.apk`, à valider sur téléphone)

Décision de l'utilisateur (16/10/2026), qui remplace le « nord toujours en haut » du §3.3 : retour d'un ami, le
« N » fixe était pris pour une boussole et ne donnait pas le nord réel.
- Rotation de la carte à deux doigts (seuil relevé pendant un zoom pour éviter les rotations involontaires ;
  pas d'inclinaison 3D).
- Le « N » devient une boussole de carte : il tourne avec la carte pour montrer où est le nord ; un appui remet le
  nord en haut. Aussi sur la carte plein écran d'une sortie.
- Orientation conservée quand la carte est recréée (changement d'onglet, rotation de l'écran).
- La flèche de direction du point bleu et la sélection de zone hors ligne étaient déjà indépendantes de
  l'orientation.

Vérifié sur émulateur : boussole affichée (« nord en haut »), appui sans erreur, carte et position GPS OK. La
rotation à deux doigts n'est pas testable sur l'émulateur (un seul doigt simulé) : à vérifier sur téléphone.
