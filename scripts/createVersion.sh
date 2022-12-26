#!/bin/bash

VERSION=1.0
COMMITS=$(git rev-list HEAD --count)
BUILDDATE=$(date +"%Y%m%d")

BUILD=src/main/java/com/jpage4500/organize/Build.java

sed -i '' "s/versionName = .*/versionName = \"$VERSION.$COMMITS\";/g" $BUILD
sed -i '' "s/versionCode = .*/versionCode = $COMMITS;/g" $BUILD
sed -i '' "s/buildDate = .*/buildDate = \"$BUILDDATE\";/g" $BUILD
