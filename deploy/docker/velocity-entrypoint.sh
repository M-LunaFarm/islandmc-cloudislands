#!/bin/sh
set -eu

require_file() {
    if [ ! -s "$1" ]; then
        echo "Required secret file is missing or empty: $1" >&2
        exit 78
    fi
}

require_file /run/secrets/cloudislands_core_token
require_file /run/secrets/cloudislands_admin_token
require_file /run/secrets/cloudislands_forwarding_secret

VELOCITY_ONLINE_MODE="${CLOUDISLANDS_VELOCITY_ONLINE_MODE:-true}"
case "$VELOCITY_ONLINE_MODE" in
    true|false) ;;
    *) echo "CLOUDISLANDS_VELOCITY_ONLINE_MODE must be true or false." >&2; exit 78 ;;
esac

FORWARDING_SECRET="$(tr -d '\r\n' < /run/secrets/cloudislands_forwarding_secret)"
case "$FORWARDING_SECRET" in
    *[!A-Za-z0-9_-]*) echo "Forwarding secret must use URL-safe characters only." >&2; exit 78 ;;
esac
if [ "${#FORWARDING_SECRET}" -lt 32 ]; then
    echo "Forwarding secret must be at least 32 characters." >&2
    exit 78
fi

mkdir -p plugins/cloudislands/config-v2
cp /opt/cloudislands/CloudIslands-Velocity.jar plugins/CloudIslands-Velocity.jar
printf '%s\n' "$FORWARDING_SECRET" > forwarding.secret

cat > velocity.toml <<EOF
config-version = "2.7"
bind = "0.0.0.0:25565"
motd = "CloudIslands"
show-max-players = 500
online-mode = ${VELOCITY_ONLINE_MODE}
force-key-authentication = true
player-info-forwarding-mode = "modern"
forwarding-secret-file = "forwarding.secret"

[servers]
lobby = "lobby-paper:25565"
island-a = "island-paper-a:25565"
island-b = "island-paper-b:25565"
try = ["lobby"]

[forced-hosts]
EOF

cat > plugins/cloudislands/config-v2/core-api.yml <<EOF
enabled: true
base-url: ${CLOUDISLANDS_CORE_API_BASE_URL:-http://core-api:8443}
timeout:
  connect: 2s
  request: 3s
EOF

cat > plugins/cloudislands/config-v2/routing.yml <<'EOF'
default-lobby: lobby
island-pool: island
ticket:
  ttl: 30s
  wait-timeout: 20s
failure:
  fallback-server: lobby
  hide-backend-node-names: true
presence:
  publish-interval: 5s
  offline-timeout: 15s
EOF

cat > plugins/cloudislands/config-v2/security.yml <<'EOF'
core-api:
  auth-token: "${file:/run/secrets/cloudislands_core_token}"
  admin-token: "${file:/run/secrets/cloudislands_admin_token}"
forwarding:
  require-modern: true
  secret: "${file:/run/secrets/cloudislands_forwarding_secret}"
plugin-message:
  block-cloudislands-channel: true
EOF

cat > plugins/cloudislands/config-v2/health.yml <<'EOF'
enabled: true
bind-host: 0.0.0.0
port: 8788
EOF

exec java ${JAVA_OPTS:--Xms256m -Xmx1g} -jar /opt/cloudislands/server.jar
