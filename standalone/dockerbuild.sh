#!/bin/bash

SCRIPTDIR=`dirname $0`

pushd $SCRIPTDIR
docker build -t seanno/shutdownhook:vdj -f docker/Dockerfile .
docker push seanno/shutdownhook:vdj
popd



