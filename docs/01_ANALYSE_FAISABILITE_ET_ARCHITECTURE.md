# IGN Trail — Analyse de faisabilité et architecture proposée

Date : 13 octobre 2026 · Statut : **à valider avant tout développement**

Toutes les URL citées ci-dessous ont été **testées le 13/10/2026** et répondent HTTP 200.

---

## 1. Cartographie IGN : ce qui est possible, ce qui ne l'est pas

### 1.1 Couches testées

| Couche | Endpoint | Licence | Clé | Hors ligne autorisé |
|---|---|---|---|---|
| **Plan IGN vectoriel** (styles `standard` et `classique`) | `https://data.geopf.fr/tms/1.0.0/PLAN.IGN/{z}/{x}/{y}.pbf`, zoom 0–18 | Etalab 2.0 (ouverte) | Non | **Oui** (réutilisation libre avec attribution) |
| Plan IGN v2 raster | WMTS `GEOGRAPHICALGRIDSYSTEMS.PLANIGNV2` | Etalab 2.0 | Non | Oui |
| Courbes de niveau | WMTS `ELEVATION.CONTOUR.LINE` | Etalab 2.0 | Non | Oui |
| Ombrage du relief | WMTS `ELEVATION.ELEVATIONGRIDCOVERAGE.SHADOW` | Etalab 2.0 | Non | Oui |
| Orthophotos | WMTS `ORTHOIMAGERY.ORTHOPHOTOS` | Etalab 2.0 | Non | Oui |
| **SCAN 25 (carte TOP 25)** | `data.geopf.fr/private/wmts?apikey=…` | **Propriétaire** | Oui | **Non pour un particulier** |

Les tuiles vectorielles Plan IGN contiennent notamment `routier_chemin` (chemins et sentiers), `oro_courbe` (courbes de niveau), `oro_relief`, la végétation, l'hydrographie et les toponymes. Ce sont les informations dont un randonneur a besoin.

### 1.2 Point bloquant n° 1 : le SCAN 25

La maquette fournie ressemble au SCAN 25. Or :

- Le SCAN 25 **n'est pas en open data**. D'après la fiche officielle, les particuliers ne peuvent pas le télécharger, même pour un usage personnel. Ils peuvent seulement le consulter dans l'explorateur cartes.gouv.fr.
- Le flux est gratuit pour un **usage professionnel ou associatif**, via une clé personnelle créée sur cartes.gouv.fr, qui demande un SIRET. Un usage grand public est payant.
- La clé `ign_scan_ws` fonctionne encore (testée), mais c'est une **clé transitoire partagée**. Les CGU de la Géoplateforme précisent que les clés « vous sont personnelles et vous ne devez pas les partager ou les transférer ». L'intégrer dans l'APK, ou télécharger massivement des tuiles avec elle, ne serait pas conforme.

**Proposition :** utiliser par défaut le **Plan IGN vectoriel, style « classique »**. Ce style reprend volontairement la symbologie des cartes IGN, et on lui ajoute un ombrage du relief. Le résultat est très proche de la maquette, 100 % légal et téléchargeable hors ligne. Le SCAN 25 resterait une **couche optionnelle, en ligne seulement**, activée uniquement si l'utilisateur saisit sa propre clé dans les paramètres. Son téléchargement hors ligne serait désactivé.

### 1.3 Limites d'usage

- WMTS public : « non soumis à limite d'usage » selon les CGU. Les téléchargements hors ligne seront tout de même bridés (≈ 8 requêtes simultanées) par correction envers le service.
- Les dépassements renvoient une erreur HTTP 429, à gérer avec un délai croissant avant chaque nouvel essai.
- Attribution obligatoire : « © IGN – Plan IGN » (Etalab 2.0), toujours visible sur la carte.

### 1.4 Itinéraires balisés (GR, PR)

Les marquages « GR 76 » de la maquette ne figurent pas dans le Plan IGN : les tracés FFRandonnée sont des données propriétaires. Les chemins eux-mêmes sont bien affichés. Une couche OSM de sentiers balisés pourra être évaluée en V2. **Limitation acceptée pour la V1.**

