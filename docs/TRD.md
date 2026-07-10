# TRD (Technical Requirements Document) — Disaster Alert Archive

> 제품 요구사항은 [PRD.md](./PRD.md) 참고. 이 문서는 PRD의 각 기능을 어떤 기술로, 어떻게 구현/구현할지를 다룬다.

## 1. 시스템 구성

- **클라이언트**: Next.js 15 (App Router) SPA/SSR 하이브리드, Netlify 배포
- **API 서버**: Spring Boot 3.4.4, AWS EC2 위에서 Docker Compose로 운영
- **리버스 프록시**: Caddy — `api.disaster-alert-archive.co.kr` → `backend:8080` 라우팅, Let's Encrypt 인증서 자동 발급/갱신
- **DB**: PostgreSQL + pgvector 확장 (이벤트 임베딩 저장·유사도 검색)
- **캐시**: Redis (인증 코드, 캐시)
- **외부 연동**: 행정안전부 재난문자 API, 기상청 API, DeepL 번역 API, Firebase Cloud Messaging, OpenAI 임베딩/챗 API, Kakao Map API, Google/Kakao/Naver OAuth

시스템 아키텍처 다이어그램: [system_architecture.png](./system_architecture.png)

## 2. 기술 스택

### 2.1 백엔드
| 분류 | 기술 |
|---|---|
| Framework | Spring Boot 3.4.4 |
| ORM | JPA + QueryDSL |
| 마이그레이션 | Flyway (`backend/src/main/resources/db/migration/V*.sql`, `ddl-auto: validate`) |
| 인증 | JWT(access/refresh, httpOnly 쿠키) + OAuth2(Google/Kakao/Naver, 자체 구현) |
| AI | Spring AI — 텍스트 임베딩(1536차원) + LLM(gpt-4o-mini 계열, OpenAI 정식 API 경유) |
| 푸시 | Firebase Admin SDK |
| 문서화 | SpringDoc OpenAPI (Swagger UI) |

### 2.2 프론트엔드
| 분류 | 기술 |
|---|---|
| Framework | Next.js 15 (App Router) |
| 상태 관리 | Zustand |
| 서버 상태 | TanStack Query |
| 폼/검증 | React Hook Form + Zod |
| 스타일 | TailwindCSS 4 |
| PWA/푸시 | next-pwa + Firebase Messaging 서비스워커 |

## 3. 핵심 기술 설계

### 3.1 이벤트 클러스터링 파이프라인
알림(`DisasterAlert`)을 임베딩 기반으로 하나의 `DisasterEvent`로 묶는다. `domain/event/service/`에 구현:

- `EventClusteringService`: 같은 시군구 후보 중 코사인 유사도 ≥ `clustering.similarity-threshold`(기본 0.85)면 병합
- `EventCrossRegionService`: 실종자·탈출 동물처럼 지역을 이동하는 사건을 LLM으로 판정해 교차 지역 병합
- `EventFragmentMergeService`: 30분 주기 스케줄러 — 파편화된 소규모 이벤트를 같은 유형/시도의 이벤트로 다중 메시지 LLM 비교를 통해 재흡수
- `EventLLMDecisionService`: 위 서비스들의 공용 LLM 판정 호출부, 비용 관리를 위해 config 플래그(`llm-fallback.enabled`, `cross-region.enabled` 등)로 on/off
- `DisasterCooldown`: 진행 중 사건의 반복 알림이 중복 푸시를 유발하지 않도록 지역/유형별 쿨다운 관리. 계절성 안전안내 문자는 쿨다운을 갱신하지 않도록 별도 필터링(`incident-specific-check`)

모든 임계값·키워드는 `application.yml`의 `clustering.*`에 있으며, 실데이터 튜닝 근거가 주석으로 남아있다. 대부분 기능은 환경변수 플래그로 기본 비활성화되어 있어, 배포 환경별로 on/off 여부를 확인해야 한다.

### 3.2 위험도 계산
`domain/risk/` — 클러스터링된 이벤트를 `AlertClusteredEvent`(Spring 애플리케이션 이벤트)로 구독해, 유형별 가중치 × 강도 × 시간 감쇠를 반영한 지역별 일일 위험도(`RegionRiskDaily`)를 산출한다. `RiskDecayScheduler`가 주기적으로 점수를 감쇠시키고, 기존 프로파일에 없는 신규 재난 유형은 `LlmRiskProfiler`가 LLM으로 보완한다.

