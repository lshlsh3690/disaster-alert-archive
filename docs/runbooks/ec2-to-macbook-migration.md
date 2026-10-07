# EC2 → 맥북 셀프호스팅 이관 런북

## 배경 / 목적

EC2 서버 비용을 없애기 위해 백엔드(+Postgres+Redis)와 프론트엔드(현재 Vercel)를 모두 사용자의 맥북 위 `docker-compose.prod.yml` 하나로 합치고, 외부 접속은 Cloudflare Tunnel로 연다. **이관 자체(터널/DNS 설정, self-hosted 러너 설치, pg_dump/pg_restore, EC2/Vercel 해지)는 사용자가 직접 실행**한다 — Claude Code 세션은 저장소 파일만 다루고 운영 인프라에 접근할 수 없다.

진행 순서는 **① 사전 준비 → ② 예비 검증(다운타임 없음) → ③ 컷오버(짧은 다운타임) → ④ 사후 검증 → ⑤ 구자원 제거**다. ①~④를 이 문서가 다루고, ⑤는 검증 기간이 끝난 뒤 별도로 진행한다.

## 사전 준비

### 1. 도메인을 Cloudflare로 위임 (아직이면)

Cloudflare 계정에 `disaster-alert-archive.co.kr` 도메인을 추가하고, 등록기관(가비아/후이즈 등) 대시보드에서 네임서버를 Cloudflare가 안내하는 값으로 변경한다. 전파에 수 분~수 시간 걸릴 수 있으니 가장 먼저 시작해둔다. Cloudflare 대시보드에서 도메인이 "Active" 상태가 될 때까지 기다린다.

### 2. 맥북 — Docker Desktop

- Docker Desktop 설치, 설정에서 **"Start Docker Desktop when you log in"** 켜기 — 재부팅 후에도 자동으로 떠 있어야 한다.

### 3. 맥북 — 절전 방지

맥북은 노트북이므로 뚜껑을 닫으면 기본적으로 절전모드에 들어가 서비스 전체가 죽는다. 아래 중 하나를 적용한다:

- 상시 전원 연결 + 시스템 설정 > 배터리(전원 어댑터) > **"디스플레이가 꺼져 있을 때 자동으로 절전 모드 끄기"** 켜기, 또는
- 외부 모니터/키보드·마우스를 연결한 클램셸 모드로 뚜껑을 닫고 운영.

### 4. Cloudflare Tunnel 생성

Cloudflare Zero Trust 대시보드 → Networks → Tunnels → Create a tunnel (Cloudflared) → 이름 지정 후 **Docker** 설치 방식을 선택하면 `TUNNEL_TOKEN` 값이 표시된다. 이 값을 맥북의 `.env`(`CLOUDFLARE_TUNNEL_TOKEN`)에 채운다.

같은 화면의 **Public Hostname** 탭에서 두 개를 등록한다:

| Public hostname | Service |
|---|---|
| `disaster-alert-archive.co.kr` | `http://frontend:3000` |
| `api.disaster-alert-archive.co.kr` | `http://backend:8080` |

컨테이너 이름이 아니라 compose 서비스명(`frontend`/`backend`)을 쓴다 — 같은 docker 네트워크 안에서 서비스명으로 통신한다.

### 5. self-hosted GitHub Actions 러너 설치

저장소 GitHub 페이지 → Settings → Actions → Runners → New self-hosted runner → macOS 선택, 안내되는 명령을 맥북에서 그대로 실행한다(`./config.sh --url ... --token ...`). 마지막에 `./run.sh`로 직접 띄우는 대신 **서비스로 등록**해 로그인 없이도 상주하게 한다:

```bash
cd actions-runner
./svc.sh install
./svc.sh start
```

이건 launchd **LaunchAgent**로 등록되는 것이라, 맥 계정에 로그인된 상태에서만 동작한다 — 잠금 화면(로그인 전)에 멈춰있으면 러너도 멈춘다. 자동 로그인을 켜두거나, 재부팅 후 반드시 다시 로그인해야 한다는 걸 기억해둔다.

이 저장소는 self-hosted 러너에서 `.github/workflows/deploy.yml`이 `push → develop`(경로: `backend/**`, `frontend/**`, `docker-compose.prod.yml`)마다 자동으로 `docker compose build` + `up -d --force-recreate --no-deps backend frontend`를 실행하도록 이미 구성돼 있다 — 러너만 떠 있으면 별도 설정 없이 바로 동작한다.

### 6. `.env` 준비

self-hosted 러너는 `~/actions-runner/_work/<repo>/<repo>/`에 체크아웃하는데, 이 폴더는 배포가 최소 1번 실행돼야 생성된다 — `.env` 없이 첫 배포를 돌리면 그 시점엔 무조건 실패하므로, 미리 수동으로 한 번 클론해서 폴더를 만들어두고 `.env`를 채운 뒤 실제 배포를 트리거하는 순서로 진행한다:

