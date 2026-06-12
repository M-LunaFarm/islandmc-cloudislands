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
