#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$ROOT/runtime/spool"
exec "$ROOT/tools/ippsample/server/ippserver" -a "$ROOT/config/printer.attrs" -p 8631 -n localhost -k -d "$ROOT/runtime/spool" -f application/pdf,image/pwg-raster,image/jpeg -2 -v 'Android Demo Printer'
