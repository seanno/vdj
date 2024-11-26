#!/bin/bash

SCRIPTDIR=`dirname $0`

# build client

pushd $SCRIPTDIR/../client
npm run build
popd

# build vdjlib

pushd $SCRIPTDIR/../vdjlib
mvn clean package install
popd

# build toolbox

if [ "$1" != "skip_toolbox" ] ; then
	
	pushd $SCRIPTDIR/../../shutdownhook/toolbox
	mvn clean package install
	popd
fi

# build standalone

pushd $SCRIPTDIR

BUILDFILE=src/main/resources/build.txt

date > $BUILDFILE
git branch --show-current --no-color >> $BUILDFILE
git rev-parse HEAD >> $BUILDFILE

mvn clean package

popd



