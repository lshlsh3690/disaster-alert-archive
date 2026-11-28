---
name: verify-disaster-alert-archive
description: 로컬에서 Disaster Alert Archive(재난 안전 문자 아카이브) 웹앱을 실제로 띄우고, 브라우저로 화면을 구동해 동작을 증명한다. 주 surface는 Next.js 프론트엔드(웹 UI)이고, 그 뒤의 Spring Boot REST API도 curl로 직접 찌를 수 있다. "이 변경 실제로 동작하는지 확인해줘", "화면 띄워서 증명해줘" 같은 요청에 사용.
---

# Disaster Alert Archive 검증 스킬

이 저장소는 백엔드(Spring Boot, `backend/`)와 프론트엔드(Next.js, `frontend/`)로 나뉜 웹 서비스다.
사용자가 실제로 만지는 surface는 **프론트엔드 웹 UI**(브라우저)이고, 그 아래 **REST API**(`/api/v1/*`)는
curl로 직접 두드려 서버 쪽만 따로 증명할 수도 있다. 모바일 전용 PWA 푸시(FCM)는 이 스킬의 Drive 범위 밖이다
(실제 모바일 기기 FCM 토큰이 필요해 로컬 브라우저로는 못 드라이브한다 — CLAUDE.md "알림(FCM)" 절 참고).

## Launch

사전 조건: Postgres(pgvector)·Redis는 docker-compose로, 백엔드/프론트는 직접 프로세스로 띄운다
(이 저장소는 로컬 개발에서 `docker-compose.dev.yml`의 `frontend`/`backend` 서비스를 안 쓴다 — `docker` 프로필 전용).

```bash
# 1) DB/Redis (이미 떠 있으면 생략 — `docker ps`로 postgres/redis 컨테이너 확인), 저장소 루트에서 실행
docker compose -f docker-compose.dev.yml up -d postgres redis

# 2) 백엔드 — 터미널 A, 저장소 루트에서 시작. 루트 .env.dev 를 셸에 export 하고 bootRun
#    (gradlew 자체는 .env.dev 를 안 읽는다, .env.test 를 읽는 DotenvExtension 은 테스트 전용)
cd backend
set -a && source ../.env.dev && set +a
./gradlew bootRun
# bootRun 은 포그라운드로 계속 떠 있다 — 이 터미널은 그대로 두고 3)은 반드시 새 터미널에서 실행한다.
# 준비 신호: 로그에 `Started BackendApplication in <N> seconds` (약 40~60초 소요, 법정동 캐시 로딩 포함)

# 3) 프론트엔드 — 터미널 B(새 터미널), 저장소 루트에서 시작. frontend/.env.local 이 이미
#    localhost:8080 + 더미 Firebase 값으로 채워져 있다
cd frontend
npm run dev
# 준비 신호: `Ready in <N>ms` (turbopack)
```

Drive/Evidence 절의 명령은 터미널 A/B가 아니라 **터미널 C(또 다른 새 터미널, 저장소 루트에서 시작)**에서
실행한다 — A/B는 서버를 띄운 채로 둬야 한다.

**빈 데이터 디렉터리에서 "쓸 만한" 상태까지:** Flyway가 스키마 + 법정동 번역 시드(`V*__legal_district*.sql`)를
자동 적용하므로 로그인·지역 선택 등은 빈 DB에서도 바로 동작한다. 하지만 **재난문자 본문(`disaster_alert`)은
시드가 아니라 운영 스케줄러(`DisasterFetchScheduler`)가 실제 공공데이터 API에서 수집해야 쌓인다** — 빈 DB로
막 띄운 인스턴스는 `/alerts` 목록이 당분간 계속 비어 보인다(아래 Gotchas 참고). 이 스킬을 처음 증명한 2026-11-28
런은 **이미 82,554건이 쌓인 장수 개발용 DB**(기존에 떠 있던 `postgres` 컨테이너, 볼륨 `postgres-data`)를 대상으로
했다 — 빈 볼륨으로 처음부터 띄우는 경로는 스키마·마이그레이션 적용까지만 확인했고, 실데이터가 쌓인 뒤의 화면은
확인하지 못했다.

**Teardown:**

```bash
# 포트로 내가 띄운 프로세스만 찾아서 끈다 — 이름으로 죽이지 않는다
powershell -NoProfile -Command "Get-NetTCPConnection -LocalPort 3000 -State Listen | Select -Expand OwningProcess" # 프론트
powershell -NoProfile -Command "Get-NetTCPConnection -LocalPort 8080 -State Listen | Select -Expand OwningProcess" # 백엔드
powershell -NoProfile -Command "Stop-Process -Id <PID> -Force"
# postgres/redis 컨테이너는 이 스킬이 띄운 게 아니라면(이미 떠 있었다면) 끄지 않는다 — 다른 작업이 쓰고 있을 수 있다
```