### 3.3 알림(FCM) 발송
- `AlertNotificationService`가 알림의 법정동 코드(및 시/도 전체 등록자를 위해 파생한 시도 코드)로 발송 대상을 조회
- `FcmSendService`는 Firebase Admin SDK로 발송하며, **반드시 data-only 메시지**여야 함 — `notification` 필드가 있으면 Chrome이 자동 표시 + 백그라운드 핸들러 실행을 모두 수행해 중복 알림이 발생하는 것을 과거에 확인함
- 프론트 서비스워커(`firebase-messaging-sw.js`)의 `onBackgroundMessage`는 `showNotification()`을 반드시 `await`해야 함(그렇지 않으면 표시 전에 SW가 종료될 수 있음)

### 3.4 인증
- 이메일 로그인: JWT access/refresh 토큰을 httpOnly 쿠키로 발급. 프론트 axios 인터셉터가 401 응답 시 `/auth/reissue`로 자동 재발급 후 원 요청 재시도
- 소셜 로그인: OAuth2 표준 스타터 대신 `domain/auth/oauth`에 Google/Kakao/Naver 프로바이더를 직접 구현. 프론트가 리다이렉트 URL(`redirect` 쿼리 파라미터)을 명시적으로 넘기며, 백엔드 `oauth.success-redirect`는 이 값이 없을 때만 쓰이는 fallback

### 3.5 실종자 추적
별도의 외부 실종자 API(경찰청 등) 연동 없이, 수집된 재난 안전 문자 중 실종 관련 문자에서 추출한 정보만으로 `MissingPersonIdentity`를 구성해 추적한다. 동일 인물의 재신고는 이름+나이+키 등 신원 정보로 클러스터링(임베딩/지역 기반이 아님).

### 3.6 다국어 번역
DeepL API 기반. 재난 문자(`DisasterEventTranslation`)와 법정동 명칭 번역을 스케줄러로 자동 처리(한/영/중/일).

## 4. 신규 요구사항 기술 설계 — 사용자 제재 (PRD 5.7)

현재 `Member` 엔티티에는 `MemberRole`(`USER`/`ADMIN`) 구분만 있고, 계정 상태(정상/정지) 필드가 없다. 구현을 위해 필요한 변경:

- **DB**: `member` 테이블에 상태 컬럼 추가(예: `status` ENUM `ACTIVE`/`SUSPENDED`, `suspended_until` 등) — Flyway 신규 마이그레이션 필요
- **API**: 관리자 전용 제재 엔드포인트 신설
  - 신고/제보 콘텐츠 숨김 처리 API (커뮤니티 제보·댓글 대상)
  - 회원 정지 처리 API (사유, 기간 기록)
- **인가**: `MemberRole.ADMIN`만 접근 가능하도록 Spring Security 설정 확장 (`SecurityConfig`)
- **런타임 체크**: 정지 상태 회원의 글쓰기 요청을 서비스 레이어에서 차단 (`CommunityService`, `CommentService` 등 작성 로직에 상태 검증 추가)
- **프론트**: 관리자용 제재 화면(신고/제보 목록, 정지 처리 UI), 정지된 사용자에게 노출할 안내 메시지

## 5. 배포 및 CI/CD

- **백엔드**: `develop` 브랜치에 `backend/**` 변경이 푸시되면 GitHub Actions(`backend-deploy.yml`)가 Docker 이미지를 빌드해 Docker Hub에 푸시하고, SSH로 EC2에 접속해 `docker-compose.prod.yml`의 `backend` 서비스만 재배포
- **프론트엔드**: Netlify가 `frontend/` 디렉토리를 빌드(`@netlify/plugin-nextjs`)해 자동 배포
- **DB 마이그레이션**: Flyway가 애플리케이션 기동 시 `db/migration/V*.sql`을 순서대로 적용 (`ddl-auto: validate`이므로 스키마 변경은 반드시 마이그레이션 파일로)

## 6. 비기능 요구사항 대응

| PRD 요구사항 | 기술적 대응 |
|---|---|
| 알림 지연 최소화 | 재난문자 수집 스케줄러 주기 단축, 클러스터링 LLM 호출을 사전 필터(키워드/유형)로 최소화해 처리 지연 감소 |
| 중복 알림 방지 | `DisasterCooldown` + 이벤트 클러스터링으로 동일 사건 재알림 억제 |
| 알림 발송 실패 최소화 | FCM 발송 실패(UNREGISTERED) 토큰 감지 — 자동 정리 로직은 별도 구현 필요(현재 감지만 하고 삭제는 미구현) |
| 번역 품질 | DeepL API 사용, 번역 실패 시 원문 유지 |

## 7. 참고
- [PRD.md](./PRD.md)
- [CLAUDE.md](../CLAUDE.md)
- [README.md](../README.md)
