#!/bin/sh
set -e
cd "$(dirname "$0")"
rm -rf dist && mkdir -p dist
V=$(date +%s)
npx esbuild src/app.js --bundle --format=esm --minify --target=es2020 --loader:.json=json --outfile=dist/app.js --log-level=warning
sed "s/__V__/$V/" src/index.html > dist/index.html
cp public/* dist/
echo "built $V"
