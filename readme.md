# CloudIslands

CloudIslands는 여러 Paper 노드와 Velocity 프록시 위에서 섬 월드를 나눠 굴리기 위한 서버 플랫폼이다냥. 섬 데이터를 특정 서버에만 묶어 두지 않고, 필요할 때 적당한 노드로 보내고 열고 저장하는 쪽에 초점을 둔다냥.

운영자는 A 서버, B 서버처럼 여러 Paper 서버를 두고도 플레이어에게는 그냥 하나의 섬 서버처럼 보이게 만들 수 있다냥. 뒤에서는 Core가 섬, 노드, 작업, 라우트 티켓을 관리하고 Paper와 Velocity가 각자 맡은 일을 처리한다냥.

## 구성

- `cloudislands-api`: 플러그인과 서비스가 같이 쓰는 공개 모델과 API 타입이다냥
- `cloudislands-common`: 노드 부하, 라우팅, 공통 값 객체를 담는다냥
- `cloudislands-core-client`: Paper/Velocity에서 Core API를 부르는 JDK 기반 클라이언트다냥
- `cloudislands-core-service`: 섬, 노드, 작업, 라우트 티켓, 마이그레이션을 처리하는 Core HTTP 서비스다냥
- `cloudislands-paper`: Paper 노드에서 섬 활성화, 보호, GUI, 관리자 명령을 처리하는 에이전트다냥
- `cloudislands-velocity`: 플레이어 입장과 섬 이동을 실제 서버로 보내는 Velocity 플러그인이다냥

## 주요 기능

- 플레이어 섬 생성, 방문, 홈, 워프, 멤버, 권한, 플래그 관리를 한다냥
- Velocity 기반 섬 라우팅과 RouteTicket 검증을 한다냥
- Paper 노드에서 섬 활성화, 저장, 스냅샷, 복구 흐름을 처리한다냥
- 노드 drain, undrain, sweep, 안전 종료 같은 운영 명령을 제공한다냥
- 관리자용 작업 큐 조회, 재시도, 취소, 복구를 지원한다냥
- 블록 가치, 업그레이드 규칙, 템플릿 관리를 한다냥
- SuperiorSkyblock2 마이그레이션 scan, dryrun, import, verify, rollback 흐름을 제공한다냥

## 기능 설명

### 섬 라우팅

플레이어가 섬으로 이동하면 Velocity가 Core에 라우팅을 요청한다냥. Core는 섬이 어느 노드에 있는지, 새로 활성화해야 하는지, 해당 노드가 지금 받을 수 있는 상태인지 판단한다냥.

이동에 필요한 정보는 RouteTicket으로 내려간다냥. Paper 노드는 이 티켓을 검증한 뒤 플레이어를 실제 섬 위치로 보낸다냥. 라우팅이 실패하면 설정된 fallback 서버로 돌려보낸다냥.

### 섬 활성화와 저장

섬은 항상 모든 서버에 떠 있는 구조가 아니다냥. 필요할 때 Paper 노드가 활성화 작업을 받아 월드나 셀 데이터를 준비하고, 사용이 끝나면 저장 작업으로 상태를 보존한다냥.

이 방식이면 노드별 부하를 줄일 수 있고, 섬을 A 서버에서 B 서버로 옮기는 운영도 가능하다냥.

### 노드 운영

관리자는 노드 목록과 상태를 확인하고, 특정 노드를 drain 상태로 바꿀 수 있다냥. drain된 노드는 새 섬 활성화 대상에서 빠진다냥.

점검 전에는 접속자를 로비로 보내거나 안전 종료 명령을 쓸 수 있다냥. 위험한 명령은 GUI와 명령 흐름에서 한 번 더 확인하도록 잡아 둔다냥.

### 관리자 GUI

Paper 플러그인은 섬 관리용 GUI를 제공한다냥. 생성, 홈, 워프, 멤버, 권한, 은행, 업그레이드, 바이옴, 제한, 스냅샷 같은 자주 쓰는 기능을 메뉴에서 접근할 수 있다냥.

관리자 노드 메뉴는 노드 상태 조회, drain, undrain, sweep, 안전 종료 같은 운영 명령으로 이어진다냥.