```bash
mkdir -p ~/actions-runner/_work/disaster-alert-archive
cd ~/actions-runner/_work/disaster-alert-archive
git clone https://github.com/lshlsh3690/disaster-alert-archive.git disaster-alert-archive
cd disaster-alert-archive
git checkout develop
cp .env.example .env   # 값 채우기
```

`DB_HOST`/`REDIS_HOST`는 컨테이너 이름이 아니라 **compose 서비스명**(`postgres`/`redis`)으로 둔다. `COOKIE_DOMAIN`, OAuth 리다이렉트 URI 등 도메인 관련 값은 EC2에서 쓰던 값 그대로 옮기면 된다(도메인 자체는 안 바뀜). 이렇게 미리 만들어둔 폴더는 `actions/checkout`이 같은 저장소로 인식해서 그 자리에서 fetch/checkout만 하므로, 다음에 실제 push로 배포가 트리거돼도 이 `.env`는 그대로 남는다(`clean: false` 설정 덕분).

`deploy.yml`의 checkout 스텝은 `clean: false`로 설정돼 있어 매 배포마다 워크스페이스를 청소하지 않는다 — `actions/checkout` 기본값(`clean: true`)은 `git clean -ffdx`를 돌려서 `.gitignore`된 파일(`.env` 포함)까지 지워버리는데, 그러면 최초 1회 채워둔 `.env`가 두 번째 배포 때 사라져 backend/postgres가 기동 실패한다 — 그래서 꺼둔 것이다. 즉 **최초 1회만 사람이 채워두면 이후 배포마다 그대로 유지된다.**

## 예비 검증 (다운타임 없음)

EC2는 계속 운영 중인 상태로 데이터만 스냅샷 떠서 맥북에서 정상 기동되는지 먼저 확인한다.

```bash
# 맥북에서, postgres/redis/cloudflared만 먼저 기동 (backend/frontend는 아직 붙이지 않음)
docker compose -f docker-compose.prod.yml up -d postgres redis cloudflared
docker compose -f docker-compose.prod.yml logs -f postgres   # "database system is ready to accept connections" 확인

# EC2에서 실행 — 운영 DB 덤프 (소스가 EC2의 disaster-postgres 컨테이너)
docker exec disaster-postgres pg_dump -U <POSTGRES_USER> -d <POSTGRES_DB> \
  -Fc --no-owner --no-acl -f /tmp/disaster_alert_$(date +%Y%m%d_%H%M).dump
docker cp disaster-postgres:/tmp/disaster_alert_<타임스탬프>.dump ~/disaster_alert.dump
# scp 등으로 맥북에 전송
scp ubuntu@<EC2_HOST>:~/disaster_alert.dump ./disaster_alert.dump

# 맥북에서 복원
docker cp ./disaster_alert.dump disaster-postgres:/tmp/restore.dump
docker exec disaster-postgres pg_restore -U <POSTGRES_USER> -d <POSTGRES_DB> --no-owner --no-acl /tmp/restore.dump
```

`flyway_schema_history` 테이블이 함께 복원됐는지 반드시 확인한다(없으면 `ddl-auto: validate`인 backend가 기동 실패한다). 복원 후 주요 테이블 행 수를 EC2(원본)와 맥북(복원본)에서 각각 조회해 서로 같은지 비교한다:

```bash
docker exec disaster-postgres psql -U <POSTGRES_USER> -d <POSTGRES_DB> -c "
  SELECT 'disaster_alert', count(*) FROM disaster_alert
  UNION ALL SELECT 'disaster_events', count(*) FROM disaster_events
  UNION ALL SELECT 'region_risk_daily', count(*) FROM region_risk_daily
  UNION ALL SELECT 'member', count(*) FROM member
  UNION ALL SELECT 'flyway_schema_history', count(*) FROM flyway_schema_history;
"
```

이제 self-hosted 러너가 떠 있는 상태에서 `develop`에 아무 변경이나 push 하면(혹은 수동으로 `docker compose -f docker-compose.prod.yml build backend frontend && up -d --no-deps backend frontend`) backend/frontend가 뜬다. `https://<터널 서브도메인 또는 맥북 로컬>`이 아니라, 이 시점에는 Cloudflare Public Hostname이 아직 EC2/Vercel을 가리키고 있으므로 맥북에서 `curl -H "Host: api.disaster-alert-archive.co.kr" http://localhost:8080/...` 식으로 로컬 검증만 한다.

## 컷오버 (짧은 다운타임)

