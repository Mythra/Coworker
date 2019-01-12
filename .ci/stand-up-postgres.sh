#!/usr/bin/env bash

COUNT=0
while (( $COUNT < 10 )); do
    PGPASSWORD="supasekrit" psql -h localhost -p 25565 -U postgres -c "CREATE DATABASE coworker;" || true
    COUNT=$(( $COUNT + 1 ))
    sleep 1
done

if (( $COUNT >= 10 )); then
    echo "Failed to create db in time!"
    exit 1
fi

OTHER_COUNT=0
while (( $OTHER_COUNT < 10 )); do
    PGPASSWORD="supasekrit" psql -h localhost -p 25565 -U postgres -d coworker -a -f ./src/main/resources/migrations/1_CreateDelayedWork_pg.sql || true
    OTHER_COUNT=$(( OTHER_COUNT + 1 ))
    sleep 1
done

if (( $OTHER_COUNT >= 10 )); then
    echo "Failed to run migration in time!"
    exit 2
fi

