#!/bin/bash

scriptdir=`dirname $0`

usage="Usage: ./postchain.sh [-j <extrajars>] [-i <nodeindex>] [-c <configfile>]"
description="if -i is omitted, 0 is assumed, if -c is ommitted config.<nodeindex>.properties is assumed"
example="Example: ./postchain.sh -j backend.jar:module.jar -i 2 -c mynode.properties"

POSITIONAL=()
while [[ $# -gt 0 ]]
do
key="$1"

case $key in
    -j)
    EXTRAJARS="$2"
    shift # past argument
    shift # past value
    ;;
    -i)
    NODEINDEX="$2"
    shift # past argument
    shift # past value
    ;;
    -c)
    CONFIGFILE="$2"
    shift # past argument
    shift # past value
    ;;
    -h)
    echo "$usage"
    echo "$description"
    echo "$example"
    exit 0
    ;;
    *)    # unknown option
    POSITIONAL+=("$1") # save it in an array for later
    shift # past argument
    ;;
esac
done
set -- "${POSITIONAL[@]}" # restore positional parameters

if [ -z $NODEINDEX ]; then
    NODEINDEX=0
fi

if [ -z $CONFIGFILE ]; then
    CONFIGFILE=config.$NODEINDEX.properties
fi

java -cp $scriptdir/${project.artifactId}-${project.version}-${executable-classifier}.jar:$EXTRAJARS ${main-class}  -i $NODEINDEX -c $CONFIGFILE $@