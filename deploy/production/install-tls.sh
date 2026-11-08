#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
umask 077
source_dir=acme/config/live/lexaid-ip
install -d -m 700 secrets/tls
install -m 644 "$source_dir/fullchain.pem" secrets/tls/fullchain.pem.next
install -m 600 "$source_dir/privkey.pem" secrets/tls/privkey.pem.next
mv secrets/tls/fullchain.pem.next secrets/tls/fullchain.pem
mv secrets/tls/privkey.pem.next secrets/tls/privkey.pem
if [[ -n $(docker compose ps --status running -q web) ]]; then
  docker compose exec -T web nginx -t
  docker compose exec -T web nginx -s reload
fi
