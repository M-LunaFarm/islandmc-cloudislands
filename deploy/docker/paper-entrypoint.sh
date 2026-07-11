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

require_value() {
    value="$1"
    name="$2"
    if [ -z "$value" ]; then
        echo "Required environment variable is empty: $name" >&2
        exit 78
    fi
}

require_file /run/secrets/cloudislands_core_token
require_file /run/secrets/cloudislands_admin_token
require_file /run/secrets/cloudislands_forwarding_secret
require_file /run/secrets/cloudislands_storage_access_key
require_file /run/secrets/cloudislands_storage_secret_key

NODE_ID="${CLOUDISLANDS_NODE_ID:-}"
NODE_ROLE="${CLOUDISLANDS_NODE_ROLE:-}"
NODE_POOL="${CLOUDISLANDS_NODE_POOL:-island}"
VELOCITY_NAME="${CLOUDISLANDS_VELOCITY_SERVER_NAME:-}"
require_value "$NODE_ID" CLOUDISLANDS_NODE_ID
require_value "$NODE_ROLE" CLOUDISLANDS_NODE_ROLE
require_value "$VELOCITY_NAME" CLOUDISLANDS_VELOCITY_SERVER_NAME

case "$NODE_ID:$NODE_POOL:$VELOCITY_NAME" in
    *[!A-Za-z0-9_.:-]*)
        echo "Node identity values may contain only letters, digits, dot, underscore, colon, and dash." >&2
        exit 78
        ;;
esac
case "$NODE_ROLE" in
    LOBBY|ISLAND_NODE) ;;
    *) echo "CLOUDISLANDS_NODE_ROLE must be LOBBY or ISLAND_NODE." >&2; exit 78 ;;
esac

FORWARDING_SECRET="$(tr -d '\r\n' < /run/secrets/cloudislands_forwarding_secret)"
case "$FORWARDING_SECRET" in
    *[!A-Za-z0-9_-]*) echo "Forwarding secret must use URL-safe characters only." >&2; exit 78 ;;
esac
if [ "${#FORWARDING_SECRET}" -lt 32 ]; then
    echo "Forwarding secret must be at least 32 characters." >&2
    exit 78
fi

mkdir -p plugins/CloudIslands/config-v2 config
cp /opt/cloudislands/CloudIslands-Paper.jar plugins/CloudIslands-Paper.jar
mkdir -p cache versions
cp -R /opt/cloudislands/paper-runtime/cache/. cache/
cp -R /opt/cloudislands/paper-runtime/versions/. versions/
printf 'eula=true\n' > eula.txt

cat > server.properties <<EOF
server-port=25565
online-mode=false
enforce-secure-profile=false
enable-query=false
enable-rcon=false
spawn-protection=0
motd=CloudIslands ${NODE_ROLE}
EOF

cat > config/paper-global.yml <<EOF
_version: 31
proxies:
  velocity:
    enabled: true
    online-mode: true
    secret: '${FORWARDING_SECRET}'
EOF

cat > plugins/CloudIslands/config-v2/runtime.yml <<EOF
node:
  id: ${NODE_ID}
  role: ${NODE_ROLE}
  pool: ${NODE_POOL}
  velocity-server-name: ${VELOCITY_NAME}
  reject-default-identity: true
  supported-templates:
    - "*"
capacity:
  max-active-islands: ${CLOUDISLANDS_MAX_ACTIVE_ISLANDS:-600}
  soft-player-limit: ${CLOUDISLANDS_SOFT_PLAYER_LIMIT:-90}
  hard-player-limit: ${CLOUDISLANDS_HARD_PLAYER_LIMIT:-110}
  max-activation-queue: ${CLOUDISLANDS_MAX_ACTIVATION_QUEUE:-20}
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
  base-url: ${CLOUDISLANDS_CORE_API_BASE_URL:-http://core-api:8443}
  timeout:
    connect: 2s
    request: 3s
redis:
  enabled: false
  uri: ""
storage:
  type: S3
  endpoint: ${CLOUDISLANDS_STORAGE_ENDPOINT:-http://minio:9000}
  bucket: ${CLOUDISLANDS_STORAGE_BUCKET:-cloudislands}
  region: ${CLOUDISLANDS_STORAGE_REGION:-us-east-1}
routing:
  transport: CORE_TICKET
  direct-local-teleport: false
  local-fallback-world: world
EOF

cat > plugins/CloudIslands/config-v2/security.yml <<'EOF'
core-api:
  auth-token: "${file:/run/secrets/cloudislands_core_token}"
  admin-token: "${file:/run/secrets/cloudislands_admin_token}"
storage:
  access-key: "${file:/run/secrets/cloudislands_storage_access_key}"
  secret-key: "${file:/run/secrets/cloudislands_storage_secret_key}"
forwarding:
  required: true
  secret: "${file:/run/secrets/cloudislands_forwarding_secret}"
route-session:
  enforce: true
  required: true
trusted-proxies: []
proxy-source-allowlist:
  required: false
admin-command-dispatch:
  enabled: false
EOF

exec java ${JAVA_OPTS:--Xms512m -Xmx2g} -jar /opt/cloudislands/server.jar --nogui
