#!/bin/bash
RUNAS="./bin/setup-directories.sh"
if [ $0 = $RUNAS ] ; then
    mkdir -p usr/src usr/bin etc sbin opt tmp/dj var
else
    echo "Run this only from the project directory"
    echo "like this:"
    echo " user:~$ $RUNAS"
fi
