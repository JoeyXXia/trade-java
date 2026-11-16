#!/usr/bin/env bash
set -euo pipefail

# Runs only on a fresh MySQL data directory. No business seed or test data.
# Generated hex passwords make SQL interpolation unambiguous; reject arbitrary SQL input.
for variable in TRADE_MIGRATION_PASSWORD TRADE_APP_PASSWORD; do
  value="${!variable:-}"
  if [[ ! "$value" =~ ^[a-fA-F0-9]{64}$ ]]; then
    echo "$variable must be a generated 64-character hex password" >&2
    exit 1
  fi
done
if [[ ! "${MYSQL_DATABASE:-}" =~ ^[a-z][a-z0-9_]{0,63}$ ]]; then
  echo "MYSQL_DATABASE must be a simple lowercase database identifier" >&2
  exit 1
fi
MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=socket -uroot <<SQL
CREATE USER 'trade_migrator'@'%' IDENTIFIED BY '$TRADE_MIGRATION_PASSWORD';
CREATE USER 'trade_app'@'%' IDENTIFIED BY '$TRADE_APP_PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON \`$MYSQL_DATABASE\`.* TO 'trade_migrator'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON \`$MYSQL_DATABASE\`.* TO 'trade_app'@'%';
SQL
