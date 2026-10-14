#!/usr/bin/env bash
# Signe l'APK release avec la clé de production d'IGN Trail (rotation de clé APK v3).
#
# Les premières versions étaient signées avec la clé de debug. La « lignée » de rotation
# prouve au téléphone que la nouvelle clé succède à l'ancienne : l'APK de production
# s'installe donc comme une simple mise à jour, sans désinstaller (données conservées).
#
# Usage : scripts/signer-production.sh <apk release> <apk signée en sortie>
# Dossier des clés : $IGNTRAIL_KEYS, par défaut ../IGN-Trail-cle-signature (hors du projet).
set -euo pipefail

in="$1"
out="$2"
here="$(cd "$(dirname "$0")/.." && pwd)"
keys="${IGNTRAIL_KEYS:-$here/../IGN-Trail-cle-signature}"
props="$keys/signature.properties"
[ -f "$props" ] || { echo "Clés introuvables : $props" >&2; exit 1; }
prop() { grep "^$1=" "$props" | cut -d= -f2-; }

sdk="${ANDROID_HOME:-${LOCALAPPDATA:-$HOME}/Android/Sdk}"
apksigner="$(ls -d "$sdk"/build-tools/*/ | sort -V | tail -1)apksigner"
[ -f "$apksigner.bat" ] && apksigner="$apksigner.bat"

"$apksigner" sign \
  --ks "$keys/$(prop oldStoreFile)" --ks-key-alias "$(prop oldKeyAlias)" \
  --ks-pass "pass:$(prop oldStorePassword)" --key-pass "pass:$(prop oldStorePassword)" \
  --next-signer \
  --ks "$keys/$(prop storeFile)" --ks-key-alias "$(prop keyAlias)" --ks-pass "pass:$(prop storePassword)" \
  --lineage "$keys/$(prop lineageFile)" \
  --rotation-min-sdk-version 28 \
  --out "$out" "$in"

"$apksigner" verify --print-certs "$out" | grep -E "Signer|DN" | head -6
echo "APK de production : $out"
