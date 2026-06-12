# CloudIslands

CloudIslands는 여러 Paper 노드와 Velocity 프록시 위에서 섬 월드를 분산 운영하기 위한 서버 플랫폼이다. 목표는 섬 데이터를 특정 서버에 묶어 두지 않고, 필요한 시점에 적절한 노드로 라우팅하고 활성화하며, 관리자가 노드와 섬 상태를 일관된 명령으로 다룰 수 있게 하는 것이다.

## 구성

- `cloudislands-api`: 플러그인과 서비스가 공유하는 공개 모델과 API 타입
- `cloudislands-common`: 노드 부하, 라우팅, 공통 값 객체
- `cloudislands-core-client`: Paper/Velocity에서 Core API를 호출하는 JDK 기반 클라이언트
- `cloudislands-core-service`: 섬, 노드, 작업, 라우트 티켓, 마이그레이션을 처리하는 Core HTTP 서비스
- `cloudislands-paper`: Paper 노드에서 섬 활성화, 보호, GUI, 관리자 명령을 처리하는 에이전트
- `cloudislands-velocity`: 플레이어 입장과 섬 이동을 실제 서버로 라우팅하는 Velocity 플러그인

## 주요 기능

- 플레이어 섬 생성, 방문, 홈, 워프, 멤버, 권한, 플래그 관리
- Velocity 기반 섬 라우팅과 RouteTicket 검증
- Paper 노드의 섬 활성화, 저장, 스냅샷, 복구 흐름
- 노드 drain, undrain, sweep, 안전 종료 같은 운영 명령
- 관리자용 작업 큐 조회, 재시도, 취소, 복구
- 블록 가치, 업그레이드 규칙, 템플릿 관리
- SuperiorSkyblock2 마이그레이션 scan, dryrun, import, verify, rollback

## 기능 설명

### 섬 라우팅

플레이어가 섬으로 이동하면 Velocity가 Core에 라우팅을 요청한다. Core는 섬이 어느 노드에 있는지, 활성화가 필요한지, 노드 상태가 이동 가능한지 판단한다. 이동에 필요한 정보는 RouteTicket으로 내려가며, Paper 노드는 이 티켓을 검증한 뒤 플레이어를 실제 섬 위치로 보낸다.

라우팅 실패 시에는 지정된 fallback 서버로 이동한다. 노드 이름은 설정에 따라 플레이어에게 숨길 수 있다.

### 섬 활성화와 저장

섬은 항상 모든 서버에 떠 있는 구조가 아니다. 필요할 때 Paper 노드가 활성화 작업을 받아 월드나 셀 데이터를 준비하고, 사용이 끝나면 저장 작업을 통해 상태를 보존한다. 이를 통해 노드별 부하를 줄이고 섬을 다른 노드로 옮길 수 있다.

### 노드 운영

관리자는 노드 목록과 상태를 확인하고, 특정 노드를 drain 상태로 전환할 수 있다. drain된 노드는 새 섬 활성화 대상에서 제외된다. 점검 전에는 플레이어를 로비로 보내거나 안전 종료 명령을 사용할 수 있다.

노드 관련 위험 명령은 메뉴와 명령에서 별도로 확인하도록 구성되어 있다.

### 관리자 GUI

Paper 플러그인은 섬 관리용 GUI를 제공한다. 생성, 홈, 워프, 멤버, 권한, 은행, 업그레이드, 바이옴, 제한, 스냅샷 같은 자주 쓰는 기능을 메뉴에서 접근할 수 있다. 관리자 노드 메뉴는 노드 상태 조회, drain, undrain, sweep, 안전 종료 같은 운영 명령으로 연결된다.

### 작업 큐

Core는 섬 활성화, 저장, 복구 같은 작업을 큐로 관리한다. 관리자는 작업 목록을 조회하고 실패한 작업을 재시도하거나 취소할 수 있다. 오래 멈춘 작업은 recover 명령으로 다시 회수할 수 있다.

### 스냅샷과 복구

섬 스냅샷은 관리자 저장, 수동 백업, 복구 지점 관리에 사용된다. 플레이어나 관리자는 스냅샷 목록을 확인하고 필요한 시점으로 복원할 수 있다. 복원 같은 위험 작업은 GUI에서 실수로 실행되지 않도록 확인 동작을 요구한다.

### 블록 가치와 레벨

블록 가치는 섬 레벨 계산과 경제 밸런스에 사용된다. 관리자는 블록별 worth, levelPoints, limit 값을 설정할 수 있다. 이 값은 섬 가치 계산과 제한 시스템에서 함께 참조된다.

### 템플릿과 업그레이드

섬 생성 템플릿은 Core에서 관리된다. 템플릿은 활성화 여부와 최소 노드 버전을 가질 수 있으며, 생성 메뉴와 관리자 명령에서 사용된다. 업그레이드 규칙은 섬 크기, 제한, 기능 확장을 단계적으로 관리하는 기준이 된다.

### SuperiorSkyblock2 마이그레이션

기존 SuperiorSkyblock2 데이터를 가져오기 위한 마이그레이션 흐름을 제공한다. 기본 흐름은 scan, dryrun, import, verify, rollback 순서다. dry-run 별칭도 지원한다. 기본 경로는 `plugins/SuperiorSkyblock2`를 기준으로 안내된다.

## 운영 흐름

1. Core 서비스가 섬, 플레이어, 노드, 작업 상태를 관리한다.
2. Paper 노드는 자신을 Core에 등록하고 섬 활성화 작업을 처리한다.
3. Velocity는 플레이어 명령이나 라우트 티켓을 통해 대상 섬의 노드를 찾는다.
4. 라우팅이 성공하면 플레이어를 해당 Paper 노드로 이동시킨다.
5. 장애나 미응답 상황에서는 fallback 서버로 돌려보내고 관리자 명령으로 상태를 확인한다.

## 설정 기준

Core API 주소와 인증 토큰은 Paper/Velocity 설정에서 지정한다. 운영 환경에서는 `CI_CORE_TOKEN` 같은 환경 변수를 사용해 토큰을 외부에 노출하지 않는 방식이 기본이다.

Velocity의 기본 설정은 `cloudislands-velocity/src/main/resources/config.yaml`에 있다. Paper 설정은 플러그인 데이터 폴더에 생성되는 설정 파일을 기준으로 한다.

## 관리자 명령

주요 관리자 명령은 `/ciadmin` 아래에 모여 있다.

- `/ciadmin status`
- `/ciadmin node menu|list|info|islands|drain|undrain|sweep|kickall|shutdown-safe`
- `/ciadmin island info|where|tp|activate|deactivate|migrate|save|snapshot|snapshots|restore|rollback|quarantine|repair|delete`
- `/ciadmin player info <player>|setisland <player> <islandUuid>|clearisland <player>`
- `/ciadmin jobs list|retry <jobId>|cancel <jobId>|recover [nodeId] [minIdleMillis] [maxJobs]`
- `/ciadmin route debug [all|player]|ticket <ticket|player>|clear <player> [ticket]`
- `/ciadmin block-values list|set <materialKey> <worth> <levelPoints> <limit>`
- `/ciadmin template|templates list|upsert|enable|disable`
- `/ciadmin migrate-superiorskyblock2 scan|dryrun|dry-run|import|verify|rollback [path]`

## 개발 메모

이 저장소는 멀티 모듈 Gradle 프로젝트다. 기능을 추가할 때는 Core API, Core Client, Paper, Velocity의 명령/모델이 함께 맞는지 확인해야 한다. 특히 관리자 명령은 Paper와 Velocity 양쪽에서 같은 의미로 동작하도록 유지하는 것이 중요하다.
