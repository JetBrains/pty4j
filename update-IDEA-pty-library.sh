#!/bin/sh

set -e # Any command which returns non-zero exit code will cause this shell script to exit immediately
set -x # Activate debugging to show execution details: all commands will be printed before execution

IDEA_REPO="$1"
if [ ! -d "$IDEA_REPO" ]; then
  echo "No IDEA repo directory passed"
  exit 1
fi

IDEA_REPO="$(cd "$IDEA_REPO"; pwd)"

PTY4J="$(cd "`dirname "$0"`"; pwd)"

VERSION=`cat $PTY4J/VERSION`

rm "$IDEA_REPO/community/lib/pty4j-"*".jar"
cp "$PTY4J/build/pty4j-$VERSION.jar" "$IDEA_REPO/community/lib/"

rm "$IDEA_REPO/community/lib/src/pty4j-"*"-src.jar"
cp "$PTY4J/build/pty4j-$VERSION-src.jar" "$IDEA_REPO/community/lib/src/"

sed -i -E 's/(<root url=.*pty4j\-)[0-9][0-9\.]*[0-9]([^0-9]*$)/\1'$VERSION'\2/' "$IDEA_REPO/.idea/libraries/pty4j.xml"
# print affected lines to verify changes
grep '<root url=' "$IDEA_REPO/.idea/libraries/pty4j.xml"

sed -i -E 's/(<root url=.*pty4j\-)[0-9][0-9\.]*[0-9]([^0-9]*$)/\1'$VERSION'\2/' "$IDEA_REPO/community/.idea/libraries/pty4j.xml"
# print affected lines to verify changes
grep '<root url=' "$IDEA_REPO/community/.idea/libraries/pty4j.xml"

sed -i -E 's/(libraryName\: "pty4j", version\: ")[^"]*(")/\1'$VERSION'\2/' "$IDEA_REPO/community/platform/build-scripts/groovy/org/jetbrains/intellij/build/CommunityLibraryLicenses.groovy"
# print affected lines to verify changes
grep 'libraryName\: "pty4j", version\: "' "$IDEA_REPO/community/platform/build-scripts/groovy/org/jetbrains/intellij/build/CommunityLibraryLicenses.groovy"

echo "\nEverything looks fine, but please verify changes manually"