## Doctor

**`/actuator/health`를 쓰지 마라 — `SecurityConfig`에 `/actuator/**`가 `permitAll`에 없어서 `anyRequest().authenticated()`에
걸려 `401 UNAUTHORIZED`가 돌아온다** (2026-11-28 확인, 제품 코드는 고치지 않음). 대신 공개 엔드포인트로 살아있는지 확인한다:

```bash
curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8080/api/v1/alerts/search?size=1"   # 200 이면 백엔드+DB 정상
curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:3000/alerts"                          # 200 이면 프론트+SSR 정상
```

## Drive

기존 Playwright 하니스 `frontend/scripts/ui-screenshot.mjs`를 그대로 쓴다(ui-checker agent 가 쓰는 것과 동일):

```bash
cd frontend
node scripts/ui-screenshot.mjs /alerts .verify-evidence   # <path>는 localhost:3000 기준 상대경로
```

**Git Bash(Windows) 주의 — 두 가지 함정:**
1. **경로 인자가 `/alerts` 처럼 `/`로 시작하면 MSYS가 이를 Windows 경로로 멋대로 변환**해서
   (`/alerts` → `C:/Program Files/Git/alerts`) 스크립트가 "사용법" 에러를 낸다. `MSYS_NO_PATHCONV=1`을 앞에 붙여라:
   `MSYS_NO_PATHCONV=1 node scripts/ui-screenshot.mjs /alerts .verify-evidence`
2. **dev 서버를 막 띄운 직후 그 라우트의 첫 네비게이션은 `networkidle` 대기가 30초 타임아웃으로 실패할 수 있다**
   (turbopack 최초 컴파일 + dev 전용 연결 때문으로 보임, 2026-11-28 `/alerts`에서 실제로 재현됨). 먼저
   `curl -s -o /dev/null "http://localhost:3000/<path>"`로 그 라우트를 한 번 "데워 놓고" Playwright 스크립트를
   돌리면 통과한다.

로그인이 필요한 화면(`/user/me`, `/notifications` 등)은 OAuth2(Google/Kakao/Naver) 소셜 로그인만 있어 로컬에서
자동화하기 어렵다 — 이 스킬은 **로그인 없이 보이는 공개 화면**(홈/목록/상세/통계)까지만 다룬다. 로그인이 필요한
여정을 드라이브해야 하면 이 Gotcha를 먼저 풀어야 한다(수동 쿠키 주입 또는 테스트 계정 발급 등, 미해결).

## Evidence

- 스크린샷: `frontend/.verify-evidence/<path>__<profile>.png` (6개 프로필: iphone-se/iphone-12/galaxy-s24/tablet-768/desktop-1280/desktop-1920).
  저장소 루트 기준 경로이고, Drive 절처럼 이미 `frontend/`로 `cd`한 상태에서 실행한다면 `.verify-evidence/...`로
  **`frontend/` 접두사 없이** 쓴다(두 번 겹쳐 `frontend/frontend/...`를 찾지 않도록).
- API 응답: 같은 디렉터리에 `curl ... -o .verify-evidence/<name>.json`(이미 `frontend/`에 있을 때 기준, 저장소
  루트에서 실행한다면 `frontend/.verify-evidence/<name>.json`)으로 받아 화면에 보이는 값과 DB/서비스 레이어가
  실제로 반환한 값을 교차 확인한다(화면이 비어 보일 때 "진짜 비었다"와 "API가 깨졌다"를 구분하는 용도).
- `.verify-evidence/`는 gitignore 대상이 아니므로 커밋하지 말고(`git status`로 확인), 다음 런 전에 지워도 된다 — 증빙은
  이 디렉터리 자체가 아니라 "그때 그 스크린샷·JSON"이 목적이므로, 보고할 때는 파일 내용을 인용하고 디렉터리는 치워도 무방하다.

## Cleanup

Launch의 Teardown 절 참고. 정리 후에도 `frontend/.verify-evidence/`의 증빙 파일은 지우지 않는다(증빙 보존 ≠ 프로세스 정리).

## Helpers

이 스킬은 별도 헬퍼 스크립트를 새로 만들지 않는다 — `frontend/scripts/ui-screenshot.mjs`(기존, ui-checker agent 공용)를
그대로 재사용한다.

## 아직 증명 못 한 것

`features/README.md`에 올린 홈/사건 목록/통계 화면은 지도만 그려봤고 실제로 drive+evidence 캡처까지는 안 했다
(2026-11-28 첫 증명 런은 `/alerts` 하나만 끝까지 수행 — 1건이면 하니스가 도는지 증명하기엔 충분하다는 게
create-verification-skill 스킬의 전제). 다음 런에서 이어서 돌리면 된다.
