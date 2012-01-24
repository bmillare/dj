#!/bin/bash

# normalize $0 on certain BSDs
if [ "$(dirname $0)" = "." ]; then
    SCRIPT="$(which $(basename $0))"
else
    SCRIPT="$0"
fi

# resolve symlinks to the script itself portably
while [ -h "$SCRIPT" ] ; do
    ls=`ls -ld "$SCRIPT"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
        SCRIPT="$link"
    else
        SCRIPT="`dirname "$SCRIPT"`/$link"
    fi
done

BIN_DIR="$(dirname "$SCRIPT")"
DJ_DIR="$(dirname "$BIN_DIR")"

# escape command-line arguments so they can be evaled as strings
ESCAPED_ARGS=""
for ARG in "$@"; do
  ESCAPED_ARGS="$ESCAPED_ARGS"' "'$(echo $ARG | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g')'"'
done

LD_LIBRARY_PATH=$LD_LIBRARY_PATH:$DJ_DIR/lib/jre/:$DJ_DIR/usr/lib/:$DJ_DIR/usr/lib64 exec java -server -Duser.dir=$DJ_DIR -cp "$DJ_DIR/lib/clojure.jar:$DJ_DIR/src" clojure.main -e "(use 'dj.cli) (main $ESCAPED_ARGS)"