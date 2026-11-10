#!/bin/bash
# Double-click me in Finder. Sets up this Mac and builds the signed .aab for Google Play.
cd "$(dirname "$0")"
bash tools/setup-release.sh
status=$?
echo
read -r -p "Press Return to close this window." _
exit $status