### 작업 큐

Core는 섬 활성화, 저장, 복구 같은 작업을 큐로 관리한다냥. 관리자는 작업 목록을 보고 실패한 작업을 재시도하거나 취소할 수 있다냥.

오래 멈춘 작업은 recover 명령으로 다시 회수한다냥. 노드가 죽었다가 돌아오는 상황에서도 작업 상태를 정리하기 위한 흐름이다냥.

### 스냅샷과 복구

섬 스냅샷은 관리자 저장, 수동 백업, 복구 지점 관리에 쓰인다냥. 플레이어나 관리자는 스냅샷 목록을 보고 필요한 시점으로 복원할 수 있다냥.

복원 같은 위험 작업은 GUI에서 실수로 눌리지 않도록 확인 동작을 요구한다냥.

### 블록 가치와 레벨

블록 가치는 섬 레벨 계산과 경제 밸런스에 쓰인다냥. 관리자는 블록별 worth, levelPoints, limit 값을 설정할 수 있다냥.

이 값은 섬 가치 계산과 제한 시스템에서 함께 참조된다냥.

### 템플릿과 업그레이드

섬 생성 템플릿은 Core에서 관리한다냥. 템플릿은 활성화 여부와 최소 노드 버전을 가질 수 있고, 생성 메뉴와 관리자 명령에서 쓰인다냥.

업그레이드 규칙은 섬 크기, 제한, 기능 확장을 단계적으로 관리하는 기준이 된다냥.

### SuperiorSkyblock2 마이그레이션

기존 SuperiorSkyblock2 데이터를 가져오기 위한 마이그레이션 흐름을 제공한다냥. 기본 흐름은 scan, dryrun, import, verify, rollback 순서다냥.

dry-run 별칭도 지원한다냥. 기본 경로는 `plugins/SuperiorSkyblock2`를 기준으로 안내한다냥.

## 운영 흐름

1. Core 서비스가 섬, 플레이어, 노드, 작업 상태를 관리한다냥.
2. Paper 노드는 자신을 Core에 등록하고 섬 활성화 작업을 처리한다냥.
3. Velocity는 플레이어 명령이나 라우트 티켓을 통해 대상 섬의 노드를 찾는다냥.
4. 라우팅이 성공하면 플레이어를 해당 Paper 노드로 이동시킨다냥.
5. 장애나 미응답 상황에서는 fallback 서버로 돌려보내고 관리자 명령으로 상태를 확인한다냥.

## 예상 운영 시나리오

### 1. A 서버에 있던 섬을 B 서버에서 열어야 하는 경우

플레이어가 `/섬 홈`을 입력했는데 해당 섬이 A 서버에 저장되어 있고, 지금 A 서버 부하가 높다고 가정한다냥. Velocity는 Core에 이동을 요청하고, Core는 A 서버 상태와 B 서버 여유 상태를 비교한다냥.

B 서버가 더 낫다고 판단되면 Core는 B 서버에서 섬을 활성화하는 작업을 만든다냥. B 서버의 Paper 에이전트는 작업을 받아 섬 데이터를 준비한다냥.

준비가 끝나면 Core가 RouteTicket을 만들고 Velocity는 플레이어를 B 서버로 보낸다냥. 플레이어 입장 후 Paper는 티켓을 검증하고 섬 위치로 이동시킨다냥.

플레이어는 섬이 어느 노드에서 열렸는지 몰라도 된다냥. 운영자는 `/ciadmin island where <섬>`으로 현재 위치만 보면 된다냥.

### 2. A 서버 점검 전에 섬을 다른 노드로 빼는 경우

A 서버를 점검해야 하면 먼저 `/ciadmin node drain A`를 실행한다냥. drain 상태가 되면 새 섬 활성화 대상에서 A 서버가 제외된다냥.

이미 A 서버에 열려 있는 섬은 저장하거나 다른 노드로 옮긴다냥. 필요하면 `/ciadmin node kickall A maintenance`로 접속자를 로비로 보낸다냥.

이후 `/ciadmin node shutdown-safe A maintenance`로 안전 종료 흐름을 진행한다냥. 점검이 끝나면 `/ciadmin node undrain A`로 다시 라우팅 대상에 넣는다냥.