---

## 2. Cartes hors ligne

**Moteur : MapLibre Native Android 13.6.1**, avec son `OfflineManager` intégré.

| Besoin du cahier des charges | Solution MapLibre |
|---|---|
| Zone libre | `OfflineTilePyramidRegionDefinition` (bbox + zooms min/max) |
| Autour d'un itinéraire (marge de 2 km) | `OfflineGeometryRegionDefinition` : on calcule un polygone tampon autour du tracé, et seules les tuiles qui le recouvrent sont téléchargées |
| Reprise après interruption | Le téléchargement d'une région reprend là où il s'était arrêté (`setDownloadState(ACTIVE)`) |
| Vérification d'intégrité | `completedResourceCount == requiredResourceCount` et absence d'erreur. Sinon, la zone est marquée « partielle » |
| Style, polices, icônes | Téléchargés en même temps que la région |
| Estimation du volume | Nombre de tuiles × taille moyenne mesurée |

### 2.1 Droits de téléchargement et de conservation (vérifiés le 13/10/2026)

Source principale : FAQ officielle IGN « Conférence Plan IGN Vecteur tuilé » (11/02/2021), complétée par les CGU actuelles de la Géoplateforme.

- **Téléchargement hors ligne dans une application mobile :** « Il est parfaitement possible de proposer le téléchargement ou la mise en cache de Plan IGN », avec IGN rando, Visorando et Iphigénie cités en exemple.
- **Vecteur tuilé :** « La mise en cache des tuiles sera autorisée ». En revanche, l'IGN ne fournit pas de paquet à télécharger (MBTiles). C'est donc l'application qui met en cache, à la demande, les tuiles d'une zone, ce que fait MapLibre.
- **Images tirées des tuiles vectorielles :** « la création de cache d'images sur les fonds cartographiques sous licence ouverte est tolérée ».
- **Gratuité :** Plan IGN, les orthophotos et l'ombrage sont sous licence ouverte, et l'usage des services en ligne est gratuit « sans limite de consommation ». Les CGU actuelles confirment que le WMTS public n'est « non soumis à limite d'usage ».
- **Obligations :**
  - mentionner la source (« © IGN ») et la date de mise à jour (Licence Etalab 2.0) ;
  - n'utiliser aucune clé partagée ;
  - utiliser l'API conformément à sa documentation, ce qui implique de rester raisonnable (pas de moissonnage massif).
- **Mise en œuvre retenue :** téléchargement **uniquement à la demande de l'utilisateur**, limité à une zone ou à la bande autour d'un parcours, avec un nombre de requêtes simultanées plafonné et une attente croissante en cas d'erreur HTTP 429. Les cartes téléchargées restent stockées tant que l'utilisateur ne les supprime pas, et pourront être rafraîchies.
- **Réserve :** la FAQ date de 2021. Aucune règle plus récente qui la contredise n'a été trouvée. Une confirmation écrite peut être demandée à contact.geoservices@ign.fr si l'application devait être diffusée publiquement.

### 2.2 Capacités réelles de MapLibre Android 13.6.1 (vérifiées dans le code source, tag `android-v13.6.1`)

