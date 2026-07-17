#!/bin/sh
set -eu

if [ "${EULA:-}" != "TRUE" ]; then
    echo "Set EULA=TRUE after accepting the Minecraft EULA." >&2
    exit 64
fi

require_file() {
    if [ ! -s "$1" ]; then
        echo "Required secret file is missing or empty: $1" >&2
        exit 78
    fi
}

require_file /run/secrets/cloudislands_core_token
require_file /run/secrets/cloudislands_admin_token

NODE_ID="${CLOUDISLANDS_NODE_ID:-single-paper-01}"
PAPER_ONLINE_MODE="${CLOUDISLANDS_PAPER_ONLINE_MODE:-true}"
case "$NODE_ID" in
    *[!A-Za-z0-9_.:-]*)
        echo "CLOUDISLANDS_NODE_ID may contain only letters, digits, dot, underscore, colon, and dash." >&2
        exit 78
        ;;
esac
case "$PAPER_ONLINE_MODE" in
    true|false) ;;
    *) echo "CLOUDISLANDS_PAPER_ONLINE_MODE must be true or false." >&2; exit 78 ;;
esac
if [ "$PAPER_ONLINE_MODE" != "true" ]; then
    echo "CloudIslands single-Paper is starting with online-mode=false; use this only in an isolated test environment." >&2
fi

mkdir -p plugins/CloudIslands/config-v2 config cache versions
cp /opt/cloudislands/CloudIslands-Paper.jar plugins/CloudIslands-Paper.jar
cp -R /opt/cloudislands/paper-runtime/cache/. cache/
cp -R /opt/cloudislands/paper-runtime/versions/. versions/
printf 'eula=true\n' > eula.txt

cat > server.properties <<EOF
server-port=25565
online-mode=${PAPER_ONLINE_MODE}
enforce-secure-profile=${PAPER_ONLINE_MODE}
enable-query=false
enable-rcon=false
spawn-protection=0
motd=CloudIslands Single Paper
EOF

cat > config/paper-global.yml <<EOF
_version: 31
proxies:
  velocity:
    enabled: false
    online-mode: ${PAPER_ONLINE_MODE}
    secret: ''
EOF

cat > plugins/CloudIslands/config-v2/runtime.yml <<EOF
node:
  id: ${NODE_ID}
  role: ISLAND_NODE
  pool: single-paper
  velocity-server-name: ${NODE_ID}
  reject-default-identity: false
  supported-templates:
    - "*"
capacity:
  max-active-islands: ${CLOUDISLANDS_MAX_ACTIVE_ISLANDS:-100}
  soft-player-limit: ${CLOUDISLANDS_SOFT_PLAYER_LIMIT:-80}
  hard-player-limit: ${CLOUDISLANDS_HARD_PLAYER_LIMIT:-100}
  max-activation-queue: ${CLOUDISLANDS_MAX_ACTIVATION_QUEUE:-10}
heartbeat:
  interval: 5s
  timeout: 20s
health:
  enabled: true
  bind-host: 0.0.0.0
  port: 8789
EOF

cat > plugins/CloudIslands/config-v2/integrations.yml <<EOF
core-api:
  enabled: true
  base-url: ${CLOUDISLANDS_CORE_API_BASE_URL:-http://core:8443}
  timeout:
    connect: 2s
    request: 3s
redis:
  enabled: false
  uri: ""
  database: 0
storage:
  type: LOCAL_FILESYSTEM
  local-path: islands-storage
routing:
  transport: CORE_TICKET
  direct-local-teleport: true
  local-fallback-world: world
EOF

cat > plugins/CloudIslands/config-v2/security.yml <<'EOF'
core-api:
  auth-token: "${file:/run/secrets/cloudislands_core_token}"
  admin-token: "${file:/run/secrets/cloudislands_admin_token}"
forwarding:
  required: false
  secret: ""
route-session:
  enforce: false
  required: false
trusted-proxies: []
proxy-source-allowlist:
  required: false
admin-command-dispatch:
  enabled: false
EOF

exec java ${JAVA_OPTS:--Xms512m -Xmx1g} -jar /opt/cloudislands/server.jar --nogui
