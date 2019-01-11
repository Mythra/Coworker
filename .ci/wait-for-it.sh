#!/usr/bin/env bash

port="$1"

echo -n "Waiting for Port: $port "

while ! nc -vz localhost "$port" ; do
    echo -n "."
    sleep 0.1
done

echo ""
echo "Done!"
