#!/usr/bin/env bash

if [[ "x$JDBC_URL" == "x" ]]; then
    (&>2 echo "[-] You must set JDBC_URL!")
    exit 1
fi

READLINK_BIN=readlink
if ! grep -q GNU <<< "$( readlink --version 2>&1 )"; then
    if [[ -n $( which greadlink 2>/dev/null ) ]]; then
        READLINK_BIN=greadlink
    else
        (&>2 echo "[-] Cannot find GNU readlink. On a Mac, try 'brew install coreutils'.")
        exit 2
    fi
fi

bench_dir=$( dirname "$( $READLINK_BIN -f "${BASH_SOURCE[0]}" )" )

pg_url="${POSTGRES_URL:-postgres://localhost:5432/coworker}"
psql "$pg_url" -c "SELECT 1;" >/dev/null
exit_code="$?"

if [[ "$exit_code" != "0" ]]; then
    (&>2 echo "[-] Failed to connect to: [ $pg_url ]!")
    exit 3
fi

set -e

echo "[+] Running Migrations."
psql "$pg_url" -a -f "$bench_dir/../src/main/resources/migrations/1_CreateDelayedWork_pg.sql"
psql "$pg_url" -a -f "$bench_dir/../src/main/resources/migrations/2_CreateJobCredit_pg.sql"
echo "[+] Ran Migrations."

echo "[+] Compiling base coworker"
(cd "$bench_dir/../" && mvn verify)
echo "[+] Done"

echo "[+] Compiling coworker-perf."
(cd "$bench_dir/coworker/" && mvn verify)
(cd "$bench_dir/coworker/target" && cp ./CoworkerPerf.jar ./CoworkerPerfLoad.jar)
echo "[+] Done"

echo "[+] Starting coworker-perf."
THREADS=10 java -jar "$bench_dir/coworker/target/CoworkerPerf.jar" &>/dev/null &
pid="$!"
echo "[+] Done."

echo "[+] Queueing 100k Jobs."
java -jar $bench_dir/coworker/target/CoworkerPerfLoad.jar queue-real-life &>/dev/null &
echo "[+] Kicked Off."

# Takes a while to spin up in a transaction a batch.
sleep 10

echo "epoch,job_count" > coworker-perf-timings.csv
start_time="$(date +%s)"
while true; do
  output="$(psql "$pg_url" -c "SELECT COUNT(*) FROM public.delayed_work;" -q -X --field-separator ' ' --pset footer=off --no-align --tuples-only)"
  if [[ "$output" == "0" ]]; then
    kill "$pid"
    end_time="$(date +%s)"
    echo "$end_time,$output" >> coworker-perf-timings.csv
    echo "[+] Done. Time to burn through rest of queue: Start Time: [ $start_time ]. End Time: [ $end_time ]."
    exit 0
  fi
  echo "$(date +%s),$output" >> coworker-perf-timings.csv
  sleep 1
done
