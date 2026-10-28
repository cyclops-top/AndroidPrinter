#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/tools/ippsample"
mkdir -p "$ROOT/tools"
if [ ! -d "$SRC/.git" ]; then
  git clone --depth 1 https://github.com/istopwg/ippsample.git "$SRC"
fi
REV=ce11296293da30a111615a2deddaea51268b49ce
if ! git -C "$SRC" cat-file -e "$REV^{commit}" 2>/dev/null; then
  git -C "$SRC" fetch --depth 1 origin "$REV"
fi
git -C "$SRC" checkout "$REV"
git -C "$SRC" submodule update --init --depth 1 libcups
cd "$SRC"
# Debug configuration builds for the host architecture instead of requiring universal Homebrew libraries.
./configure --disable-shared --enable-debug --disable-dbus --prefix="$ROOT/tools/installed"
make -C libcups/cups -j4
make -C server -j4
