# IGN Trail

Application Android de randonnée et de navigation GPS sur fond de carte IGN. Elle fonctionne sans compte, sans publicité et sans abonnement.

- Avancement et décisions : [PROJECT_STATE.md](PROJECT_STATE.md)
- Analyse technique : [docs/01_ANALYSE_FAISABILITE_ET_ARCHITECTURE.md](docs/01_ANALYSE_FAISABILITE_ET_ARCHITECTURE.md)
- Tests de la Phase 1 sur téléphone : [docs/02_TESTS_PHASE1.md](docs/02_TESTS_PHASE1.md)
- Tests de la Phase 2 sur téléphone : [docs/03_TESTS_PHASE2.md](docs/03_TESTS_PHASE2.md)
- Tests de la Phase 3 sur téléphone : [docs/04_TESTS_PHASE3.md](docs/04_TESTS_PHASE3.md)
- Tests de la Phase 4 sur téléphone : [docs/05_TESTS_PHASE4.md](docs/05_TESTS_PHASE4.md)
- Tests de la Phase 5 sur téléphone : [docs/06_TESTS_PHASE5.md](docs/06_TESTS_PHASE5.md)
- Tests de stabilisation et recette V1 (Phases 6-7) : [docs/07_TESTS_PHASE7.md](docs/07_TESTS_PHASE7.md)

## Compiler

Prérequis : Android Studio installé (il fournit le JDK 25 et le SDK Android). Les plateformes manquantes (SDK 37, Build-Tools 36) sont installées automatiquement à la première compilation.

### Avec Android Studio

Ouvrir le dossier, attendre la synchronisation Gradle, puis lancer **Run ▶** avec un téléphone branché.

### En ligne de commande (Git Bash)

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
```

Tests unitaires :

```bash
./gradlew test
```

APK optimisée (34 Mo, recommandée) :

```bash
./gradlew :app:assembleRelease
```

Elle est produite dans `app/build/outputs/apk/release/app-release.apk`, signée avec la clé de debug
(usage sur émulateur). **Pour le téléphone, toujours la signer ensuite avec la clé de production :**

```bash
scripts/signer-production.sh app/build/outputs/apk/release/app-release.apk apk/IGN-Trail-1.0.apk
```

Les clés sont dans `../IGN-Trail-cle-signature/` (hors du projet ; autre emplacement : variable
`IGNTRAIL_KEYS`). **Ce dossier doit être sauvegardé** (clé USB, cloud personnel) : sans lui, aucune
mise à jour ne pourra être installée par-dessus l'application, il faudrait la désinstaller.
Il contient la clé de production, l'ancienne clé de debug et la « lignée » de rotation qui permet au
téléphone d'accepter la nouvelle clé comme successeur de l'ancienne (aucune désinstallation nécessaire).

Une fois l'APK de production installée, une APK signée avec la seule clé de debug est refusée par le
téléphone (« conflit de signature ») : c'est normal.

APK de debug (98 Mo) :

```bash
./gradlew :app:assembleDebug
```

Le Java 26 installé sur le système n'est pas utilisé : la compilation passe par le JBR d'Android Studio.

## Organisation

```
core/model      Kotlin pur — modèles (LocationFix, GeoPoint…)
core/geo        Kotlin pur — distances, caps, échelle graphique (testé)
core/tracking   Kotlin pur — moteur d'enregistrement : filtre GPS, pauses, statistiques, dénivelé (testé)
core/gpx        Kotlin pur — lecture et écriture GPX (testé)
core/navigation Kotlin pur — guidage, sortie de parcours, retour au départ (testé)
core/planning   Kotlin pur — création de parcours, profil, durée estimée (testé)
data/location   Fused Location / GNSS natif, altitude niveau de la mer, boussole
data/settings   Préférences (DataStore)
data/storage    Base Room : sessions, mesures brutes, événements, itinéraires
app             Interface Compose, carte MapLibre, enregistrement, cartes hors ligne, guidage, création (calcul IGN)
```

Données cartographiques : © IGN – Géoplateforme, Licence Ouverte Etalab 2.0.

## Licence

Code sous licence [MIT](LICENSE).

Les données cartographiques ne sont pas couvertes par cette licence : cartes, ombrage, altitudes et
calcul d'itinéraire proviennent de la Géoplateforme IGN (© IGN – Licence Ouverte Etalab 2.0).
