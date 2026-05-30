#!/usr/bin/env bash
#
# Rasterize the hand-authored SVG diagrams in this folder to 2x PNG.
#
# The *.svg files are the source of truth: hand-authored in a dark theme to
# match the project's visual language. The *.png exports are what the Markdown
# docs embed (PNG renders everywhere; SVG is kept for crisp/vector use).
#
# Uses headless Google Chrome for faithful rasterization (gradients + filters).
# Re-run after editing any *.svg:
#
#   ./render.sh
#
set -euo pipefail
cd "$(dirname "$0")"

CHROME="${CHROME:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"
if [ ! -x "$CHROME" ]; then
  echo "Chrome not found at: $CHROME" >&2
  echo "Override with: CHROME=/path/to/chrome ./render.sh" >&2
  exit 1
fi

shopt -s nullglob
svgs=(*.svg)
if [ ${#svgs[@]} -eq 0 ]; then
  echo "No .svg sources found in $(pwd)" >&2
  exit 1
fi

for svg in "${svgs[@]}"; do
  png="${svg%.svg}.png"
  # Pull intrinsic size from the viewBox so the screenshot is exact (no padding).
  dims="$(sed -n 's/.*viewBox="0 0 \([0-9]*\) \([0-9]*\)".*/\1 \2/p' "$svg" | head -1)"
  w="${dims% *}"
  h="${dims#* }"
  if [ -z "$w" ] || [ -z "$h" ]; then
    echo "Could not read viewBox from $svg, skipping" >&2
    continue
  fi
  echo "Rendering $svg (${w}x${h}) -> $png"
  "$CHROME" --headless=new --disable-gpu --hide-scrollbars \
    --force-device-scale-factor=2 --window-size="${w},${h}" \
    --default-background-color=00000000 \
    --screenshot="$png" "file://$(pwd)/$svg" >/dev/null 2>&1
done

echo "Done: rasterized ${#svgs[@]} diagram(s)."
