#!/bin/sh
# Uses certificates mounted at /etc/nginx/certs; generates a self-signed pair for
# first-run testing only. Replace with the unit's internal CA certificate in production.
set -e
mkdir -p /etc/nginx/certs
if [ ! -f /etc/nginx/certs/server.crt ]; then
  echo "No TLS certificate mounted — generating a temporary self-signed certificate."
  openssl req -x509 -nodes -newkey rsa:2048 -days 365 \
    -subj "/CN=${TLS_COMMON_NAME:-attendance.local}" \
    -keyout /etc/nginx/certs/server.key -out /etc/nginx/certs/server.crt >/dev/null 2>&1
fi
