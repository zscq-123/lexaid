#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
db() {
  docker compose exec -T database sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --default-character-set=utf8mb4 --max-allowed-packet=256M -N -B -u"$MYSQL_USER" "$MYSQL_DATABASE" "$@"' sh "$@"
}
exec 9>.import.lock
flock -n 9 || { echo "An import is already running" >&2; exit 1; }
db -e 'CREATE TABLE IF NOT EXISTS lexaid_seed_import (file_name VARCHAR(180) PRIMARY KEY, sha256 CHAR(64) NOT NULL, applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP)'
while read -r digest name <&3; do
  [[ $digest =~ ^[0-9a-f]{64}$ && $name =~ ^[0-9][0-9]-[a-zA-Z0-9_.-]+\.sql(\.gz)?$ ]] || exit 2
  applied=$(db -e "SELECT sha256 FROM lexaid_seed_import WHERE file_name='$name'")
  if [[ $applied == "$digest" ]]; then continue; fi
  if [[ -n $applied ]]; then echo "Published migration has changed: $name" >&2; exit 1; fi
  if [[ ${1:-} == --wait-for-upload && ! -f init/$name ]]; then
    echo "Waiting for $name"
    deadline=$((SECONDS + 3600))
    until [[ -f init/$name ]]; do
      (( SECONDS < deadline )) || exit 1
      sleep 3
    done
  fi
  (cd init && printf '%s  %s\n' "$digest" "$name" | sha256sum -c -)
  if [[ $name == *.gz ]]; then gzip -dc "init/$name" | db; else db < "init/$name"; fi
  db -e "INSERT INTO lexaid_seed_import(file_name,sha256) VALUES('$name','$digest')"
done 3< init/SHA256SUMS
