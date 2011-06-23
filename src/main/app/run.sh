#! /bin/sh
set -e
#exec &> /var/log/stats_http_access.log
mkdir -p /var/log/stats_http_access
exec 1>>/var/log/stats_http_access/stdout.log
exec 2>>/var/log/stats_http_access/stderr.log

APP_HOME=$(dirname $0)
OUTPUT_DIR=$1
shift
java -Djava.ext.dirs=${APP_HOME}/lib -Dstats_http.data=${OUTPUT_DIR} com.mimesis.monitor.stats.StatsFromHttpAccess $@