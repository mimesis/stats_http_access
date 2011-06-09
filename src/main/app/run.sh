#! /bin/sh
set -e
#exec &> /var/log/stats_http_access.log
exec 1>>/var/log/stats_http_access.stdout.log
exec 2>>/var/log/stats_http_access.stderr.log

OUTPUT_DIR=$1
shift
java -Djava.ext.dirs=lib -D=${OUTPUT_DIR} com.mimesis.monitor.stats.StatsFromHttpAccess $@