| Capacité | Constat |
|---|---|
| Région rectangulaire | `OfflineTilePyramidRegionDefinition` présent |
| Région autour d'une géométrie (bande de 2 km) | `OfflineGeometryRegionDefinition` présent ; le calcul des tuiles nécessaires suit réellement la géométrie (`util::tileCount(reg.geometry, z)`) |
| Reprise après interruption | `OfflineRegion.setDownloadState(STATE_ACTIVE)` ; les ressources déjà téléchargées ne sont pas re-téléchargées |
| Intégrité | `OfflineRegionStatus` : `completedResourceCount`, `requiredResourceCount`, `isComplete`, `isRequiredResourceCountPrecise` |
| Gestion | `listOfflineRegions`, `delete`, `invalidate` (rafraîchissement), `packDatabase` (récupération d'espace), `setMaximumAmbientCacheSize` |
| Limite de 6 000 tuiles | Elle **ne s'applique qu'aux tuiles du fournisseur historique** (URL canoniques `isCanonicalURL`), **pas aux tuiles IGN** : aucun réglage n'est nécessaire |

**Points d'attention identifiés :**
- Le vectoriel étant redessiné sans perte au-delà du dernier niveau téléchargé, **télécharger jusqu'au zoom 16 suffit** pour afficher correctement jusqu'au zoom 18. Ordre de grandeur pour une zone de 10 × 10 km : environ 1 000 à 1 500 tuiles, soit **20 à 50 Mo** (à mesurer en Phase 3).
- L'ombrage du relief est en raster, donc plus lourd. Il sera optionnel dans le téléchargement.
- État « disponible / partiel / indisponible » avant le départ : l'application conserve dans Room la géométrie et l'état de chaque région, puis vérifie que le tampon du parcours est couvert.
- **Remplacement du fournisseur :** une interface `MapTileProvider` décrit les sources, et l'enregistrement GPS n'a aucune dépendance vers la carte. Il fonctionne même sans fond de carte.

**Verdict : faisable sans réserve avec Plan IGN.**

---

## 3. Calcul d'itinéraire

**Service retenu : Géoplateforme IGN, calcul d'itinéraire.** Testé avec succès :

```
GET https://data.geopf.fr/navigation/itineraire
    ?resource=bdtopo-osrm&profile=pedestrian
    &start=lon,lat&end=lon,lat&intermediates=lon,lat|lon,lat
    &geometryFormat=geojson
```

- Il utilise le graphe BD TOPO, qui comprend les chemins et sentiers. Le graphe testé date du 05/10/2026.
- Aucune clé n'est nécessaire. Limite : 5 requêtes par seconde et par IP, suffisant car un recalcul n'a lieu qu'à chaque modification d'un point.
- Le profil `pedestrian` ne distingue pas un sentier de montagne difficile d'un chemin facile. C'est une limite à signaler dans l'interface.

**Interface interchangeable `RoutingProvider`**, avec les implémentations suivantes :

1. `IgnRoutingProvider` (V1, en ligne).
2. `ManualRoutingProvider` (V1, hors ligne) : relie les points en ligne droite. Ces segments sont **affichés en pointillés et étiquetés « tracé direct, non vérifié »**, et ne sont jamais présentés comme un chemin.
3. Plus tard (V2) : BRouter ou GraphHopper sur données OSM, pour un routage hors ligne.

**Altimétrie prévisionnelle :** API Géoplateforme `altimetrie/1.0/calcul/alti/rest/elevationLine.json` (RGE ALTI, Etalab, 5 requêtes/s). Testée avec succès.

**Verdict : faisable.**

---

## 4. Autres choix techniques et justifications

| Sujet | Choix | Justification |
|---|---|---|
| Localisation | **Fused Location Provider** (Play Services) par défaut, avec repli automatique sur `LocationManager` GPS si les Play Services sont absents | Le cahier des charges demande Fused. Le repli garantit que l'application fonctionne aussi sans Google. Les deux implémentations passent par une interface `LocationSource`. |
| Dénivelé | Altitude GPS **et baromètre s'il est présent**, lissage puis cumul avec **seuil d'hystérésis** (≈ 3 m avec baromètre, ≈ 8 m en GPS seul, réglable) | Évite de surestimer le dénivelé en additionnant le bruit GPS. Le profil reste recalculable depuis les points bruts. |
| Correction par MNT après la randonnée | **Optionnelle et désactivée par défaut** | Elle enverrait la trace à l'IGN, ce qui va contre l'exigence 18 (« aucune transmission non nécessaire »). |
| Reprise après redémarrage | Au redémarrage, une notification « Randonnée interrompue — Reprendre » s'affiche ; un appui relance le service | Android 14 et suivants interdisent de démarrer un service de localisation depuis le démarrage du téléphone sans l'autorisation de localisation en arrière-plan. Cette solution évite de demander cette autorisation sensible. Le trou dans la trace est enregistré comme une interruption, sans ligne droite fictive. |
| Autorisations | `ACCESS_FINE_LOCATION`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS`, `INTERNET`, `VIBRATE`, `CAMERA` (photos des points d'intérêt). **Pas** d'`ACCESS_BACKGROUND_LOCATION`. | Limitées au besoin réel. Le service au premier plan suffit. |
| Rendu MapLibre | Variante **OpenGL ES** (`android-sdk-opengl`) | Depuis la version 13, Vulkan est le moteur par défaut, mais les pilotes Vulkan des téléphones Android 10–11 sont souvent instables. OpenGL est le choix le plus sûr pour Android 10 et plus. |
| Injection de dépendances | **Conteneur manuel** (`AppContainer`) | Moins de génération de code, compilation plus simple et plus rapide. Hilt reste possible plus tard. |
| Architecture | MVVM, Repository, Flow, avec une **séparation en modules Gradle** (voir § 6) | Les calculs GPS sont du Kotlin pur, testables sur JVM en quelques secondes. |
| Écriture en base | Insertion des points par lots (toutes les 10 s ou 20 points) dans une transaction Room, en mode WAL | Au maximum ≈ 10 s de données perdues en cas d'arrêt brutal, avec une usure limitée de la mémoire. |
| Batterie | Positions toutes les 1 à 2 s en mouvement, ralenties à l'arrêt ; mise à jour de la carte suspendue quand l'écran est éteint (seuls les calculs continuent) ; pas de `WAKE_LOCK` permanent | Exigence 17. Les « tueurs d'applications » des constructeurs (Xiaomi, Huawei, Samsung…) seront signalés à l'utilisateur, avec un lien vers le réglage d'optimisation de la batterie. |

---

## 5. Dépendances et versions retenues

Versions stables publiées sur Maven, vérifiées le 13/10/2026 :

| Composant | Version |
|---|---|
| Gradle | 9.8.1 |
| Android Gradle Plugin | 9.4.1 |
| Kotlin | 2.4.21 |
| KSP | 2.3.12 |
| Compose BOM (Material 3) | 2026.09.00 |
| activity-compose | 1.13.0 |
| lifecycle (viewmodel / runtime-compose) | 2.11.0 |
| navigation-compose | 2.10.2 |
| core-ktx | 1.19.1 |
| Room | 2.8.5 |
| DataStore Preferences | 1.2.1 |
| WorkManager | 2.12.0 |
| Coroutines | 1.11.0 |
| kotlinx-serialization-json | 1.11.0 |
| MapLibre Android (OpenGL) | 13.6.1 |
| play-services-location | 21.4.0 |
| OkHttp | 5.5.0 |
| Coil 3 (photos) | 3.6.3 |
| Tests | JUnit 4, kotlinx-coroutines-test, Turbine, Robolectric pour Room |

Cibles retenues après vérification :

- **`minSdk 29`** (Android 10) : version minimale d'exécution.
- **`compileSdk 37`** (plateforme Android 37.0, version stable et non bêta) : c'est **imposé par les bibliothèques actuelles**. Les métadonnées de `core 1.19.1` et `compose-ui 1.12.1` exigent `minCompileSdk=37`. Ce réglage ne concerne que la compilation et n'a aucun effet sur la compatibilité avec Android 10.
- **`targetSdk 36`** (Android 16) : comportements système bien éprouvés. On passera à 37 une fois les changements de comportement d'Android 17 évalués.
- **Chaîne de compilation :** AGP 9.4 accepte un `compileSdk` jusqu'à 37 et exige au minimum le JDK 17 et Gradle 9.6. Gradle 9.8.1 s'exécute avec Java 17 à 27. Le JBR 25 d'Android Studio est donc compatible.

**Environnement de compilation constaté sur ce PC :**

- Android Studio est installé (build 2026.1), avec son JDK intégré (JBR 25) : il sera utilisé pour Gradle.
- Le Java 26 du système est trop récent pour Gradle et ne sera pas utilisé.
- Seule la plateforme `android-35` est présente dans le SDK. **Il faut installer `platforms;android-37`** via `sdkmanager`, au démarrage de la Phase 1.
- Aucun téléphone n'est connecté en ADB actuellement.

---

## 6. Arborescence proposée

```
Mon_Gps/
├── PROJECT_STATE.md
├── docs/
├── settings.gradle.kts
├── gradle/libs.versions.toml            # catalogue de versions unique
├── core/
│   ├── model/        (Kotlin pur)       # TrackPoint, Session, Route, Poi, ActivityProfile…
│   ├── geo/          (Kotlin pur)       # haversine, cap, tampon, projection sur polyligne
│   ├── tracking-engine/ (Kotlin pur)    # filtre GPS, pauses auto, stats, vitesse lissée
│   ├── elevation/    (Kotlin pur)       # lissage, hystérésis D+/D-, profil
│   ├── navigation/   (Kotlin pur)       # guidage, distance au tracé, hors-parcours, retour
│   ├── statistics/   (Kotlin pur)       # agrégats, seuils personnels de couleur
│   └── gpx/          (Kotlin pur)       # lecture/écriture GPX 1.1, validation
├── data/
│   ├── storage/      (Android)          # Room : entités, DAO, migrations, archive de sauvegarde
│   ├── settings/     (Android)          # DataStore
│   ├── location/     (Android)          # LocationSource : Fused + LocationManager, baromètre, boussole
│   ├── routing/      (Android)          # RoutingProvider : IGN, Manuel ; client altimétrie
│   ├── offline/      (Android)          # OfflineManager MapLibre, régions, couverture
│   └── poi/          (Android)          # points d'intérêt + photos (stockage privé)
└── app/              (Android)
    └── fr.igntrail/
        ├── tracking/   # TrackingService (Foreground, type location), notification, reprise
        ├── map/        # MapView Compose, couches IGN, suivi/recentrage, tracé coloré
        ├── ui/
        │   ├── theme/      # Material 3, clair/sombre, panneaux semi-opaques
        │   ├── components/ # boutons flottants, panneau stats, graphique altimétrique
        │   ├── carte/      # onglet Carte + randonnée en cours + plein écran
        │   ├── creer/      # planificateur
        │   ├── parcours/   # historique, fiches, import GPX
        │   └── stats/      # tableau de bord
        └── AppContainer.kt
```

Les modules `core/*` n'ont **aucune dépendance Android**. Toute la logique des critères de test I, E et G y est testée en unitaire. Les dépendances vont dans un seul sens, `app → data → core`, ce qui empêche toute dépendance circulaire.

---

## 7. Récapitulatif des points bloquants et décisions à valider

| # | Point | Proposition |
|---|---|---|
| 1 | SCAN 25 non utilisable hors ligne par un particulier, et clé partagée non transférable | Plan IGN vectoriel « classique » + ombrage par défaut ; SCAN 25 en ligne uniquement, avec la clé personnelle de l'utilisateur |
| 2 | Pas de routage hors ligne en V1 | Mode manuel en pointillés, clairement signalé |
| 3 | Pas de GR/PR balisés dans Plan IGN | Reporté en V2 (couche OSM) |
| 4 | Fused Location dépend des Play Services | Repli automatique sur le GPS natif |
| 5 | Correction par MNT = envoi de la trace à l'IGN | Optionnelle, désactivée par défaut |
| 6 | SDK `android-37` absent | À installer au démarrage de la Phase 1 |

Sources principales : CGU Géoplateforme (cartes.gouv.fr/cgu), fiche SCAN 25 (cartes.gouv.fr), documentation du calcul d'itinéraire (cartes.gouv.fr/aide), métadonnées Maven Central et Google Maven.
