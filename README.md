# CloudIslands

[한국어](#한국어) | [English](#english)

## 한국어

CloudIslands는 Paper와 Velocity에서 섬을 운영하기 위한 Skyblock 플랫폼입니다.
섬을 특정 Minecraft 서버에 묶어 두지 않고, 필요할 때 사용 가능한 Paper 노드에
불러옵니다. 섬 정보는 Core가 관리하고, Paper는 실제 월드를 실행하며, Velocity는
플레이어를 올바른 서버로 보냅니다.

**현재 버전:** `1.1.263`

서버 규모에 따라 두 가지 구성을 쓸 수 있습니다.

- **Paper 한 대:** 공개 Paper 서버 한 대와 Core, PostgreSQL, Redis로 구성합니다.
  Velocity나 별도 로비 서버 없이 바로 운영할 수 있습니다.
- **분산 네트워크:** Velocity, 로비, 여러 Paper 노드, Core 이중화, PostgreSQL,
  Redis, S3 호환 스토리지를 함께 사용합니다.

처음 확인할 때는 Docker Compose 구성을 권장합니다. 이미 운영 중인 인프라가 있다면
설정 팩이나 Helm 차트만 가져다 쓸 수 있습니다.

### 목차

- [CloudIslands를 쓰는 이유](#cloudislands를-쓰는-이유)
- [실행 환경](#실행-환경)
- [지원하는 Minecraft 버전](#지원하는-minecraft-버전)
- [배포 구성 고르기](#배포-구성-고르기)
- [Paper 한 대로 시작하기](#paper-한-대로-시작하기)
- [분산 구성으로 시작하기](#분산-구성으로-시작하기)
- [설정](#설정)
- [설치 후 확인](#설치-후-확인)
- [명령어와 권한](#명령어와-권한)
- [구조와 데이터 저장 위치](#구조와-데이터-저장-위치)
- [장애와 복구](#장애와-복구)
- [보안](#보안)
- [백업과 복원](#백업과-복원)
- [SuperiorSkyblock2에서 이전하기](#superiorskyblock2에서-이전하기)
- [연동과 애드온](#연동과-애드온)
- [빌드와 릴리스](#빌드와-릴리스)
- [문제 해결](#문제-해결)
- [이번 릴리스](#이번-릴리스)

### CloudIslands를 쓰는 이유

일반적인 Skyblock 플러그인은 섬 월드와 서버가 강하게 묶입니다. CloudIslands는
섬의 소유권과 현재 월드를 실행하는 서버를 분리합니다.

- 준비된 Paper 노드라면 어느 곳에서든 섬을 열 수 있습니다.
- 섬 데이터는 매니페스트와 SHA-256 체크섬을 포함한 묶음으로 저장합니다.
- fencing token으로 오래된 노드가 뒤늦게 저장 결과를 덮어쓰는 일을 막습니다.
- 이동 티켓은 플레이어, 출발 노드, nonce, 만료 시간에 묶여 있습니다.
- 섬, 멤버, 권한, 경제, 미션, 랭킹, 스냅샷, 작업, 감사 기록은 SQL에 저장합니다.
- Redis는 큐, 이벤트, 잠금, 캐시를 빠르게 처리하는 용도이며 원본 데이터 저장소가
  아닙니다.
- Paper의 블록 보호 판정은 로컬 인덱스를 사용합니다. 블록 이벤트를 처리하면서
  HTTP, SQL, Redis를 기다리지 않습니다.
- 스토리지 저장에 실패해도 이미 열린 섬은 계속 플레이할 수 있고 저장은 재시도됩니다.
- Paper 한 대 구성에서도 같은 섬 수명 주기와 이동 티켓을 사용하되, 서버 이동 대신
  로컬 텔레포트를 수행합니다.

여기서 이동 가능한 섬 데이터란 CloudIslands가 관리하는 월드와 섬 상태를 뜻합니다.
CoreProtect 기록, WorldEdit 실행 취소 기록, 다른 플러그인이 자체 DB에 저장한 값까지
섬 파일에 몰래 포함하지는 않습니다.

### 실행 환경

| 항목 | 기준 |
|---|---|
| Paper | `1.21.x` 또는 안정 버전 `26.1.x` |
| Java | Paper `1.21.x`는 Java 21, Paper `26.1.x`와 `26.2.x`는 Java 25 |
| Velocity | 분산 구성은 `3.5.0-SNAPSHOT` 컴파일 기준 |
| 데이터베이스 | PostgreSQL 16 권장, MySQL과 MariaDB도 지원 |
| Redis | Core 큐, 이벤트, 잠금, 캐시 용도로 Redis 7 권장 |
| 섬 스토리지 | 여러 서버면 S3 호환 스토리지, 한 호스트면 로컬 파일 시스템 사용 가능 |
| 빌드 | 저장소에 포함된 Gradle Wrapper 9.1 사용 |

필요한 CPU와 메모리는 섬 수, 시야 거리, 추가 플러그인, 자동화 장치 규모에 따라 크게
달라집니다. 예제의 메모리 제한은 개발용 시작값입니다. 운영 서버에서는 MSPT와 힙
사용량을 보고 Core와 Paper를 따로 조정해야 합니다.

외부에 서버를 열기 전에 다음 항목은 반드시 확인하십시오.

- DB, Redis, Paper 데이터는 영구 볼륨이나 외부 관리형 서비스에 둡니다.
- Core, DB, Redis, 오브젝트 스토리지, 백엔드 Paper 포트는 외부에 공개하지 않습니다.
- Paper 노드 ID와 Velocity 서버 이름은 중복되지 않아야 합니다.
- SQL과 섬 스토리지는 같은 시점에 백업합니다.
- 실제로 사용할 권한, 경제, 커스텀 블록, 스태커 플러그인 조합으로 테스트합니다.

### 지원하는 Minecraft 버전

| 대상 | 컴파일 검사 | 부팅 검사 | 지원 상태 |
|---|---|---|---|
| Paper `1.21.x` | `paper121Compile` | `paper121BootSmoke` | 정식 지원 |
| Paper `26.1.x` | `paper261Compile` | `paper261BootSmoke` | 정식 지원, 현재 기준 `26.1.2` |
| Paper `26.2.x` | `paper262Compile` | `paper262BootSmoke` | 실험적 지원, beta build 60 기준 |

한 번 컴파일됐다는 이유만으로 지원 버전으로 표시하지 않습니다. 정식 지원에는 컴파일,
실제 부팅, 플러그인 패키징, 릴리스 검증 결과가 모두 필요합니다. 버전 기준 파일은
`gradle/minecraft-versions.toml`입니다.

버전 정보를 바꿨다면 다음 검사도 함께 실행하십시오.

```bash
./gradlew verifyReadmeVersionTable verifyMinecraftVersionMatrix
```

### 배포 구성 고르기

| 구성 | 이런 경우에 사용 | 접속 포트 | 섬 스토리지 |
|---|---|---|---|
| Paper 한 대 | Minecraft 서버 한 대면 충분할 때 | Paper `25565` | 영구 로컬 경로 또는 S3 |
| 분산 Compose | 로비 분리, Core 이중화, 여러 섬 노드가 필요할 때 | Velocity `25565` | S3 또는 MinIO |
| Helm | Kubernetes와 외부 영구 스토리지를 이미 운영할 때 | Velocity Service | 공유 오브젝트 스토리지 |
| 설정 팩 | DB와 프로세스 관리 환경을 직접 갖추고 있을 때 | Paper 또는 Velocity | 로컬 또는 S3 |

예제는 `deploy/examples`, 전체 분산 Compose 구성은 `deploy/compose`, Helm 차트는
`deploy/helm/cloudislands`에 있습니다.

### Paper 한 대로 시작하기

이 구성은 PostgreSQL, 비공개 Redis, Core, 공개 Paper 서버 한 대를 실행합니다.
Paper가 섬 생성, 보호, 명령어, GUI, 저장, 복원, 로컬 이동을 모두 처리합니다.
Redis는 Core에서만 사용하고 Paper 플러그인에서는 끕니다.

#### 1. 설정과 비밀값 준비

```bash
cd deploy/examples/single-paper
cp .env.example .env
mkdir -p secrets
umask 077
openssl rand -hex 32 > secrets/database-password
openssl rand -hex 32 > secrets/core-token
openssl rand -hex 32 > secrets/admin-token
mkdir -p /srv/cloudislands/islands-storage
```

`.env`를 열어 `CLOUDISLANDS_STORAGE_PATH`를 위에서 만든 절대 경로로 지정합니다.
아래 값은 운영 환경에서도 그대로 두는 편이 좋습니다.

```dotenv
CLOUDISLANDS_PAPER_ONLINE_MODE=true
CLOUDISLANDS_PAPER_VERSION=26.1.2
MINECRAFT_EULA=TRUE
```

`MINECRAFT_EULA=TRUE`는 Minecraft EULA에 동의한다는 뜻입니다.

#### 2. 실행

```bash
docker compose up -d --build --wait
docker compose ps
```

포트를 바꾸지 않았다면 `localhost:25565`로 접속합니다.

#### 3. 상태 확인

```bash
curl --fail http://127.0.0.1:8443/ready
docker compose exec paper curl --fail --silent http://127.0.0.1:8789/health
docker compose logs --tail=200 core paper
```

게임 안에서는 다음 순서로 확인합니다.

```text
/is create default
/is home
/ciadmin setup verify
/ciadmin doctor
```

섬을 만든 뒤 나갔다가 다시 접속해 `/is home`이 정상적으로 작동하는지 확인하십시오.
마지막으로 있던 섬이 아직 열리지 않은 상태에서 접속해도 기본 월드의 같은 좌표로 잘못
보내지 않고, 설정한 대기 월드의 스폰으로 돌려보냅니다.

#### 4. 데이터 유지한 채 종료

```bash
docker compose down
```

PostgreSQL, Redis, Paper 볼륨까지 지울 생각이 아니라면 `-v`를 붙이지 마십시오.
`CLOUDISLANDS_STORAGE_PATH`의 섬 데이터는 Compose 볼륨 밖에 있으므로 별도로 백업해야
합니다.

### 분산 구성으로 시작하기

분산 Compose는 PostgreSQL, 비밀번호가 설정된 Redis, MinIO, Core 두 대와 HAProxy,
Velocity, 로비 Paper 한 대, 섬 Paper 두 대를 실행합니다. 외부에는 Velocity만
공개됩니다. Core 관리 포트는 로컬 호스트에서만 접근할 수 있고 나머지 백엔드는 호스트
포트를 열지 않습니다.

#### 1. 비밀값 만들기

저장소 최상위에서 실행합니다.

```bash
mkdir -p /srv/cloudislands/secrets
umask 077
openssl rand -hex 32 > /srv/cloudislands/secrets/database-password
openssl rand -hex 32 > /srv/cloudislands/secrets/redis-password
openssl rand -hex 20 > /srv/cloudislands/secrets/storage-access-key
openssl rand -hex 32 > /srv/cloudislands/secrets/storage-secret-key
openssl rand -hex 32 > /srv/cloudislands/secrets/core-token
openssl rand -hex 32 > /srv/cloudislands/secrets/admin-token
openssl rand -base64 48 | tr -d '\n' > /srv/cloudislands/secrets/forwarding-secret
```

Compose가 파일을 찾을 수 있도록 경로를 내보냅니다.

```bash
export CLOUDISLANDS_DATABASE_PASSWORD_FILE=/srv/cloudislands/secrets/database-password
export CLOUDISLANDS_REDIS_PASSWORD_FILE=/srv/cloudislands/secrets/redis-password
export CLOUDISLANDS_STORAGE_ACCESS_KEY_FILE=/srv/cloudislands/secrets/storage-access-key
export CLOUDISLANDS_STORAGE_SECRET_KEY_FILE=/srv/cloudislands/secrets/storage-secret-key
export CLOUDISLANDS_CORE_TOKEN_FILE=/srv/cloudislands/secrets/core-token
export CLOUDISLANDS_ADMIN_TOKEN_FILE=/srv/cloudislands/secrets/admin-token
export CLOUDISLANDS_FORWARDING_SECRET_FILE=/srv/cloudislands/secrets/forwarding-secret
export MINECRAFT_EULA=TRUE
```

forwarding secret은 Velocity와 모든 백엔드 Paper가 같은 값을 써야 합니다. Core token이나
admin token과 같은 값을 재사용하지 마십시오.

#### 2. 실행

```bash
docker compose -f deploy/compose/docker-compose.yml up -d --build --wait
docker compose -f deploy/compose/docker-compose.yml ps
```

기본 접속 주소는 `localhost:25565`입니다.

#### 3. 라우팅 확인

```bash
curl --fail http://127.0.0.1:8443/live
curl --fail http://127.0.0.1:8443/ready
docker compose -f deploy/compose/docker-compose.yml logs --tail=200 core-1 core-2 velocity lobby-paper island-paper-a island-paper-b
```

`/ready`에서 DB, Redis, 오브젝트 스토리지, 큐, Paper 노드 heartbeat가 모두 준비된
상태여야 합니다. 기본 구성이라면 섬을 열 수 있는 노드가 두 대 보여야 합니다.

게임 안에서는 실제 이동까지 확인합니다.

```text
/is create default
/is home
/is visit <플레이어-또는-섬>
/ciadmin node list
/ciadmin doctor
```

#### 4. Paper 노드 추가

새 Paper 노드는 다음 조건을 지켜야 합니다.

- 다른 노드와 겹치지 않는 `node.id`
- Velocity 설정에 등록한 이름과 같은 서버 이름
- 같은 섬을 처리할 노드끼리는 같은 island pool
- 같은 Core 주소, 스토리지 bucket, forwarding secret
- 노드마다 분리된 쓰기 가능한 Paper 데이터 디렉터리

실행 중인 Paper 데이터 디렉터리를 복사해서 다른 노드로 쓰면 안 됩니다. 섬 소유권은
Core와 스토리지에서 결정하며, 서버 폴더 복제로 이전하지 않습니다.

### 설정

CloudIslands는 Config v2 YAML을 사용합니다. 첫 실행 때 플러그인 데이터 디렉터리에
기본 파일이 생성됩니다.

일반 사용자는 Paper와 Velocity의 `config-v2/config.yml`에서
`configuration-mode: BASIC`만 사용하면 됩니다. Paper의 `basic.topology`는
`SINGLE_PAPER`, `NETWORK_ISLAND`, `LOBBY` 중 하나이며, `LOBBY`는 섬 월드를 열지
않으면서 CloudIslands API, `/is`, 애드온 명령과 tab completion을 제공합니다.
세부 파일을 직접 조정하려면 `configuration-mode: ADVANCED`로 바꾸십시오.

Core는 `CI_CONFIGURATION_MODE=BASIC` 또는 `-Dcloudislands.mode=BASIC`으로 실행하면 내장 MySQL 프로필을 사용합니다.
기본값은 `127.0.0.1:3306/cloudislands`, 자동 스키마 생성이며
`CI_DB_USERNAME`, `CI_DB_PASSWORD`, `CI_CORE_TOKEN`으로 값을 덮어쓸 수 있습니다.
고급 사용자는 `CI_CONFIG_FILE` 또는 `-Dcloudislands.config=...`로 단일 Core YAML을
지정할 수 있습니다.

#### Core Service 단독 JAR 실행

Release에서 `CloudIslands-Core-1.1.263.jar`를 내려받아 Velocity의 `plugins` 폴더가 아닌
별도 Core 폴더에 둡니다. BASIC 모드는 로컬 MySQL과 로컬 섬 스토리지를 사용합니다.

Windows CMD 예시:

```bat
set CI_CONFIGURATION_MODE=BASIC
set CI_DB_USERNAME=cloudislands
set CI_DB_PASSWORD=데이터베이스비밀번호
set CI_CORE_TOKEN=generate-cloudislands-keys.cmd로_만든_core_token
java -Xms256m -Xmx1g -jar CloudIslands-Core-1.1.263.jar
```

Linux 예시:

```bash
export CI_CONFIGURATION_MODE=BASIC
export CI_DB_USERNAME=cloudislands
export CI_DB_PASSWORD='데이터베이스비밀번호'
export CI_CORE_TOKEN='충분히-긴-무작위-core-token'
java -Xms256m -Xmx1g -jar CloudIslands-Core-1.1.263.jar
```

고급 단일 YAML을 쓰려면 `CI_CONFIG_FILE=cloudislands.yml` 또는
`-Dcloudislands.config=cloudislands.yml`을 지정합니다. Velocity의 `core-api.base-url`과
Paper의 `core-api.base-url`은 이 프로세스의 주소(기본 `http://127.0.0.1:8443`)를 가리켜야
합니다. Velocity 종료와 Core 종료가 서로 묶이지 않으므로 Core 장애 감지와 복구가
독립적으로 동작합니다.

Velocity forwarding secret은 설정이 비어 있으면 `velocity.toml`의
`forwarding-secret-file`을 읽습니다. 파일도 없으면 Java `SecureRandom`으로 32바이트
hex 값을 생성하므로 Windows와 Linux 모두 `openssl rand`가 필요 없습니다. Paper는
`config/paper-global.yml`의 `proxies.velocity.secret`을 자동 인식합니다.

| 설정 팩 | 용도 |
|---|---|
| `deploy/examples/basic-mysql/config-pack.yml` | BASIC 모드와 로컬 MySQL 빠른 시작 |
| `deploy/examples/single-paper/config-pack.yml` | Paper 한 대와 로컬 라우팅 |
| `deploy/examples/single-node/config-pack.yml` | 분산 네트워크의 섬 노드 한 대 |
| `deploy/examples/two-island-nodes/config-pack.yml` | 섬 노드 두 대와 용량 분배 예제 |
| `deploy/examples/production-ha/config-pack.yml` | 이중화를 고려한 운영 기준 |
| `deploy/examples/migration-lab/config-pack.yml` | SuperiorSkyblock2 이전 연습 환경 |

Paper 설정은 역할별로 나뉩니다.

- `runtime.yml`: 노드 ID, 역할, pool, 용량, heartbeat, 상태 확인
- `integrations.yml`: Core, Redis, 스토리지, 라우팅 방식, 외부 연동
- `security.yml`: token, Velocity forwarding, route session, proxy 경계
- `features.yml`: GUI와 기능 켜기/끄기
- `gameplay.yml`: 생성기, 보호, 제한, 게임 규칙
- `ui/`: 메시지, 테마, 메뉴

섬 월드를 여는 서버는 `ISLAND_NODE`, 명령어와 GUI만 제공하는 분산 로비는 `LOBBY`를
사용합니다.

Paper 한 대 구성에서 중요한 값은 다음과 같습니다.

```yaml
redis:
  enabled: false
routing:
  direct-local-teleport: true
  local-fallback-world: world
forwarding:
  required: false
route-session:
  enforce: false
  required: false
```

분산 구성에서는 local routing을 끄고 Velocity modern forwarding, route session,
proxy source 검사를 켜야 합니다.

운영 모드의 Core는 메모리 저장소를 원본으로 사용할 수 없습니다. PostgreSQL, MySQL,
MariaDB 중 하나를 사용하고 JDBC fallback은 끄십시오. Core가 여러 대라면 한 대만 자동
스키마 생성을 켜고, 스키마가 준비된 뒤 나머지 Core를 시작하는 구성이 안전합니다.
제공된 분산 Compose가 이 방식으로 설정되어 있습니다.

비밀번호와 token은 Docker/Kubernetes Secret이나 별도 비밀 관리 도구로 전달하십시오.
`.env`, access key, forwarding secret, 실제 값이 채워진 런타임 설정을 저장소에 올리면
안 됩니다.

Helm 차트는 `deploy/helm/cloudislands`에 있습니다. 모든 이미지 태그를 고정하고,
영구 StorageClass와 기존 Secret을 연결하십시오. 운영 환경에서는 Core를 두 개 이상
두고 NetworkPolicy, TLS, 백업, PodDisruptionBudget도 직접 준비해야 합니다.

### 설치 후 확인

프로세스가 켜졌다는 사실만으로 설치가 끝난 것은 아닙니다. 실제 플레이와 복원 경로까지
확인하십시오.

1. Core의 `/live`, `/ready`가 모두 `UP`인지 확인합니다.
2. `/ciadmin node list`에 예상한 Paper 노드가 모두 보이는지 확인합니다.
3. `/ciadmin setup verify`와 `/ciadmin doctor`를 실행합니다.
4. 섬을 만들고 생성된 스폰 위치로 이동하는지 확인합니다.
5. 접속을 끊었다가 다시 들어와 `/is home`을 실행합니다.
6. 스냅샷을 만든 뒤 섬을 비활성화하고 복원합니다.
7. Paper를 재시작하고 노드가 `STARTING`에서 `READY`로 돌아오는지 봅니다.
8. 스토리지를 잠시 끊어 열린 섬은 계속 플레이되고 저장은 재시도되는지 확인합니다.
9. Core, Paper, Velocity 상태 페이지와 로그에서 재시도나 stale node 오류를 확인합니다.
10. 플레이어를 받기 전에 운영 데이터가 아닌 섬 하나로 백업과 복원을 연습합니다.

### 명령어와 권한

플레이어가 주로 사용하는 명령어입니다.

- `/is`, `/island`, `/섬`: 섬 메뉴와 기본 명령어
- `/is help`: 현재 켜진 기능에 맞춘 도움말
- `/is create [template]`, `/is home`, `/is visit`, `/is warp`: 섬 생성과 이동
- `/is members`, `/is invite`, `/is trust`, `/is permissions`: 멤버와 권한
- `/is bank`, `/is warehouse`, `/is upgrades`, `/is missions`: 성장 기능
- `/is settings`, `/is fly`, `/is biome`, `/is border`: 섬 환경 설정
- `/is snapshot`, `/is restore`: 허용된 범위의 사용자 복구 기능

플레이어 권한은 `cloudislands.island.*` 아래에 있습니다. 기본 권한
`cloudislands.player`는 플레이어에게 주어지며, 서버의 권한 플러그인에서 변경 명령을
더 제한할 수 있습니다.

운영자 명령어는 다음과 같습니다.

- `/ciadmin status`: 서비스와 노드 상태 요약
- `/ciadmin setup verify`: 배포 설정 연결 상태 확인
- `/ciadmin doctor`: 장애 원인과 복구 방향 확인
- `/ciadmin node ...`: 노드 조회, drain, 복귀, 이동, 안전 종료
- `/ciadmin island ...`: 섬 조회, 활성화, 저장, 복원, 수리, 격리, 이전, 삭제
- `/ciadmin jobs`, `/ciadmin route`, `/ciadmin storage`: 작업과 이동, 스토리지 진단
- `/ciadmin audit`, `/ciadmin metrics`, `/ciadmin support-bundle`: 감사와 운영 자료 수집
- `/ciadmin integrations report`: 선택 연동 플러그인 상태
- `/ciadmin migrate-superiorskyblock2 ...`: SuperiorSkyblock2 이전

Paper 권한은 `cloudislands.admin.*`를 사용합니다. Core는 별도로 admin token의 서버 측
권한을 검사하므로 Bukkit 권한만 준다고 Core 관리 기능을 우회할 수는 없습니다.

### 구조와 데이터 저장 위치

```text
플레이어
   |
   +--> Paper 한 대 -------------------------+
   |         |                               |
   |         +-- 이동 티켓을 로컬에서 처리   |
   |                                         v
   +--> Velocity --> 로비 / 섬 Paper ------> Core API
                                             |   |   |
                                             |   |   +--> Redis
                                             |   +------> SQL
                                             +----------> 섬 스토리지
```

| 모듈 | 역할 |
|---|---|
| `cloudislands-api` | 공개 애드온 API, 이벤트, 서비스 계약 |
| `cloudislands-common` | 보안, 라우팅, 설정, 실패 처리, 캐시 공통 코드 |
| `cloudislands-protocol` | 통신 DTO와 호환성 계약 |
| `cloudislands-core-client` | 비동기 Core 클라이언트 |
| `cloudislands-core-service` | DB 원본 상태, API, 작업, 감사, 노드 할당 |
| `cloudislands-paper` | 명령어, GUI, 보호, 섬 열기, 저장, 복원, 텔레포트 |
| `cloudislands-velocity` | 이동 준비, session, 프록시 전송 |
| `cloudislands-storage` | 섬 묶음, 매니페스트, 체크섬, 스냅샷, 보관 정책 |
| `cloudislands-migration` | SuperiorSkyblock2 가져오기와 검증 |
| `cloudislands-satis` | 선택 설치하는 공식 공장·성장 기능 팩 |
| `cloudislands-testkit` | 애드온과 연동 테스트 도구 |
| `cloudislands-bom` | 애드온 의존성 버전 정렬 |

섬을 열 때는 Core가 권한과 상태를 확인하고, 여유 있는 노드를 고른 뒤 fencing token이
붙은 작업을 보냅니다. Paper가 섬을 만들거나 복원하고 준비가 끝났다고 보고하면 Core가
현재 실행 위치를 확정합니다. 그다음 Velocity가 플레이어를 옮기거나, Paper 한 대
구성에서는 같은 서버 안에서 안전한 위치로 텔레포트합니다.

데이터별 원본 위치는 다음과 같습니다.

- **SQL:** 섬, 실행 상태, 작업, 멤버, 권한, 경제, 미션, 랭킹, 스냅샷, 감사 기록
- **S3 또는 로컬 스토리지:** 이동 가능한 섬 묶음과 매니페스트
- **Redis:** 큐, 이벤트, 잠금, heartbeat와 캐시
- **Paper 로컬 디스크:** 현재 열린 월드와 저장 재시도 기록

Paper 로컬 디스크는 분산 구성의 최종 원본이 아닙니다.

### 장애와 복구

**Paper 노드 장애:** Core는 heartbeat가 충분히 오래 끊겼는지 확인한 뒤 노드를 장애
상태로 바꿉니다. 새 이동을 중단하고 영향을 받은 섬을 복구 대상으로 돌립니다. 다른
노드가 마지막으로 검증된 섬 묶음을 열며, 늦게 도착한 이전 노드의 저장 결과는 fencing
검사에서 거부됩니다.

**정상 재시작:** Paper는 다시 켜질 때 `STARTING`으로 등록한 뒤 `READY`로 전환합니다.
이전 프로세스가 남긴 `SHUTTING_DOWN` 상태 때문에 재시작한 노드가 계속 제외되지는
않습니다.

**오브젝트 스토리지 장애:** 이미 열린 섬은 계속 플레이할 수 있습니다. 새로 섬을 열거나
복원하는 작업은 안전하게 실패하고 저장 실패는 재시도 큐에 남습니다. 상태 확인은 Paper
메인 스레드 밖에서 수행합니다.

**Core 장애:** 이미 열린 섬의 보호와 제한된 로컬 동작은 유지됩니다. 다만 새로운 섬
열기, 멤버 변경, 경제 처리처럼 Core 확인이 필요한 작업은 실패로 닫힙니다. 분산 구성은
Core를 두 대 이상 두고 준비 상태를 확인하는 내부 로드밸런서를 사용하십시오.

**Redis 장애:** SQL과 섬 스토리지는 남지만 큐, 이벤트, 잠금, 캐시가 영향을 받습니다.
Core 준비 상태가 내려가면 새 작업을 받지 말고 Redis가 복구될 때까지 원본 데이터를
임의로 캐시에서 재구성하지 마십시오.

비동기 응답은 요청을 시작한 정확한 플레이어 연결과 일치할 때만 적용됩니다. 같은 UUID가
다시 접속했더라도 이전 연결에서 늦게 도착한 텔레포트, GUI, 인벤토리 결과를 새 연결에
적용하지 않습니다.

### 보안

Paper 한 대를 online mode로 직접 공개하는 경우 Core와 Redis는 비공개 네트워크에 두고,
Core token과 admin token은 서로 다른 무작위 값으로 설정합니다. direct-local routing을
쓸 때는 Velocity forwarding과 route session이 필요하지 않습니다.

분산 구성에서는 다음 항목이 필수입니다.

- 플레이어는 Velocity로만 접속
- 백엔드 Paper 포트는 외부 차단
- Velocity modern forwarding 사용
- 모든 백엔드에서 같은 forwarding secret 사용
- route session과 proxy source 검사 활성화
- Core, DB, Redis, S3는 내부 네트워크에만 노출

Core 관리 API는 별도 admin token과 서버 측 권한을 사용합니다. token을 URL query에
넣거나 로그에 출력하지 말고, 주기적으로 교체하십시오. 관리 포트는 loopback, VPN,
내부 ingress 중 하나로 제한합니다.

운영 중에는 비밀값을 커밋하지 말고, 업로드된 섬 묶음의 경로와 체크섬을 검증하며,
감사 로그와 support bundle에 token이나 개인정보가 포함되지 않는지 확인하십시오.

### 백업과 복원

다음 항목은 같은 복구 지점으로 묶어 백업합니다.

- PostgreSQL/MySQL 덤프 또는 일관된 스냅샷
- S3 bucket 또는 로컬 섬 스토리지
- Paper 저장 재시도 기록과 운영 설정
- 사용한 릴리스 버전, 이미지 digest, 설정 팩
- 암호화해 보관한 비밀값 복구 절차

Redis는 최종 원본이 아니므로 Redis만 백업해서는 복구할 수 없습니다.

복원은 별도 환경에서 먼저 연습합니다.

1. DB와 섬 스토리지를 같은 시점의 백업으로 복원합니다.
2. Core를 먼저 켜고 `/live`, `/ready`를 확인합니다.
3. Paper 노드를 켜고 heartbeat가 `READY`가 되는지 확인합니다.
4. 운영 데이터가 아닌 섬을 열고 체크섬 검증, 이동, 저장을 확인합니다.
5. 감사 로그에 복원과 이동 기록이 남았는지 확인합니다.
6. 확인이 끝난 뒤에만 플레이어 접속을 엽니다.

릴리스 단위의 전체 복구 검사는 다음 명령으로 실행합니다.

```bash
./gradlew releaseClusterSmokeGate
```

### SuperiorSkyblock2에서 이전하기

이전 작업은 운영 서버에서 바로 시작하지 말고 복사한 데이터로 먼저 연습하십시오.

1. 기존 서버와 SuperiorSkyblock2 데이터를 백업합니다.
2. `deploy/examples/migration-lab/config-pack.yml`로 격리된 환경을 만듭니다.
3. `/ciadmin migrate-superiorskyblock2 preflight`를 실행합니다.
4. dry-run 보고서에서 매핑되지 않은 월드, 멤버, 권한, 경제 값을 확인합니다.
5. 문제가 없을 때 실제 import를 실행합니다.
6. 섬 수, 소유자, 멤버, 홈, 워프, 은행, 역할, 설정을 대조합니다.
7. 일부 플레이어로 접속, 섬 이동, 저장, 재접속을 확인합니다.
8. 검증 자료와 원본 백업을 보관한 뒤 전환합니다.

가져오기는 재실행해도 같은 결과를 만들도록 설계되어 있지만, 확인 없이 운영 DB에
반복 실행해서는 안 됩니다. 월드와 플러그인 DB를 동시에 바꾸는 동안에는 기존 서버를
읽기 전용으로 두는 편이 안전합니다.

### 연동과 애드온

Vault, PlaceholderAPI, Plan, vanish 플러그인, ItemsAdder, Oraxen, Nexo,
CraftEngine, Slimefun, RoseStacker, WildStacker, AdvancedSpawners 연동이 포함되어
있습니다. `/ciadmin integrations report`에서 현재 서버에 설치된 연동 상태를 확인할 수
있습니다.

커스텀 블록과 가구는 일반 블록 보호 경계를 그대로 따라야 합니다. 스태커 연동은 화면에
보이는 엔티티 수가 아니라 논리 수량으로 제한과 드롭을 계산합니다. 실제 운영 조합은
플러그인별 버전 차이가 있으므로 공개 전에 반드시 직접 확인하십시오.

애드온은 `cloudislands-api`를 컴파일 의존성으로 사용하고 런타임 이벤트와 서비스는
Paper 메인 스레드 규칙을 따라야 합니다. `cloudislands-testkit`으로 호환성 검사를 만들 수
있고, 예제는 `cloudislands-example-addon`에 있습니다.

Satis는 선택 설치하는 공식 기능 팩입니다. 공장, 창고, 성장 기능을 제공하며 별도
플러그인으로 패키징됩니다. 사용하지 않는 서버에는 넣을 필요가 없습니다.

### 빌드와 릴리스

전체 검사는 저장소 최상위에서 실행합니다.

```bash
./gradlew check
```

자주 쓰는 검사는 다음과 같습니다.

```bash
./gradlew verifyMinecraftVersionMatrix verifyReadmeVersionTable
./gradlew apiCompatibilityCheck protocolCompatibilityCheck
./gradlew verifySnapshotRestoreCoverage verifyIntegrationRuntimeSmoke
./gradlew ciIntegrationSmoke
./gradlew ciBootSmoke
```

릴리스 묶음은 아래 명령으로 만듭니다.

```bash
./gradlew clean check distBundle distChecksums distSbom distProvenance distChangelog
```

결과는 `build/dist`에 생성됩니다.

- `cloudislands-<version>.zip`: 전체 배포 묶음
- `checksums-sha256.txt`: SHA-256 체크섬
- `sbom/cyclonedx.json`: CycloneDX SBOM
- `provenance.json`: 빌드 출처와 커밋 정보
- `CHANGELOG.txt`: README 릴리스 노트에서 만든 변경 내역
- `plugins/`, `services/`, `tools/`, `devkit/`: 설치 대상별 파일

배포 전에 체크섬, SBOM, provenance가 현재 커밋과 버전을 가리키는지 확인하십시오.
공개 API는 semantic versioning을 따르며 `apiCompatibilityCheck`에서 기준 시그니처와
비교합니다.

### 문제 해결

#### Core `/ready`가 내려간 경우

`/live`부터 확인합니다. 프로세스는 살아 있는데 `/ready`만 내려갔다면 DB, Redis,
스토리지, 큐, Paper heartbeat 상태를 차례로 봅니다. 준비되지 않은 Core에 공개 트래픽을
보내지 마십시오.

#### DB 스키마 오류로 Core가 켜지지 않는 경우

로그에 나온 migration 번호와 checksum을 확인합니다. 이미 적용된 migration 파일을
수정하지 말고 새 migration을 추가해야 합니다. 여러 Core가 동시에 스키마를 만들도록
설정하지 않았는지도 확인하십시오.

#### Paper 노드가 `READY`가 되지 않는 경우

`node.id`, 역할, pool, Core 주소, 인증 token, 스토리지 접근 권한을 확인합니다.
분산 구성이라면 Velocity 서버 이름, forwarding secret, route session 설정도 함께
대조합니다.

#### 플레이어가 섬에 들어가지 못하는 경우

`/ciadmin node list`, `/ciadmin route`, `/ciadmin jobs` 순서로 확인합니다. 사용 가능한
노드의 용량, heartbeat, 이동 티켓 만료, Velocity backend 이름, proxy source 경계를
점검하십시오. Paper 한 대라면 direct-local routing과 fallback world 설정을 봅니다.

#### 스냅샷이나 비활성화 작업이 끝나지 않는 경우

스토리지 상태, 저장 재시도 기록, 작업 claim, fencing token 충돌을 확인합니다. 저장되지
않은 로컬 월드 폴더를 먼저 지우면 안 됩니다. 원인을 해결한 뒤 같은 작업을 재시도합니다.

#### Paper 한 대인데 Redis 오류가 보이는 경우

Paper 설정의 `redis.enabled`가 `false`인지 확인합니다. Core는 여전히 Redis를 사용하므로
Core 쪽 Redis가 정상인지는 별도로 확인해야 합니다.

#### 설정 reload가 거부되는 경우

메시지와 일부 UI 설정은 즉시 반영되지만 노드 ID, 역할, pool, 네트워크 경계처럼 실행
구조를 바꾸는 값은 재시작이 필요합니다. 거부된 reload는 현재 실행 설정을 반쯤 바꾸지
않습니다.

#### 운영 자료가 필요한 경우

`/ciadmin support-bundle`을 사용하고 Core, Paper, Velocity 로그와 상태 응답을 함께
수집합니다. 외부에 전달하기 전에 token, 접속 정보, 플레이어 개인정보를 지우십시오.

### 이번 릴리스

`v1.1.263`의 주요 변경 사항입니다.

- Core Service를 `java -jar CloudIslands-Core-1.1.263.jar`로 실행할 수 있는 독립 fat JAR로
  배포하며 PostgreSQL, MySQL, MariaDB 드라이버와 필요한 런타임을 함께 포함합니다.
- Velocity의 사용되지 않던 embedded Core 설정, 모델, 기본 리소스를 제거했습니다.
  Velocity는 기존처럼 외부 Core API에 연결하며 라우팅과 명령 기능은 그대로 유지됩니다.
- 명시적인 ADVANCED 로비 설정을 BASIC 자동 감지가 덮어쓰지 않도록 부팅 검증 구성을
  분리했습니다.
- Gradle 9와 Java 25에서도 전체 Build 게이트가 동작하도록 application 배포와 독립
  Core JAR 패키징을 분리하고 JDBC ServiceLoader 항목을 직접 병합합니다.

- PlaceholderAPI가 설치되지 않아도 Satis 전체가 중단되지 않고 플레이스홀더 확장만
  건너뜁니다. PlaceholderAPI는 계속 선택 의존성입니다.
- 기존 Satis 설정 파일을 다시 저장하려다 출력하던 `already exists` 경고를 제거하고,
  기본 설정의 오래된 Core API 별칭과 `port: 0` 센티널 경고를 무시합니다.
- BASIC 모드에서 서버별 진단 포트 충돌을 막기 위해 내장 health endpoint를 기본
  비활성화하고, `island-1`/`islands-1` 폴더를 네트워크 섬 노드로 자동 인식합니다.
- 같은 컴퓨터의 Velocity를 위한 기본 신뢰 주소 `127.0.0.1`과 `::1`을 제공하며,
  `basic.trusted-proxies`에서 다른 프록시 주소나 CIDR로 바꿀 수 있습니다.

- Windows에서 Paper의 기존 평문 Core/Admin 토큰을 값 변경 없이
  `plugins/CloudIslands/secrets`로 자동 이관하고 안전한 파일 참조로 교체합니다.
- BASIC 로비는 Redis와 S3를 기본 요구하지 않으며, Redis를 사용할 때만
  `redis://127.0.0.1:6379` 같은 주소를 명시적으로 입력합니다.
- Core 중단 시 권한 이벤트, 섬 작업, heartbeat 경고를 하나의 장애 메시지로 합치고
  최대 60초 백오프와 5분 요약을 적용하며, 복구 시 한 번만 알립니다.
- Paper와 Velocity의 주요 설정 모든 항목에 용도와 입력 방법을 설명하는 주석을
  추가하고, `lobby`, `hub`, `spawn` 서버 폴더를 BASIC 로비로 자동 감지합니다.
- OpenSSL 없이 Core/Admin/forwarding 키를 만드는 Windows
  `generate-cloudislands-keys.cmd`를 릴리스 ZIP과 개별 자산으로 배포합니다.

- 태그 CI가 실제로 확인한 Core, PostgreSQL, MySQL, Redis, MinIO, 로비 Paper,
  Velocity 증거를 전용 게이트로 검증합니다.
- CI에서 만들 수 없는 전체 운영 장애 주입 증거는 `releaseClusterSmokeGate`에 그대로
  남겨 두어, 부분 증거가 GA 전체 인증으로 잘못 표시되지 않게 했습니다.
- 태그 Integration 결과에 결합된 증거 JSON과 부족 항목 보고서를 함께 업로드합니다.

- 한국어 운영 문서를 첫 화면에 추가하고, 같은 내용을 확인할 수 있도록 영어 문서를
  아래에 함께 제공합니다.
- Paper 한 대와 분산 구성의 설치, 보안, 백업, 복구, 이전, 장애 대응 절차를 실제 실행
  순서에 맞춰 다시 정리했습니다.
- 릴리스 증거 검사에서 Paper agent와 로비 역할 표식을 모두 확인하도록 테스트 계약을
  현재 동작과 맞췄습니다.

- PostgreSQL, 비공개 Redis, 로컬 섬 스토리지를 사용하는 Paper 한 대 구성을 정식으로
  지원합니다.
- 섬 생성 직후 같은 이동 티켓과 안전 텔레포트 경로로 바로 이동합니다.
- 정상 재시작한 Paper 노드가 `STARTING`을 거쳐 다시 합류합니다.
- S3 상태 확인을 비동기로 처리해 Paper 메인 스레드를 막지 않습니다.
- 마지막 섬 위치를 기록하고, 아직 열리지 않은 섬에서 재접속한 플레이어를 대기 월드로
  안전하게 돌려보냅니다.
- Paper에서 `redis.enabled: false`가 실제로 적용되며 상태 페이지에도 올바르게 표시됩니다.
- 늦게 도착한 비동기 응답을 새 플레이어 연결에 적용하지 않습니다.
- 1.0.x 공개 API 호환성을 유지하면서 지원 버전용 Paper 파일을 하나로 제공합니다.

현재 `1.1.263` 기준으로 Paper 한 대와 분산 구성 모두 실제 운영에 필요한 경로가 구현되어
있습니다. 저장소의 자동 검사는 DB, Redis, 오브젝트 스토리지, Core 이중화, Paper와
Velocity 부팅, 섬 생성과 복원, 노드 재시작, 스토리지 장애, 체크섬, SBOM, provenance를
다룹니다. 다만 운영 환경의 플러그인 조합, 네트워크, 저장소, 권한, 플레이어 부하는 배포
전에 별도로 확인해야 합니다.

주요 경로는 다음과 같습니다.

- `deploy/examples/single-paper/docker-compose.yml`: 가장 작은 전체 구성
- `deploy/compose/docker-compose.yml`: 분산 구성
- `deploy/helm/cloudislands`: Kubernetes 차트
- `gradle/minecraft-versions.toml`: 지원 Minecraft 버전 기준
- `build/dist`: 릴리스 결과물
- `cloudislands-paper/src/main/resources/config-v2`: Paper 기본 설정
- `cloudislands-velocity/src/main/resources/config-v2`: Velocity 기본 설정
- `cloudislands-core-service/src/main/resources`: Core 설정과 DB migration

상세한 검증 근거 표는 아래 영어 문서의 **Verified feature coverage**에서 확인할 수
있습니다.

저장소: <https://github.com/M-LunaFarm/islandmc-cloudislands>

---

# English

CloudIslands is a production-oriented Skyblock platform for Paper and Velocity.
It treats each island as a portable, globally owned resource instead of tying it
to one Minecraft server. Core owns durable state, Paper runs island worlds, and
Velocity routes players with short-lived tickets.

Version: `1.1.263`

CloudIslands supports both of these deployment shapes:

- **Single Paper:** one public Paper server, Core, PostgreSQL, and private Redis.
  No Velocity or separate lobby server is required.
- **Distributed network:** Velocity, lobby Paper, multiple island Paper nodes,
  two Core instances, PostgreSQL, Redis, and S3-compatible object storage.

The supplied Docker Compose stacks are the fastest supported way to evaluate or
operate either topology.

## Contents

- [Why CloudIslands](#why-cloudislands)
- [Requirements](#requirements)
- [Supported Minecraft versions](#supported-minecraft-versions)
- [Choose a deployment](#choose-a-deployment)
- [Single Paper quickstart](#single-paper-quickstart)
- [Distributed quickstart](#distributed-quickstart)
- [Configuration](#configuration)
- [First validation](#first-validation)
- [Player and operator commands](#player-and-operator-commands)
- [Architecture](#architecture)
- [Reliability and recovery](#reliability-and-recovery)
- [Security](#security)
- [Backup and restore](#backup-and-restore)
- [SuperiorSkyblock2 migration](#superiorskyblock2-migration)
- [Integrations and addons](#integrations-and-addons)
- [Development and release](#development-and-release)
- [Troubleshooting](#troubleshooting)
- [Verified feature coverage](#verified-feature-coverage)
- [Release notes](#release-notes)

## Why CloudIslands

CloudIslands separates logical island ownership from the server currently
hosting its chunks.

- Islands can activate on any compatible ready node.
- Portable bundles include manifests and SHA-256 checksums.
- Fencing tokens prevent stale nodes from committing lifecycle results.
- Route tickets are player-, node-, nonce-, and expiry-bound.
- Core persists islands, membership, economy, permissions, missions, rankings,
  snapshots, jobs, and audit data in SQL.
- Redis accelerates queues, events, locks, and caches but is not the durable
  source of truth.
- Paper protection uses local indexed state on synchronous event paths; it does
  not perform HTTP, SQL, or Redis calls while deciding block events.
- Failed storage saves remain retryable while already loaded islands continue
  local play.
- A single Paper deployment uses the same lifecycle engine and consumes route
  tickets locally.

Portable means CloudIslands-owned world and island state. Third-party database
rows, CoreProtect history, WorldEdit undo history, and other provider-owned
state are not silently embedded in an island bundle.

## Requirements

### Runtime

| Component | Required baseline |
|---|---|
| Paper | `1.21.x` or stable `26.1.x` |
| Java | Java 21 for Paper `1.21.x`; Java 25 for Paper `26.1.x` and `26.2.x` |
| Velocity | `3.5.0-SNAPSHOT` compile baseline for distributed deployments |
| Database | PostgreSQL 16 recommended; MySQL and MariaDB are supported |
| Redis | Redis 7 recommended for Core queues, events, locks, and caches |
| Storage | S3-compatible shared storage for a cluster; local filesystem for one host |
| Build | Gradle Wrapper 9.1; no system Gradle installation required |

Velocity `3.5.0-SNAPSHOT` remains the proxy compile baseline for distributed
deployments.

### Host sizing

Capacity depends on island count, view distance, plugins, and automation load.
Start small, observe MSPT and heap, then tune each service independently. The
example stacks intentionally use modest development defaults; they are not a
universal production sizing recommendation.

### Before public traffic

- Use durable named volumes or external managed services.
- Keep Core, SQL, Redis, object storage, and clustered Paper backends private.
- Use unique node IDs and Velocity backend names.
- Back up SQL and island storage together.
- Run the validation and release gates documented below.
- Test with the exact economy, permissions, custom-block, and stacker plugins
  used by the target server.

## Supported Minecraft versions

The universal Paper artifact packages explicit runtime adapters. Stable support
requires compile, boot-smoke, packaging, and release-gate evidence; a successful
compile alone is not reported as production support.

<!-- minecraft-version-matrix:start -->
| Target | Compile | Boot smoke | Release | Notes |
|---|---|---|---|---|
| Paper `1.21.x` | `paper121Compile` | `paper121BootSmoke` | release-supported | current paper-api and plugin.yml baseline |
| Paper `26.1.x` | `paper261Compile` | `paper261BootSmoke` | release-supported | stable Paper 26.1.2 API compile and boot verified on Java 25 |
| Paper `26.2.x` | `paper262Compile` | `paper262BootSmoke` | experimental boot-verified | official Paper 26.2 beta build 60 API compile and boot verified on Java 25; stable release channel pending |
<!-- minecraft-version-matrix:end -->

`gradle/minecraft-versions.toml` is authoritative. Run
`./gradlew verifyReadmeVersionTable verifyMinecraftVersionMatrix` after changing
the matrix.

## Choose a deployment

| Deployment | Use it when | Public entrypoint | Island storage |
|---|---|---|---|
| Single Paper | One Minecraft server is enough | Paper `25565` | Durable local path or S3 |
| Distributed Compose | You need lobby separation, HA Core, or multiple island nodes | Velocity `25565` | Shared S3/MinIO |
| Helm | You already operate Kubernetes and externalize persistence correctly | Velocity Service | Shared object storage |
| Manual config pack | Existing services and process supervision are already in place | Paper or Velocity | Local or shared S3 |

Deployment examples live under `deploy/examples`; the full local cluster is in
`deploy/compose`, and the Kubernetes chart is in `deploy/helm/cloudislands`.

## Single Paper quickstart

This stack starts PostgreSQL, private Redis, Core, and one public Paper server.
The current Compose defaults use Paper 26.1.2 images. Paper performs island
activation, protection, commands, GUI, saving,
restore, and local ticket consumption. Redis remains enabled for Core but is
disabled inside the Paper plugin.

### 1. Prepare configuration

```bash
cd deploy/examples/single-paper
cp .env.example .env
mkdir -p secrets
umask 077
openssl rand -hex 32 > secrets/database-password
openssl rand -hex 32 > secrets/core-token
openssl rand -hex 32 > secrets/admin-token
mkdir -p /srv/cloudislands/islands-storage
```

Edit `.env` and set `CLOUDISLANDS_STORAGE_PATH` to the absolute durable path
created above. Keep these production defaults:

```dotenv
CLOUDISLANDS_PAPER_ONLINE_MODE=true
CLOUDISLANDS_PAPER_VERSION=26.1.2
MINECRAFT_EULA=TRUE
```

Setting `MINECRAFT_EULA=TRUE` confirms that you accept the Minecraft EULA.

### 2. Start the stack

```bash
docker compose up -d --build --wait
docker compose ps
```

Join `localhost:25565` unless `CLOUDISLANDS_PAPER_PORT` was changed.

### 3. Validate it

```bash
curl --fail http://127.0.0.1:8443/ready
docker compose exec paper curl --fail --silent http://127.0.0.1:8789/health
docker compose logs --tail=200 core paper
```

In game:

```text
/is create default
/is home
/ciadmin setup verify
/ciadmin doctor
```

Create an island, leave it, reconnect, and run `/is home`. The direct-local
runtime stores the last island marker and returns stale unloaded-island logins
to the configured fallback world instead of applying island coordinates to the
default world.

### 4. Stop without deleting data

```bash
docker compose down
```

Do not add `-v` unless you intentionally want to delete PostgreSQL, Redis, and
Paper volumes. The directory in `CLOUDISLANDS_STORAGE_PATH` is outside those
volumes and must be backed up separately.

## Distributed quickstart

The clustered stack starts PostgreSQL, password-protected Redis, MinIO, two Core
instances behind HAProxy, Velocity, one lobby Paper, and two island Paper nodes.
Only Velocity is publicly exposed. Core is published on loopback for health and
operator access; the backend services and Paper nodes have no public host port.

### 1. Create secrets

From the repository root:

```bash
mkdir -p /srv/cloudislands/secrets
umask 077
openssl rand -hex 32 > /srv/cloudislands/secrets/database-password
openssl rand -hex 32 > /srv/cloudislands/secrets/redis-password
openssl rand -hex 20 > /srv/cloudislands/secrets/storage-access-key
openssl rand -hex 32 > /srv/cloudislands/secrets/storage-secret-key
openssl rand -hex 32 > /srv/cloudislands/secrets/core-token
openssl rand -hex 32 > /srv/cloudislands/secrets/admin-token
openssl rand -base64 48 | tr -d '\n' > /srv/cloudislands/secrets/forwarding-secret
```

Export the file paths:

```bash
export CLOUDISLANDS_DATABASE_PASSWORD_FILE=/srv/cloudislands/secrets/database-password
export CLOUDISLANDS_REDIS_PASSWORD_FILE=/srv/cloudislands/secrets/redis-password
export CLOUDISLANDS_STORAGE_ACCESS_KEY_FILE=/srv/cloudislands/secrets/storage-access-key
export CLOUDISLANDS_STORAGE_SECRET_KEY_FILE=/srv/cloudislands/secrets/storage-secret-key
export CLOUDISLANDS_CORE_TOKEN_FILE=/srv/cloudislands/secrets/core-token
export CLOUDISLANDS_ADMIN_TOKEN_FILE=/srv/cloudislands/secrets/admin-token
export CLOUDISLANDS_FORWARDING_SECRET_FILE=/srv/cloudislands/secrets/forwarding-secret
export MINECRAFT_EULA=TRUE
```

The forwarding secret must be shared by Velocity and every clustered Paper
backend. Never reuse it as the Core or admin token.

### 2. Start the cluster

```bash
docker compose -f deploy/compose/docker-compose.yml up -d --build --wait
docker compose -f deploy/compose/docker-compose.yml ps
```

The default client address is `localhost:25565`.

### 3. Validate routing capacity

```bash
curl --fail http://127.0.0.1:8443/live
curl --fail http://127.0.0.1:8443/ready
docker compose -f deploy/compose/docker-compose.yml logs --tail=200 core-1 core-2 velocity lobby-paper island-paper-a island-paper-b
```

`/ready` must report durable database, Redis, object storage, queue, and fresh
island-node heartbeat checks as ready. The default topology should expose two
route candidates before public traffic reaches Velocity.

Run an end-to-end player smoke:

```text
/is create default
/is home
/is visit <player-or-island>
/ciadmin node list
/ciadmin doctor
```

### 4. Scale island nodes

Every island Paper process needs:

- a unique `node.id`;
- a unique Velocity server name matching the proxy backend entry;
- the same island pool when it should serve the same allocation group;
- the same Core endpoint, storage bucket, and forwarding secret;
- a distinct writable Paper data directory.

Never clone a live Paper data directory between running nodes. Island ownership
comes from Core and storage, not from copying one server's local world state.

## Configuration

CloudIslands uses Config v2 YAML. Bundled defaults are materialized under the
plugin data directory on first start.

For a compact setup, keep `configuration-mode: BASIC` in the Paper and Velocity
`config-v2/config.yml` files. Paper accepts `SINGLE_PAPER`, `NETWORK_ISLAND`, or
`LOBBY`; the lobby role exposes the API, `/is`, addon commands, and tab
completion without executing island worlds. Switch to `ADVANCED` to manage the
split configuration files directly.

Run Core with `CI_CONFIGURATION_MODE=BASIC` or `-Dcloudislands.mode=BASIC` for the bundled local MySQL profile.
It defaults to `127.0.0.1:3306/cloudislands` with automatic schema creation and
accepts `CI_DB_USERNAME`, `CI_DB_PASSWORD`, and `CI_CORE_TOKEN` overrides. An
advanced single YAML can be selected with `CI_CONFIG_FILE` or
`-Dcloudislands.config=...`.

When the forwarding secret is blank, Velocity reads the path configured by
`forwarding-secret-file` in `velocity.toml`. If the file is absent, a 32-byte
hex secret is created with Java `SecureRandom` on both Windows and Linux; no
`openssl rand` command is required. Paper automatically recognizes
`proxies.velocity.secret` from `config/paper-global.yml`.

### Config packs

| Path | Purpose |
|---|---|
| `deploy/examples/basic-mysql/config-pack.yml` | BASIC mode with a local MySQL quick start |
| `deploy/examples/single-paper/config-pack.yml` | One public Paper server with direct-local routing |
| `deploy/examples/single-node/config-pack.yml` | One clustered island node |
| `deploy/examples/two-island-nodes/config-pack.yml` | Two-node routing and capacity example |
| `deploy/examples/production-ha/config-pack.yml` | HA-oriented production baseline |
| `deploy/examples/migration-lab/config-pack.yml` | Isolated SuperiorSkyblock2 migration rehearsal |

### Paper configuration groups

- `runtime.yml`: node identity, role, pool, capacity, heartbeat, and health.
- `integrations.yml`: Core endpoint, Redis, storage, routing mode, and hooks.
- `security.yml`: Core/admin tokens, forwarding, route sessions, and proxies.
- `features.yml`: GUI and feature switches.
- `gameplay.yml`: generators, protection, limits, and gameplay policy.
- `ui/`: localized messages, theme, and menu definitions.

Use `ISLAND_NODE` for servers that host island worlds. Use `LOBBY` for a
clustered lobby that provides commands and GUI without activation or saving.

For single Paper, the important values are:

```yaml
redis:
  enabled: false
routing:
  direct-local-teleport: true
  local-fallback-world: world
forwarding:
  required: false
route-session:
  enforce: false
  required: false
```

For clustered Paper, keep direct-local routing disabled and require Velocity
modern forwarding, a route session, and a proxy-source boundary.

### Core persistence

Production mode rejects unsafe in-memory authority. Use PostgreSQL, MySQL, or
MariaDB with JDBC fallback disabled. Automatic schema creation is explicit and
serialized across Core instances. Applied migrations are checksum-tracked;
modified migration history or incompatible critical columns fail startup.

Use one migration leader with automatic schema enabled and start additional
Core instances with automatic schema disabled after the schema is ready. The
supplied distributed Compose stack already follows this pattern.

### Secrets

Prefer Docker/Kubernetes secret files or a host secret manager. Do not commit
tokens, passwords, access keys, forwarding secrets, `.env` files, or generated
runtime configuration containing resolved secrets.

### Helm

The chart is under `deploy/helm/cloudislands`. Set an existing Secret containing
the keys configured under `secrets.*`, pin every image tag, provide durable
storage classes, and use at least two Core replicas for HA. The chart defaults
are a starting point, not a substitute for network policies, TLS, backups, and
pod disruption planning.

## First validation

Treat a healthy process as necessary but insufficient. Validate the user flow
and persistence path.

1. Confirm Core `/live` and `/ready` are `UP`.
2. Confirm every expected Paper node appears in `/ciadmin node list`.
3. Run `/ciadmin setup verify` and `/ciadmin doctor`.
4. Create an island and verify the player reaches its generated spawn.
5. Disconnect and run `/is home` after reconnecting.
6. Create a snapshot, deactivate the island, and restore it.
7. Restart Paper and confirm the node returns from `STARTING` to `READY`.
8. Rehearse a storage outage and verify active local play remains available.
9. Inspect Core, Paper, and Velocity health endpoints and logs for retries or
   stale-node failures.
10. Back up and restore a non-production island before onboarding players.

The repository release gates cover these contracts, but operators must still
exercise their own network, plugins, permissions, economy, and storage.

## Player and operator commands

### Player entrypoints

- `/is`, `/island`, `/섬`: island menu and player commands.
- `/is help`: paginated command help for the enabled feature set.
- `/is create [template]`, `/is home`, `/is visit`, `/is warp`: lifecycle and routing.
- `/is members`, `/is invite`, `/is trust`, `/is permissions`: team access.
- `/is bank`, `/is warehouse`, `/is upgrades`, `/is missions`: progression.
- `/is settings`, `/is fly`, `/is biome`, `/is border`: island environment.
- `/is snapshot`, `/is restore`: owner-facing recovery where permitted.

Commands are permission-gated under `cloudislands.island.*`. The base
`cloudislands.player` permission defaults to players; mutation permissions can
be restricted by the server permission provider.

### Operator entrypoints

- `/ciadmin status`: compact service and node state.
- `/ciadmin setup verify`: deployment wiring validation.
- `/ciadmin doctor`: first-line health and recovery guidance.
- `/ciadmin node ...`: list, inspect, drain, undrain, move, and safe shutdown.
- `/ciadmin island ...`: inspect, activate, deactivate, save, restore, repair,
  quarantine, migrate, or delete.
- `/ciadmin jobs`, `/ciadmin route`, `/ciadmin storage`: control-plane diagnosis.
- `/ciadmin audit`, `/ciadmin metrics`, `/ciadmin support-bundle`: evidence and observability.
- `/ciadmin integrations report`: optional plugin adapter status.
- `/ciadmin migrate-superiorskyblock2 ...`: migration workflow.

Paper permissions use `cloudislands.admin.*`. Core separately enforces
server-side admin-token permissions. Granting a Bukkit permission does not
bypass the Core admin policy.

## Architecture

```text
Players
   |
   +--> Single Paper --------------------------+
   |         |                                 |
   |         +-- local route-ticket consume    |
   |                                           v
   +--> Velocity --> Lobby / Island Paper --> Core API
                                               |   |   |
                                               |   |   +--> Redis
                                               |   +------> SQL authority
                                               +----------> island storage
```

### Module map

| Module | Responsibility |
|---|---|
| `cloudislands-api` | Public addon API, events, services, and typed contracts |
| `cloudislands-common` | Shared security, routing, config, failure, and cache policies |
| `cloudislands-protocol` | Wire DTOs and compatibility contracts |
| `cloudislands-core-client` | Typed asynchronous Core client |
| `cloudislands-core-service` | Durable authority, HTTP/admin API, jobs, audit, and allocation |
| `cloudislands-paper` | Commands, GUI, protection, activation, save, restore, and teleport |
| `cloudislands-velocity` | Proxy commands, route preparation, sessions, and transfers |
| `cloudislands-storage` | Bundles, manifests, checksums, snapshots, and retention |
| `cloudislands-migration` | SuperiorSkyblock2 import and verification tooling |
| `cloudislands-satis` | Optional official factory/progression feature pack |
| `cloudislands-testkit` | Addon and integration fixtures |
| `cloudislands-bom` | Developer dependency alignment |

### Island lifecycle

1. A player requests create, home, visit, or warp.
2. Core validates permissions and locks the logical island transition.
3. The allocator selects a fresh compatible node with available capacity.
4. Core publishes a fenced job.
5. Paper claims the job, restores or creates the island cell, and preloads it.
6. Paper reports completion with the claim and fencing token.
7. Core commits the active runtime and marks the route ticket ready.
8. Velocity transfers the player, or single Paper consumes the ticket locally.
9. Paper resolves a safe destination within the active island region.

Physical node, world, cell, storage key, and database details stay out of
player-facing messages.

### Data authority

- **SQL:** durable islands, runtime, jobs, members, permissions, economy,
  missions, rankings, snapshots, audit, and idempotency receipts.
- **Object/local storage:** portable island bundles and manifests.
- **Redis:** queues, events, locks, heartbeat/cache acceleration.
- **Paper local disk:** active runtime worlds and retry journals, not cluster authority.

## Reliability and recovery

### Node failure

Core confirms stale heartbeats before declaring a node down. New routes stop,
affected islands enter recovery, and another compatible node restores the
latest verified bundle. Stale completions fail fencing checks.

### Graceful restart

A restarted Paper node explicitly rejoins through `STARTING` and returns to
`READY`; a previous graceful `SHUTTING_DOWN` heartbeat does not permanently
exclude the new process.

### Object storage failure

Active islands remain loaded for local play. New restore/activation work that
needs storage fails closed, and periodic or empty-island save failures remain
queued for retry. Storage health probing runs off the Paper main thread.

### Core failure

Loaded island protection and constrained local play continue. Control-plane
mutations, new activations, and routes remain limited until Core is healthy.

### Redis failure

SQL remains durable authority. Queue/event and cache paths degrade or pause;
they must not silently turn into divergent per-process production authority.

### Player reconnect safety

Single Paper records the last active island in player persistent data. If Paper
loads that player's island coordinates into the fallback world after the shard
was unloaded, CloudIslands compares the marker with the active-island registry
and moves the player to the configured safe fallback spawn.

## Security

### Single Paper

- Keep Paper `online-mode=true` whenever it is directly reachable.
- Publish only the Paper port and loopback operator endpoints.
- Keep Core, SQL, and Redis on a private container or host network.
- Protect Core and admin APIs with separate random tokens.

### Distributed network

- Velocity is the only public Minecraft entrypoint.
- Use modern forwarding with the same strong secret on Velocity and Paper.
- Firewall Paper backends and require route sessions.
- Keep Core, SQL, Redis, and object storage private.
- Terminate TLS at a trusted internal boundary if Core leaves one host.
- Strip spoofable security headers at the proxy boundary.

### Admin API

Core requires both normal API authentication and the admin token for admin
routes. `CI_ADMIN_PERMISSIONS` is the server-side allowlist. Unknown permission
names fail startup, and future permissions are not granted automatically by the
explicit default profile.

### Operational rules

- Never store secrets in Git or logs.
- Never expose Redis, PostgreSQL, MySQL, MariaDB, or MinIO directly to players.
- Never enable in-memory production fallback.
- Never bypass schema checksum or contract failures.
- Treat support bundles and audit exports as sensitive operational data.

## Backup and restore

### Back up together

1. SQL authority.
2. S3 bucket or `CLOUDISLANDS_STORAGE_PATH`.
3. Deployment configuration and secret references.
4. Third-party databases such as CoreProtect or economy providers, separately.

For single Paper, also retain the Paper volume when player inventories and
vanilla player data live there.

### Restore rehearsal

1. Restore SQL to an isolated environment.
2. Restore the matching island storage snapshot.
3. Start Core and verify migration checksums and schema contracts.
4. Start Paper nodes and confirm compatible heartbeats.
5. Restore one island and verify its manifest checksum.
6. Consume a home route and inspect audit evidence.
7. Run `releaseClusterSmokeGate` before declaring the backup usable.

CloudIslands does not treat a CoreProtect rollback or WorldEdit undo history as
an island-bundle restore.

## SuperiorSkyblock2 migration

SuperiorSkyblock2 migration is input-only; CloudIslands has no runtime
dependency on SuperiorSkyblockAPI.

Use an isolated migration lab and follow this order:

1. **Scan** the legacy source and list unsupported or ambiguous data.
2. **Dry-run** owner, member, role, permission, home, warp, economy, mission,
   generator, limit, and world conversions.
3. **Back up** legacy SQL/world data and CloudIslands SQL/storage.
4. **Approve** the exact dry-run report with the generated approval token.
5. **Import** without allowing legacy and CloudIslands writers concurrently.
6. **Verify** Core state, bundles, manifests, checksums, permissions, economy,
   and player routes.
7. **Compare** source and destination counts and exceptions.
8. **Plan rollback** before removing the legacy provider.

Start from `deploy/examples/migration-lab/config-pack.yml`. Migration reports
are runtime artifacts and should not be committed to this repository.

## Integrations and addons

Optional soft integrations include Vault, PlaceholderAPI, LuckPerms,
CoreProtect, WorldEdit, FAWE, ItemsAdder, Oraxen, Nexo, CraftEngine,
RoseStacker, WildStacker, AdvancedSpawners, Plan, ProtocolLib, SkinsRestorer,
SuperVanish, PremiumVanish, SlimeWorldManager, Slimefun, and CMI.

Use `/ciadmin integrations report` to distinguish detected, missing, degraded,
and unsupported adapters. Optional API failures fall back conservatively and
remain observable.

### Custom blocks and stacks

CloudIslands can value and limit custom blocks from supported providers and can
reconcile logical stack amounts. Provider keys use lower-case prefixes such as
`itemsadder:`, `oraxen:`, `nexo:`, `craftengine:`, and `slimefun:`. When several
stacker providers describe the same position, CloudIslands uses the highest
logical amount instead of double counting.

### PlaceholderAPI

The expansion exposes island identity, owner, role, team, co-op, level, worth,
rank, bank, limits, homes, warps, flags, permissions, upgrades, chat state, and
SS2-compatible aliases. Reads are coalesced and cached by island to avoid one
Core request per scoreboard line and player.

### Addon API

External addons can register collision-safe `/is` subcommands through
`AddonIslandCommand`. The API supports permissions, argument bounds,
asynchronous results, tab completion, help integration, and automatic cleanup
when an addon is disabled. `SimpleAddonIslandCommand.builder(...)` provides a
short executor-and-suggestions form for common commands. Use
`cloudislands-testkit` and the example addon as the compatibility reference.

### Satis

`cloudislands-satis` is optional. Its machines, resource nodes, contracts,
research, market, storage, GUI, and placeholders remain scoped by CloudIslands
island UUID. Disabled features stop their listeners and tickers without purging
stored island data.

## Development and release

### Build

```bash
./gradlew build
```

The wrapper may run on Java 25; Gradle toolchains select the Java version needed
by each compile and boot-smoke target.

Useful gates:

```bash
./gradlew verifyMinecraftVersionMatrix compileAllMinecraftVersions
./gradlew bootSmokeAllStableMinecraftVersions verifyAdapterPackaging
./gradlew apiCompatibilityCheck protocolCompatibilityCheck
./gradlew ciIntegrationSmoke
./gradlew releaseClusterSmokeGate
```

Full local release gate:

```bash
./gradlew build distBundle distChecksums distSbom distProvenance --no-daemon
```

### Release artifacts

Artifacts are generated under `build/dist`:

- `cloudislands-<version>.zip`;
- `cloudislands-addons-<version>.zip`;
- Paper and Velocity plugin jars;
- Core service runtime;
- migration tools and developer kit;
- `checksums-sha256.txt`;
- `cloudislands-sbom.cdx.json`;
- `provenance.json`;
- `CHANGELOG.txt`.

Verify the complete bundle from inside `build/dist`:

```bash
sha256sum -c checksums-sha256.txt
```

### API compatibility

CloudIslands follows semantic versioning for the public addon API. The public
signature baseline, contract metadata, example addon, and testkit are checked
before release. Deprecated API remains available for at least one minor release
before removal.

### Repository documentation policy

Operator documentation belongs in this README. Generated parity, migration,
support-bundle, and smoke reports are runtime/build artifacts rather than
committed Markdown documents.

<!-- operator-release-docs:start -->
## Operator release documentation

### Production setup

Use durable JDBC authority, shared storage, private Redis, authenticated Core,
and explicit server-side admin permissions. A distributed production network
should run at least two Core instances behind an internal health-checked load
balancer. Run `/ciadmin setup verify`, `/ciadmin doctor`, player create/home
smokes, backup/restore rehearsal, `ciIntegrationSmoke`, and
`releaseClusterSmokeGate` before opening traffic.

### Local dev stack

Use the single-Paper Compose example for the smallest complete stack or
`deploy/compose/docker-compose.yml` for the distributed shape. Wait for Core
`/ready`, confirm all Paper heartbeats, then run `/ciadmin setup verify`,
`/ciadmin doctor`, island create, route consume, snapshot, and restore checks.

### Migration procedure

SuperiorSkyblock2 migration must run as scan, dry-run, backup, approval, import,
verify, compare, and rollback planning. Do not run both providers as concurrent
authoritative writers, and do not decommission the source until SQL and bundle
evidence has been reconciled.

### Troubleshooting

Start with `/ciadmin doctor`, then inspect `/ciadmin node list`, `/ciadmin island
inspect`, `/ciadmin route debug`, `/ciadmin storage verify`, Core `/ready`, and
the component health endpoints. Preserve failure codes and support bundles for
operators without exposing node IDs, storage keys, tokens, or database errors
to players.

### Release artifacts and changelog

`./gradlew build distBundle distChecksums distSbom distProvenance` produces the
release bundle, `checksums-sha256.txt`, `cloudislands-sbom.cdx.json`,
`provenance.json`, and generated `CHANGELOG.txt`. A matching `v*` tag runs the
dedicated release workflow and publishes the verified distributable assets.
<!-- operator-release-docs:end -->

## Troubleshooting

### Core `/ready` is down

Read every failed check in the response. Verify SQL connectivity and schema,
Redis authentication, object-storage credentials/bucket, job queue, and fresh
Paper heartbeats. `/live` only proves the process is alive; it does not prove
the deployment can route an island.

### Database schema contract mismatch

Stop writes and back up SQL. Repair every reported `table.column` to the
expected type, then rerun the normal migration/bootstrap path. Do not bypass
the guard; it exists to prevent later island-creation corruption.

### Paper node never becomes ready

Check unique `node.id`, pool, Velocity backend name, Core token, storage access,
supported templates, hard capacity, and heartbeat timestamps. A node in
`DRAINING` intentionally refuses new activations. A restarted graceful node
must send `STARTING` before returning to `READY`.

### Player cannot enter an island

Inspect the route ticket state, target node, expiry, nonce/session publication,
world activation job, and Paper teleport counters. In a cluster, confirm modern
forwarding and the backend firewall. In single Paper, confirm
`direct-local-teleport: true` and the exact `local-fallback-world` name.

### Snapshot or deactivation is stuck

Check storage health, save retry queues, pending snapshot journals, world flush,
chunk unload, manifest generation, and checksum upload. CloudIslands will not
unload an island after a failed required save.

### Redis failures appear in single Paper health

Paper Config v2 must contain `redis.enabled: false`. Core still needs its private
Redis service. Health should show `redisEnabled=false` and no synthetic Paper
Redis failure growth.

### Config reload is rejected

Runtime-safe message and UI changes can reload atomically. Node identity,
storage backend, forwarding, and other process-level changes require a restart;
the active configuration remains unchanged when reload validation fails.

### Need an evidence bundle

Use `/ciadmin support-bundle` and collect Core, Paper, and Velocity health/log
windows around the failure. Redact tokens, player-sensitive data, storage keys,
and private topology before sharing.

## Verified feature coverage

The block below is generated from repository evidence and verified by
`verifyFeatureParityEvidence`. Do not edit it independently of the Gradle gate.

<!-- feature-parity:start -->
| Area | Status | Verified evidence | Limit |
|---|---|---|---|
| lifecycle/templates/homes/warps/visits | IMPLEMENTED_VERIFIED | ciIntegrationSmoke verifies advisory-lock-serialized dual-Core schema bootstrap on PostgreSQL and MySQL 8.4 plus cross-Core create, job, route, session, consume, player-ticket cache convergence, node recovery, bank, membership, warp, event replay, and database backup behavior; Paper 1.21.11, 26.1.2 stable, and 26.2 build 60 beta smoke verify normal command registration and runtime startup, while Paper 26.1.2 additionally verifies rejected-bootstrap rollback, diagnostic /is and /ciadmin, corrected-config retry, and second-attempt READY recovery; Paper tests verify main-thread template permission preflight, exact initiating-Player fencing for paid creation plus delete/reset feedback, automatic post-charge refund before Core creation when the connection is replaced, UUID/island-name/player-name targeted warp resolution in native and migration commands, exact initiating-Player fencing for home/warp lookup, permission resolution, safe-destination lookup, local teleport, fallback movement, and feedback, one observable warp-to-island-info lookup chain, stale target-info response rejection, Core-authoritative newest-intent revisions for asynchronous primary-island selection across Paper nodes, exact selection-feedback connection fencing, scheduler-bound single-Paper fallback teleport, target-island coordinates, safe destination scans, final online-player revalidation, bounded destination revalidation, teleport warmup cancellation as soon as a player moves or starts falling within the same block, and exact initiating-Player fencing for both player and administrator teleports through route creation, polling, publication, local consumption, world readiness, safe-destination resolution, final teleport, fallback movement, feedback, loading bars, and delayed route-session rejection | 26.2 build 60 beta is compile- and boot-verified but remains experimental and is not release-supported until Paper publishes a stable channel build |
| access/bans/membership/roles/permissions | IMPLEMENTED_VERIFIED | Core API and permission event replay are exercised in tests; accepted visitor bans and kicks return to the Paper scheduler and evict the target independently from actor connectivity, while all delayed member and ban lists, invite creation/list/accept/decline, leave/remove/uncoop, role/trust/ownership changes, pardon, and actor feedback require the exact initiating Player connection; permission and role queries, direct mutations, override resolution, and staged-save success or conflict UI retain that same exact connection, and an older save completion cannot clear equal-valued changes staged by a replacement connection | third-party permission plugins are integration-status reported, not all boot-verified |
| flags/protection | IMPLEMENTED_VERIFIED | unit verified; Paper policy tests and protection smoke cover LOWEST-priority custom-machine right-click fencing through the independent container permission for ItemsAdder/Oraxen/Nexo/CraftEngine/Slimefun blocks, dedicated normal/glow item-frame add, rotate, and remove changes plus HIGHEST-priority pickup attempt and final entity boundaries, granular interactions, durable role-gated personal flight with external-flight ownership isolation, Core-authoritative newest-intent preference revisions, exact-connection callback fencing, and replacement-safe update tokens, durable per-player border visibility, real blue/green/red border color transitions, block-display preferences, transition refresh, and border ownership isolation, soft-explosion target authorization and non-destructive accounting, CraftEngine furniture build/break enforcement, RoseStacker direct-spawn flag parity, default-compatible natural flags, shard-safe player time/weather overrides, fail-closed dispenser, armor-dispense, origin-island-preserving ground items and merges, hopper, inventory-transfer, and block-projectile boundaries including migrating islands, cancellation-final natural spread, growth, formation, fade, fluid, fire, leaf, bucket, fertilize, structure, and Enderman transitions, dependent block breaks, raids, mob targeting, bounded asynchronous safe returns with same-instance and authorizing-block continuation fencing, and fail-closed player/entity cross-dimension portals inside active island regions | runtime grief/protection scenarios need manual or fixture-backed Paper interaction tests; cross-dimension island worlds remain intentionally unavailable until their lifecycle, storage, and routing are implemented end to end |
| ranking/level/worth/bank/block values | IMPLEMENTED_VERIFIED | verifyRankingWorthCertification and verifyIntegrationRuntimeSmoke cover typed values, authoritative bank-balance ordering with ranking exclusions, ItemsAdder/Oraxen/Nexo/CraftEngine/Slimefun custom block and furniture identity, CraftEngine place/break event deltas, RoseStacker/WildStacker/AdvancedSpawners logical amounts, cause-aware permanent entity removal including external plugin removals, cancellation-final and inheritance-deduplicated block transitions, chunk-complete UUID-deduplicated entity snapshots, bounded scans, serialized writes, and concurrent-mutation rejection; Paper policy tests verify every delayed progression query and level-recalculation result returns only to the exact initiating Player connection instead of a same-UUID replacement | custom and stacker vendor APIs remain deployment-specific live acceptance; busy islands retry reconciliation instead of publishing a mixed-time scan |
| upgrades/size/border/biome | IMPLEMENTED_VERIFIED | verifyUpgradeEffectCoverage covers Core upgrade effects, atomic multi-price charging/refunds, rule-complete GUI views, and biome normalization; one level now preserves and applies concurrent size/team/warp/coop/role limits, crop/spawner/drop multipliers, island effects, normal-world per-material generator rates, arbitrary block limits, and per-entity limits from either CloudIslands or quoted-level SS2 layouts without reducing administrator or mission overrides; Paper enforces both generator upgrade-level and authoritative island-level rule requirements, with fail-closed level loading and event-driven cache refresh, plus exact material and entity-type counts including logical stacker spawns alongside existing aggregate limits; authoritative size is carried through activation, restore, reset, and migration jobs, while live size changes atomically replace Paper protection, scan, and snapshot bounds; Core island response paths expose the independent authoritative BORDER limit, and async border/profile responses return through the Paper scheduler only when the exact initiating Player connection, current island, and latest per-player request revision still match; Paper tests also cover reconnect, island-change, and out-of-order border rejection, region-file cell isolation, unsafe-size fencing, world-border policy, activation-time persisted-biome reconciliation, and chunk-batched biome painting | normal-world per-material generator rates, arbitrary block limits, and per-entity limits are runtime-applied; Nether and End generator-rate maps remain preserved but intentionally inactive until cross-dimension island lifecycle, storage, and routing exist end to end; operator deployment acceptance is still recommended; cells below 1024 blocks or not aligned to 512 blocks fail startup, and islands that cannot fit without sharing region files fail activation or are fenced on unsafe live resize |
| bank/economy/missions/challenges/generators/limits | IMPLEMENTED_VERIFIED | verifyMissionEventProgress covers final uncancelled block, farm, kill, fishing, capacity-bounded bulk crafting, enchanting, statistic, advancement, and item-consumption progress plus the bounded definition cache; gameplay progress delivery carries Core idempotency metadata, while monotonic absolute progress keeps repeated authoritative bank balances and island levels from double-counting or regressing; level recalculation advances the built-in level mission; PostgreSQL and MySQL dual-Core smoke verifies same-key MISSION and CHALLENGE definitions retain independent rows and progress, including fresh and upgraded MySQL schemas, restored mission metadata, and MySQL-safe completion assignment order; reward-settlement tests cover failure reopening, repeatable reset, and durable warehouse item delivery; PostgreSQL/MySQL shared warehouse settlement records move through PREPARED and ESCROWED before Paper replays the exact mutation key, so reconnecting on another Paper node can resume protected deposits and withdrawals; every delayed warehouse stage retains the exact initiating Player instance, and a replacement connection can continue only by replaying the durable PDC/Core settlement instead of inheriting an older inventory callback; `/is deposit *` and `/is withdraw *` resolve authoritative full balances through scheduler-safe Vault and Core queries before reusing the existing idempotent mutation, refund, and rollback paths; delayed balance, target, deposit, withdrawal, rollback, refund, upgrade-purchase, and mission-completion results retain the exact initiating Player connection before Paper feedback; Paper warehouse policy rejects metadata-bearing items that its material-and-amount schema cannot restore, while overflow-safe logical-stack mob-drop scaling, upgrade CAS/refund, generator, and economy safety gates cover the remaining scope | brewing completion has no reliable Bukkit actor and is intentionally not guessed; operator live-server economy/provider acceptance is still recommended |
| chat/logs/reviews | IMPLEMENTED_VERIFIED | verifyReviewModerationCoverage plus current-visible-visitor classification, Core audit/visitor route tests, UUID/island-name/player-name review target resolution, exact-connection fencing for current visitor, public-island, review, and visitor-stat reads plus review writes/deletes, same-island continuation for island-scoped query results, LOWEST/HIGHEST mutually exclusive local/team-chat isolation, MONITOR-only accepted global-spy delivery, scheduler-bound permission and message calls, and same-instance reconnect fencing for both queued dispatch and delayed Core failure feedback cover current workflow | live multi-player chat moderation acceptance is deployment-specific outside unit CI |
| snapshots/rollback/migration/recovery | IMPLEMENTED_VERIFIED | ciIntegrationSmoke verifies recovery restore with shared services; Paper policy tests verify delayed snapshot list/create/restore responses cross the scheduler and retain the exact initiating Player connection instead of a same-UUID replacement, while migration return tickets retain the initiating Player instance across polling, route-session publication, proxy transfer, failure feedback, and delayed BossBar cleanup | releaseClusterSmokeGate now includes database backup, object bundle, manifest checksum, restore, route, and audit evidence |
| Java API/events/addons | IMPLEMENTED_VERIFIED | apiCompatibilityCheck verifies release contract metadata and the public API signature baseline; Paper tests fence asynchronous addon command results to the originating plugin lifecycle and same online Player instance, with scheduler-only delivery and no disable-time completion-thread fallback | external addon certification depends on testkit evidence supplied by the addon |
| integrations/localization/GUI | IMPLEMENTED_VERIFIED | verifyIntegrationRuntimeSmoke verifies executable runtime services including CraftEngine block/furniture and Slimefun block identity plus RoseStacker, WildStacker, and AdvancedSpawners logical amount reconciliation; Paper tests also verify formatting-only MiniMessage rendering with literal dynamic placeholders across branding, GUI, scoreboard, command, title, action-bar, boss-bar, kick, migration, routing, boundary, flag, and protection-notice components, while all revision-guarded GUI loaders and asynchronous join-profile responses reject disconnected or replaced Player instances before mutating presentation or flight state, the async admin node loader reserves its GUI revision before the Core request so older responses cannot replace a newer menu, shared async admin success and failure feedback retains the initiating Player connection, and the shared GUI click boundary rejects null and AIR slots before resolving actions; Paper 26.1.2 smoke proves atomic config reload by applying message changes to already-created renderers and refusing node changes as restart-required without mutating active runtime | Vault, PlaceholderAPI, Plan, vanish, ItemsAdder/Oraxen/Nexo/CraftEngine/Slimefun custom-content, and stacker accounting services are executable; click, URL, insertion, selector, score, and NBT MiniMessage tags stay intentionally disabled as an untrusted-format security boundary; CoreProtect remains append-only and WorldEdit/FAWE remain compatibility-only because CloudIslands chunk bundles own world-state transfer |
<!-- feature-parity:end -->

## Release notes

Current release: `v1.1.263`

Release notes for `v1.1.263`:

- Publishes `CloudIslands-Core-1.1.263.jar` as an executable standalone fat jar
  with the Core runtime and supported JDBC drivers included.
- Removes the unused embedded-Core model, settings, and bundled resources from
  Velocity while preserving its external Core API client, routing, and commands.
- Keeps explicit ADVANCED lobby boot fixtures separate from BASIC folder-based
  topology inference.
- Uses a Gradle-native standalone jar task with merged JDBC ServiceLoader
  descriptors so the full Java 25/Gradle 9 Build gate remains compatible.

- Keeps Satis running without PlaceholderAPI by deferring all PlaceholderAPI
  class loading until the optional dependency is actually present.
- Stops re-saving existing Satis resources, ignores legacy Core API marker and
  zero-port sentinel noise, and preserves real alias conflicts as diagnostics.
- Disables the per-Paper health listener in BASIC mode to avoid same-host port
  collisions, recognizes `island-1`/`islands-1` folders as network Island nodes,
  and supplies localhost Velocity proxy trust defaults.

- Migrates existing plaintext Paper Core and admin tokens into protected,
  cross-platform files without changing their values before Config v2 validation.
- Makes BASIC lobby installs Redis- and S3-optional, resolves Redis/S3 credentials
  only when those integrations are enabled, and auto-detects common lobby folders.
- Coalesces permission-event, island-job, and heartbeat connection failures into
  one outage warning, exponential retry up to 60 seconds, five-minute summaries,
  and one recovery notice.
- Documents every primary Paper and Velocity configuration field inline with its
  purpose, accepted values, and safe local defaults.
- Ships a tested Windows `generate-cloudislands-keys.cmd` that creates 256-bit
  Core, admin, and forwarding secrets without OpenSSL and preserves existing keys.

- Replaces Velocity's Docker-only `/run/secrets` Core API defaults with local,
  cross-platform secret files under the plugin data directory.
- Generates and reuses 256-bit Core and admin tokens with Java `SecureRandom`
  on Windows and Linux without requiring OpenSSL.
- Migrates untouched legacy Docker defaults only when the Docker secret files
  are unavailable, while preserving explicit operator file and environment sources.

- Replaces the Velocity player command dump with a clickable category overview,
  concise quick actions, and a canonical alias-free command list.
- Limits the initial tab-completion response to common commands in the configured
  language while retaining legacy aliases when a player starts typing one.
- Removes blank and case-insensitive duplicate commands before pagination and
  suppresses raw syntax placeholders from completion results.

- Validates the Core, PostgreSQL, MySQL, Redis, MinIO, Lobby Paper, and Velocity
  evidence actually observed by tag CI through a dedicated gate.
- Keeps full production failure-injection certification in
  `releaseClusterSmokeGate`, so observed-only CI evidence cannot be mislabeled
  as complete GA certification.
- Uploads both the combined observed evidence and its explicit missing-coverage
  report from tag Integration runs.

- Adds a Korean operator guide before the full English documentation, covering
  both deployment topologies without reducing the English reference.
- Reorganizes setup, security, backup, recovery, migration, and troubleshooting
  around the order in which operators perform the work.
- Aligns the release-evidence contract test with the Paper agent and lobby-role
  readiness markers required by the current cluster evidence generator.

- Supports a complete single-Paper topology with PostgreSQL, private Core
  Redis, local filesystem island storage, direct-local ticket consumption, and
  a public online-mode Paper server.
- Routes players immediately after island creation and restores inactive island
  snapshots through the same ticket and safe-teleport pipeline.
- Rejoins a gracefully restarted Paper node through `STARTING` instead of
  preserving stale `SHUTTING_DOWN` state.
- Probes storage health asynchronously so S3 outages cannot block the Paper
  watchdog or server thread; active islands stay playable and failed saves retry.
- Persists a last-island marker and recovers stale unloaded-shard logins to the
  configured fallback spawn.
- Honors `redis.enabled: false` in Paper Config v2 and reports single-Paper
  routing and authentication policies accurately in health output.
- Retains exact player-connection fencing across delayed routing, membership,
  permissions, progression, review, inventory, GUI, and operator feedback.
- Keeps the public API compatible with the 1.0.x signature baseline and ships a
  universal Paper artifact for the verified runtime matrix.

## Project status

The current `1.1.263` source baseline is implemented and verified for practical
single-Paper and distributed use. Repository evidence includes unit and policy
tests, real PostgreSQL/Redis/object-storage integration, multi-Core behavior,
Paper and Velocity boot smokes, real player create/home/restore flows, node
restart, storage fault injection, bundle/checksum generation, SBOM, provenance,
and Compose rendering.

Deployment-specific acceptance remains mandatory. Before a public launch,
repeat the smoke paths with the production network, storage provider, database,
permission/economy plugins, custom content, view distance, and expected player
load.

## Critical paths

- `deploy/examples/single-paper/docker-compose.yml`: smallest complete runtime.
- `deploy/compose/docker-compose.yml`: distributed local/host topology.
- `deploy/helm/cloudislands`: Kubernetes chart.
- `gradle/minecraft-versions.toml`: supported Minecraft matrix.
- `build/dist`: release bundle output.
- `cloudislands-paper/src/main/resources/config-v2`: Paper configuration defaults.
- `cloudislands-velocity/src/main/resources/config-v2`: Velocity configuration defaults.
- `cloudislands-core-service/src/main/resources`: Core runtime resources and migrations.

Repository: <https://github.com/M-LunaFarm/islandmc-cloudislands>