### 3. B 서버가 갑자기 응답하지 않는 경우

B 서버가 죽거나 응답이 늦어지면 Core의 노드 상태가 나빠진다냥. Velocity 라우팅은 fallback 서버로 플레이어를 돌려보낸다냥.

관리자는 `/ciadmin node list`와 `/ciadmin node info B`로 상태를 확인한다냥. B 서버에 걸려 있던 작업이 남아 있으면 `/ciadmin jobs list`로 확인한다냥.

필요한 작업은 `/ciadmin jobs retry <jobId>` 또는 `/ciadmin jobs cancel <jobId>`로 처리한다냥. 오래 멈춘 작업은 `/ciadmin jobs recover [nodeId] [minIdleMillis] [maxJobs]`로 회수한다냥.

### 4. 섬 데이터 복구가 필요한 경우

플레이어가 섬을 잘못 수정했거나 장애 이후 데이터 확인이 필요하면 스냅샷 목록을 먼저 본다냥. `/ciadmin island snapshots <섬>`으로 복구 가능한 지점을 확인한다냥.

복원이 필요하면 `/ciadmin island restore <섬> <snapshot>`을 실행한다냥. 일반 운영에서는 복원 전에 현재 상태를 한 번 더 저장하는 편이 안전하다냥.

`/ciadmin island snapshot <섬> before-restore`로 복원 전 지점을 남겨 두면 롤백 판단이 쉬워진다냥.

### 5. 기존 SuperiorSkyblock2 서버를 가져오는 경우

마이그레이션은 바로 import부터 하지 않는다냥. 먼저 `/ciadmin migrate-superiorskyblock2 scan plugins/SuperiorSkyblock2`로 원본 데이터를 읽는다냥.

그 다음 `/ciadmin migrate-superiorskyblock2 dryrun plugins/SuperiorSkyblock2`로 실제 반영 전에 문제를 확인한다냥. dryrun 결과가 괜찮으면 import를 실행하고, 이후 verify로 누락이나 불일치를 확인한다냥.

문제가 있으면 rollback 명령으로 되돌린다냥.

## 설정 기준

Core API 주소와 인증 토큰은 Paper/Velocity 설정에서 지정한다냥. 운영 환경에서는 `CI_CORE_TOKEN` 같은 환경 변수를 사용해 토큰을 외부에 노출하지 않는 방식이 기본이다냥.

Velocity의 기본 설정은 `cloudislands-velocity/src/main/resources/config.yaml`에 있다냥. Paper 설정은 플러그인 데이터 폴더에 생성되는 설정 파일을 기준으로 한다냥.

## 명령어 목록

인게임에서는 `/섬 command list [page]`와 `/ciadmin command list [page]`로 페이지를 넘겨 볼 수 있다냥. 목록은 `1 line > 1 command` 기준으로 보여준다냥.

### 플레이어 명령

- `/섬 help [page]`
- `/섬 command list [page]`
- `/섬`
- `/섬 생성`
- `/섬 목록`
- `/섬 내섬`
- `/섬 홈`
- `/섬 홈목록`
- `/섬 셋홈`
- `/섬 방문`
- `/섬 랜덤방문`
- `/섬 공개섬`
- `/섬 초대`
- `/섬 초대목록`
- `/섬 초대수락`
- `/섬 초대거절`
- `/섬 멤버`
- `/섬 추방`
- `/섬 승급`
- `/섬 강등`
- `/섬 양도`
- `/섬 신뢰`
- `/섬 신뢰해제`
- `/섬 밴`
- `/섬 밴해제`
- `/섬 밴목록`
- `/섬 방문자추방`
- `/섬 공개`
- `/섬 비공개`
- `/섬 잠금`
- `/섬 잠금해제`
- `/섬 비행`
- `/섬 인벤보존`
- `/섬 피빕`
- `/섬 공개워프`
- `/섬 설정`
- `/섬 권한`
- `/섬 권한설정`
- `/섬 플래그`
- `/섬 워프`
- `/섬 워프목록`
- `/섬 공개워프목록`
- `/섬 워프설정`
- `/섬 워프삭제`
- `/섬 워프공개`
- `/섬 워프비공개`
- `/섬 레벨`
- `/섬 레벨계산`
- `/섬 가치`
- `/섬 랭킹`
- `/섬 가치랭킹`
- `/섬 업그레이드`
- `/섬 업그레이드목록`
- `/섬 업그레이드구매`
- `/섬 크기`
- `/섬 경계`
- `/섬 바이옴`
- `/섬 은행`
- `/섬 입금`
- `/섬 출금`
- `/섬 미션`
- `/섬 챌린지`
- `/섬 채팅`
- `/섬 팀채팅`
- `/섬 제한`
- `/섬 호퍼`
- `/섬 스포너`
- `/섬 엔티티`
- `/섬 레드스톤`
- `/섬 스냅샷`
- `/섬 스냅샷목록`
- `/섬 복원`
- `/섬 로그`
- `/섬 리셋`
- `/섬 삭제`

