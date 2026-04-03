#!/usr/bin/env bash
# Rigenera app/src/main/res/drawable-nodpi/ic_launcher_logo.png da NCarousel_alpha.svg
# (PNG embedded in SVG). Richiede: python3, ImageMagick (magick).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SVG="$ROOT/branding/NCarousel_alpha.svg"
TMP="$ROOT/branding/.extracted.png"
OUT="$ROOT/app/src/main/res/drawable-nodpi/ic_launcher_logo.png"
python3 << PY
import re, base64, pathlib
p = pathlib.Path("$SVG")
t = p.read_text(encoding="utf-8", errors="replace")
m = re.search(r'href="data:image/png;base64,([^"]+)"', t)
assert m, "PNG base64 non trovato nello SVG"
b64 = m.group(1).replace("&#10;", "").replace("\n", "").replace(" ", "")
pathlib.Path("$TMP").write_bytes(base64.b64decode(b64))
PY
magick "$TMP" -trim +repage PNG32:"$TMP.trim.png"
magick xc:'#0082C9' -resize 432x432! \
  \( "$TMP.trim.png" -resize '400x400>' -gravity center -background none -extent 432x432 \) \
  -compose CopyOpacity -composite PNG32:"$OUT"
rm -f "$TMP" "$TMP.trim.png"
echo "OK -> $OUT"
