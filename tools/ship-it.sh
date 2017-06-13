#!/usr/bin/env bash

# Abort on errors
set -e

cd $(dirname $0)/..
declare GRADLEW=./gradlew
declare GRADLE_PROPERTIES=$HOME/.gradle/gradle.properties
declare CODEKVAST_VERSION=$(grep codekvastVersion gradle.properties | egrep --only-matching '[0-9.]+')
declare GIT_HASH=$(git rev-parse --short HEAD)

echo "Checking that we have Bintray credentials..."
if [ -n "$BINTRAY_USER" -a -n "$BINTRAY_KEY" ]; then
    echo "Environment variables BINTRAY_USER and BINTRAY_KEY are defined"
else
    if [ ! -e  ${GRADLE_PROPERTIES} ]; then
        echo "$GRADLE_PROPERTIES is missing and BINTRAY_USER and/or BINTRAY_KEY is undefined"
        exit 1
    fi

    egrep --quiet '^\s*bintrayUser\s*[:=]\s*\S+$' ${GRADLE_PROPERTIES} || {
        echo "bintrayUser=xxx is missing in $GRADLE_PROPERTIES"
        exit 1
    }

    egrep --quiet '^\s*bintrayKey\s*[:=]\s*\S+$' ${GRADLE_PROPERTIES} || {
        echo "bintrayKey=xxx is missing in $GRADLE_PROPERTIES"
        exit 1
    }
    echo "Found Bintray credentials in  $GRADLE_PROPERTIES"
fi

echo "Checking that Git workspace is clean..."
git status --porcelain --branch | egrep --quiet '^## master\.\.\.origin/master' || {
    echo "The Git workspace is not on the master branch. Git status:"
    git status --short --branch
    exit 2
}

if [ $(git status --porcelain | wc -l) -gt 0 ]; then
    echo "The Git workspace is not clean. Git status:"
    git status --short --branch
    exit 2
fi

echo "Checking that we are in sync with Git origin..."
git fetch --quiet
git status --porcelain --branch | egrep --quiet '^## master\.\.\.origin/master$' || {
    echo "The Git workspace is not synced with origin. Git status:"
    git status --short --branch
    exit 2
}

echo -n "Everything looks fine.
About to build and publish $CODEKVAST_VERSION-$GIT_HASH
Are you sure [N/y]? "
read answer
if [ "${answer}" != 'y' ]; then
    echo "Nothing done."
    exit 4
fi

tools/real-clean-workspace.sh
tools/build-it.sh --console=plain --no-daemon --no-build-cache --max-workers=1

echo "Creating Git tag ${CODEKVAST_VERSION}"
git tag -m "Version ${CODEKVAST_VERSION}" ${CODEKVAST_VERSION}
git push --tags

# Continue after errors
set +e

echo "Uploading distributions to Bintray..."
${GRADLEW} --console=plain :product:dist:bintrayUpload

echo "Uploading codekvast-agent-${CODEKVAST_VERSION}.jar to jcenter..."
${GRADLEW} --console=plain :product:java-agent:bintrayUpload