```bash
# 1. EC2에서 backend 정지 — 이 순간부터 신규 알림 수집/발송이 멈춘다
ssh ubuntu@<EC2_HOST> "cd ~/apps/disaster && docker compose -f docker-compose.prod.yml stop backend"

# 2. EC2에서 최종 덤프 (위와 동일한 명령, 새 타임스탬프)
# 3. 맥북으로 전송 후, 맥북 disaster-postgres를 비우고 최종 덤프로 재복원
docker exec disaster-postgres psql -U <USER> -d postgres -c "DROP DATABASE IF EXISTS <DB>; CREATE DATABASE <DB>;"
docker cp ./disaster_alert_final.dump disaster-postgres:/tmp/restore.dump
docker exec disaster-postgres pg_restore -U <USER> -d <DB> --no-owner --no-acl /tmp/restore.dump

# 4. 맥북에서 backend/frontend 기동
docker compose -f docker-compose.prod.yml up -d --force-recreate --no-deps backend frontend
docker compose -f docker-compose.prod.yml logs -f backend   # Flyway "Successfully validated" 확인

# 5. Cloudflare 대시보드에서 Public Hostname 확인 (이미 3번 항목에서 맥북을 가리키도록 설정돼 있음 — 새로 DNS를 바꿀 필요는 없고, 트래픽이 바로 맥북으로 들어오기 시작한다)

# 6. Vercel — Project Settings > Domains 에서 disaster-alert-archive.co.kr 커스텀 도메인 연결 해제
#    (프로젝트 자체는 롤백 대비로 남겨둔다)
```

정지~재기동까지의 다운타임은 덤프/복원 크기에 비례한다 — 예비 검증 단계에서 소요 시간을 미리 가늠해둔다.

## 사후 검증

- 헬스체크/주요 API 응답 확인, 로그인(Google/Kakao/Naver OAuth) 스모크 테스트.
- FCM 알림 발송 확인 (관심지역 알림 1건 테스트).
- 스케줄러(`DisasterFetchScheduler`, `WeatherCollectScheduler`, `RiskDecayScheduler`)가 최소 한 주기 이상 맥북 컨테이너에 정상적으로 데이터를 쓰는지 로그로 확인.
- 맥북을 재부팅해 Docker Desktop + 모든 컨테이너가 자동으로 다시 뜨는지 한 번 실제로 검증해본다 (정전/업데이트 재부팅 대비).
- **이 기간 동안 EC2 인스턴스는 종료하지 않는다** — 문제가 발견되면 Cloudflare Public Hostname을 EC2로, Vercel 도메인 연결을 다시 붙이는 것만으로 즉시 롤백 가능하다(단, 롤백 시점 이후 맥북에서 쌓인 데이터는 EC2에 반영되지 않으므로 유실 감안).

## 부가 기능 — 외부에서 로그 보기 (Dozzle)

EC2 정리 이후 SSH로 로그를 못 보게 되는 문제를 해결하기 위해 `docker-compose.prod.yml`에 `dozzle`(웹 기반 Docker 로그 뷰어, `amir20/dozzle`) 서비스를 추가했다. 호스트 포트는 노출하지 않고(다른 서비스와 동일한 패턴), Cloudflare Tunnel + Access로만 접근 가능하게 한다.

1. Cloudflare Zero Trust → **Access** → **Applications** → **Add an application** → **Self-hosted**
   - Public hostname: `logs.disaster-alert-archive.co.kr`
   - Access policy: `owner-only` — Include 규칙 **Emails** = 본인 이메일만 허용 (인증 없이 공개하면 컨테이너 로그에 담긴 민감정보가 노출되므로 필수)
   - Zero Trust Free 플랜도 카드 등록이 필요함(과금은 안 됨)
2. Tunnel(`disasteralertarchive`) → **Routes** → **+ Add route** → **Published application**
   - Subdomain `logs`, Domain `disaster-alert-archive.co.kr`, Service URL `http://dozzle:8080`
3. 맥북에서 `docker compose -f docker-compose.prod.yml up -d dozzle`

`https://logs.disaster-alert-archive.co.kr` 접속 시 Cloudflare Access 로그인(이메일 인증) → 통과하면 Dozzle 웹 UI에서 모든 컨테이너(backend/frontend/postgres/redis/cloudflared)의 실시간 로그를 볼 수 있다.

## 구자원 제거 (검증 기간 종료 후, 별도 작업)

검증 기간(예: 1~2주) 동안 이상 없으면 진행한다. 이 문서의 범위 밖이며 별도로 진행 시점에 다시 다룬다 — EC2 인스턴스 종료 전 최종 스냅샷을 남겨두는 것을 권장하고, Vercel 프로젝트는 삭제 전 한 번 더 커스텀 도메인이 완전히 해제됐는지 확인한다.
