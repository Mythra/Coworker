#!/usr/bin/env bash

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

set -e

redis-cli -h "$REDIS_HOST" -n 0 -p "$REDIS_PORT" ping

echo "[+] Compiling base coworker"
(cd "$bench_dir/../" && mvn verify)
echo "[+] Done"

echo "[+] Compiling jesque-perf."
(cd "$bench_dir/jesque/" && mvn verify)
(cd "$bench_dir/jesque/target" && cp ./JesquePerf.jar ./JesquePerfLoad.jar)
echo "[+] Done"

echo "[+] Clearing older data."
redis-cli -h "$REDIS_HOST" -n 0 -p "$REDIS_PORT" FLUSHALL
echo "[+] Done."

echo "[+] Starting jesque-perf."
THREADS=10 java -jar "$bench_dir/jesque/target/JesquePerf.jar" &>/dev/null &
pid="$!"
echo "[+] Done."

echo "[+] Queueing 100k Jobs."
java -jar $bench_dir/jesque/target/JesquePerfLoad.jar queue-real-life &>/dev/null &
echo "[+] Kicked Off."

sleep 1

echo "epoch,job_count" > jesque-perf-timings.csv
start_time="$(date +%s)"
while true; do
  output="$(redis-cli -h "$REDIS_HOST" -n 0 --raw -p "$REDIS_PORT" GET resque:stat:processed)"
  if [[ "$output" -eq "100000" ]]; then
    kill "$pid"
    end_time="$(date +%s)"
    echo "$end_time,$output" >> jesque-perf-timings.csv
    echo "[+] Done. Times to burn through rest of queue: Start Time: [ $start_time ]. End Time: [ $end_time ]."
    exit 0
  fi
  echo "$(date +%s),$output" >> jesque-perf-timings.csv
  sleep 1
done