### 관리자 명령

- `/ciadmin status`
- `/ciadmin help [page]`
- `/ciadmin command list [page]`
- `/ciadmin island info <island|player>`
- `/ciadmin island where <island>`
- `/ciadmin island tp <island>`
- `/ciadmin island activate <island>`
- `/ciadmin island deactivate <island>`
- `/ciadmin island migrate <island> <node>`
- `/ciadmin island save <island>`
- `/ciadmin island snapshot <island> [reason]`
- `/ciadmin island snapshots <island>`
- `/ciadmin island rollback <island> <snapshot>`
- `/ciadmin island quarantine <island>`
- `/ciadmin island repair <island>`
- `/ciadmin island delete <island>`
- `/ciadmin island restore <island> <snapshot>`
- `/ciadmin player info <player>`
- `/ciadmin player setisland <player> <islandUuid>`
- `/ciadmin player clearisland <player>`
- `/ciadmin node list`
- `/ciadmin node info`
- `/ciadmin node islands <node> [limit]`
- `/ciadmin node drain`
- `/ciadmin node undrain`
- `/ciadmin node kickall`
- `/ciadmin node sweep`
- `/ciadmin node shutdown-safe`
- `/ciadmin jobs list`
- `/ciadmin jobs retry <jobId>`
- `/ciadmin jobs cancel <jobId>`
- `/ciadmin jobs recover [nodeId] [minIdleMillis] [maxJobs]`
- `/ciadmin route debug [all|player]`
- `/ciadmin route ticket <ticket|player>`
- `/ciadmin route clear <player> [ticket]`
- `/ciadmin cache clear`
- `/ciadmin events`
- `/ciadmin audit`
- `/ciadmin metrics`
- `/ciadmin storage`
- `/ciadmin rankings level [limit]`
- `/ciadmin rankings worth [limit]`
- `/ciadmin block-values list`
- `/ciadmin block-values set <materialKey> <worth> <levelPoints> <limit>`
- `/ciadmin upgrade-rules`
- `/ciadmin template list`
- `/ciadmin template upsert <id> <name> [enabled] [minNodeVersion]`
- `/ciadmin template enable <id>`
- `/ciadmin template disable <id>`
- `/ciadmin templates list`
- `/ciadmin templates upsert <id> <name> [enabled] [minNodeVersion]`
- `/ciadmin templates enable <id>`
- `/ciadmin templates disable <id>`
- `/ciadmin reload`
- `/ciadmin migrate-superiorskyblock2 scan`
- `/ciadmin migrate-superiorskyblock2 dryrun`
- `/ciadmin migrate-superiorskyblock2 dry-run`
- `/ciadmin migrate-superiorskyblock2 import`
- `/ciadmin migrate-superiorskyblock2 verify`
- `/ciadmin migrate-superiorskyblock2 rollback`

## 개발 메모

이 저장소는 멀티 모듈 Gradle 프로젝트다냥. 기능을 추가할 때는 Core API, Core Client, Paper, Velocity의 명령/모델이 함께 맞는지 확인해야 한다냥.

특히 관리자 명령은 Paper와 Velocity 양쪽에서 같은 의미로 동작하도록 유지하는 것이 중요하다냥.
