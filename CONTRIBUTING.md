# 기여 가이드

Disaster Alert Archive 에 관심 가져 주셔서 감사합니다. 버그 제보, 문서 수정, 기능 PR 모두 환영합니다.
이 문서는 처음 기여하는 분이 **PR 하나를 머지까지** 가져가는 데 필요한 것만 담았습니다.
프로젝트 소개와 기술 스택은 [README](README.md), 아키텍처와 함정은 [CLAUDE.md](CLAUDE.md) 에 있습니다.

이슈·PR·커밋 메시지는 **한국어나 영어** 어느 쪽으로 써도 됩니다(코드 주석과 기존 문서는 대부분 한국어입니다). English issues and PRs are welcome.

## 시작하기 전에 — 이슈를 먼저 열어 주세요

**모든 PR 은 이슈에서 시작합니다.** 코드를 쓰기 전에 [이슈](https://github.com/lshlsh3690/disaster-alert-archive/issues/new/choose)를 열어 무엇을 왜 바꾸려는지 적어 주세요(버그 제보 / 기능 제안 양식이 있습니다). 메인테이너가 방향을 확인하고 답을 달면 그때 작업을 시작하면 됩니다. 이미 있는 이슈를 맡고 싶다면 그 이슈에 "제가 해 보겠습니다" 댓글을 남겨 주세요.

- 같은 문제를 두 사람이 따로 고치거나, 다 만든 PR 이 방향이 달라 닫히는 일을 막기 위해서입니다.
- 오타 한 글자 같은 아주 작은 수정도 이슈를 열어 주시면 좋지만, 이 경우만은 PR 본문에 이유를 적어 바로 보내셔도 됩니다.
- 특히 아래 영역은 이유가 있어 지금 모양이 된 곳이라, 이슈에서 합의하지 않고 바꾸는 PR 은 머지하지 않습니다.
  - 이벤트 클러스터링 임계값·키워드(`application.yml` 의 `clustering.*`) — 실제 사건 데이터로 튜닝한 값입니다
  - FCM 메시지 형식(data-only), 서비스워커의 `push` 이벤트 처리
  - 이미 삭제한 기능(위험도 점수, 안내성 분리 경로 등) 되살리기 — 삭제 이유는 CLAUDE.md 에 있습니다
- 보안 취약점은 공개 이슈로 올리지 말고 GitHub 의 **Security → Report a vulnerability**(비공개 제보)로 알려 주세요.

## 브랜치와 PR 흐름

1. 이슈를 열고 메인테이너의 답을 받습니다(위 절).
2. 저장소를 fork 합니다.
3. **`develop` 에서** 작업 브랜치를 만듭니다. `main` 은 쓰지 않는 초기 브랜치라 기준으로 삼지 않습니다.
   ```bash
   git checkout -b fix/짧은-설명 origin/develop
   ```
   브랜치 이름은 `feat/`, `fix/`, `docs/`, `refactor/`, `test/`, `chore/` 접두사에 이슈 번호를 붙이는 것을 권장합니다(예: `fix/123-guest-push`).
4. 커밋하고 fork 에 push 한 뒤, **base 를 `develop` 으로** PR 을 엽니다. PR 본문에 `Closes #이슈번호` 를 적어 이슈와 연결해 주세요.
5. CodeRabbit 이 자동으로 리뷰를 남깁니다(한국어). 지적을 반영하거나, 반영하지 않을 이유를 답글로 남겨 주세요. 무료 플랜이라 리뷰가 늦거나 건너뛰어질 수 있습니다.
6. 메인테이너가 리뷰 후 머지합니다.

## 로컬 개발 환경

사전 요구사항: Java 17+, Node.js 20+, Docker.

```bash
# Postgres(pgvector) + Redis 만 컨테이너로
docker compose -f docker-compose.dev.yml up postgres redis

# 백엔드
cp .env.example .env.dev        # 값 채우기
cd backend && ./gradlew bootRun

# 프론트엔드
cd frontend && npm install
cp .env.local.example .env.local
npm run dev                      # http://localhost:3000
```

### 외부 API 키가 없을 때

기여자에게 운영 키를 드리지 않습니다. 키 없이도 대부분의 작업은 가능합니다.

| 키 | 없으면 | 비고 |
|---|---|---|
| `DISASTER_ALERT_SERVICE_KEY` | 재난문자 수집이 안 됨 | [공공데이터포털](https://www.safetydata.go.kr/)에서 개인 키를 무료로 받을 수 있습니다 |
| `OPENAI_API_KEY` | 임베딩·클러스터링·번역이 안 됨 | 호출마다 요금이 듭니다. 단위 테스트에는 필요 없습니다 |
| `firebase-service-account.json` | 푸시 발송이 안 됨(시작 시 에러 로그는 정상) | 환경변수가 아니라 `backend/src/main/resources/` 의 파일입니다 |
| OAuth·메일 키 | 소셜 로그인·메일 발송이 안 됨 | 더미 값이면 됩니다 |

**실제 키나 `.env*` 파일, 서비스 계정 JSON 은 절대 커밋하지 마세요.**

## 코드 컨벤션

### 백엔드 (Spring Boot)

- 도메인별 `controller → service → repository` 계층을 지킵니다. 모든 API 는 `/api/v1/*` 아래입니다.
- 응답은 공용 `ApiResponse`/`ApiErrorResponse` 형식을 씁니다. 요청 DTO 는 `@Valid` 로 검증합니다.
- 예외는 `CustomException` + `ErrorCode` 로 던집니다. 에러 바디를 직접 만들지 않습니다.
- 엔티티 상태 변경은 setter 가 아니라 엔티티 메서드로 합니다.
- 메서드는 대략 30~50줄 안으로 유지하고, 사소한 이유로 새 라이브러리를 들이지 않습니다.
- **DB 스키마는 Flyway 로만 바꿉니다.** 이미 있는 `V*.sql` 은 절대 고치지 말고 새 `V{다음 번호}__설명.sql` 을 추가하세요.
- 로그 레벨: 사람이 조치해야 하는 것만 `error`, 정상 흐름에서 늘 생기는 것(4xx, 죽은 FCM 토큰 등)은 `warn` 이하. 외부 에러 트래킹이 없어 로그 레벨이 유일한 장애 신호입니다.

### 프론트엔드 (Next.js)

- API 호출은 `src/api/*.ts`(도메인별 axios 래퍼) 위에 `lib/queries`·`lib/mutations` React Query 훅으로 쌓습니다.
- React Query 키는 튜플로 구성하고, 특별한 이유가 없으면 낙관적 업데이트는 쓰지 않습니다.
- DTO 필드명은 백엔드와 정확히 일치시킵니다.
- 다국어 문자열은 `constants/i18n` 에 추가합니다(보간 구분자는 `{ }`).
- UI 를 바꿨다면 모바일·데스크톱 스크린샷을 PR 에 첨부해 주세요.

## 테스트

```bash
cd backend  && ./gradlew test    # 실제 OpenAI 를 부르는 @Tag("realApi") 는 제외됨
cd frontend && npm test && npm run lint
```

- **로직을 바꾸거나 추가하면 테스트를 함께 보내 주세요.** 이 프로젝트는 실패하는 테스트를 먼저 쓰고(Red) 통과시키는 구현을 쓰는(Green) 방식을 씁니다. 버그 수정이면 그 버그를 재현하는 테스트가 가장 좋은 설명입니다.
- 외부 의존이 없는 순수 로직은 `@SpringBootTest` 없이 JUnit 단위 테스트로 씁니다.
- DB 가 필요한 테스트는 `@IntegrationTest` 를 붙이고, 로컬 docker Postgres 와 `backend/.env.test` 가 있어야 돕니다.
- `realApiTest`(실제 OpenAI 호출, 유료)는 PR 에서 요구하지 않습니다.
- 경로에 한글이 들어간 폴더에 clone 하면 Windows 에서 `./gradlew test` 가 `ClassNotFoundException` 으로 실패할 수 있습니다. ASCII 경로에 clone 하세요.

### 이벤트 클러스터링을 건드릴 때

같은 사건인지 가르는 기준의 원본은 [`CLUSTERING_CRITERIA.md`](specs/001-event-clustering-pipeline/CLUSTERING_CRITERIA.md) 입니다. 병합·분리 동작을 바꾸는 PR 은 다음을 함께 적어 주세요.

- 바꾸는 이유와 근거가 된 실제 알림 예시(알림 id 와 본문 요지)
- 가능하면 평가 셋으로 잰 전후 점수와 고쳐진·새로 틀린 쌍 수 — 실행 방법은 [`specs/006-clustering-evaluation/quickstart.md`](specs/006-clustering-evaluation/quickstart.md). 재클러스터링은 OpenAI 호출 비용이 들어 필수는 아닙니다. 메인테이너가 대신 잴 수 있습니다

## 커밋 메시지

[Conventional Commits](https://www.conventionalcommits.org/ko/) 형식을 씁니다. 설명은 한글을 권장하지만 영어도 괜찮습니다.

```
type(scope): 왜 바꿨는지 한 문장

(버그 수정이나 이유가 바로 안 보이는 변경이면) 무엇이 문제였고 왜 이 방식으로 고쳤는지.
```

- `type`: `feat` 기능, `fix` 버그, `docs` 문서, `refactor`, `test`, `chore`, `ci`
- `scope`: 도메인이나 모듈(`event`, `notification`, `auth`, `frontend` 등). 애매하면 생략합니다
- 관련 없는 변경은 커밋을 나눠 주세요(리뷰어가 "이게 왜 같이 있지?"를 묻지 않게)
- 예: `fix(notification): 시도 전체를 관심 지역으로 둔 게스트에게 푸시가 안 가던 문제를 고친다`

## PR 작성

PR 을 열면 템플릿이 채워집니다. 제목은 커밋과 같은 형식(`type(scope): 설명`)으로 쓰고, 본문에는 다음을 적어 주세요.

- **무엇을, 왜** 바꿨는지
- **어떻게 확인했는지** — 돌린 테스트, 직접 확인한 화면이나 API 응답
- 연결된 이슈 번호(`Closes #123`) — 이슈 없는 PR 은 이슈를 먼저 열어 달라는 답을 받게 됩니다

PR 은 하나의 목적에 집중해 주세요. 리팩터링과 기능 추가를 섞으면 리뷰가 오래 걸립니다.

## 데이터에 대해

재난문자 원문은 행정안전부 공공데이터(Open API)로 공개된 데이터입니다. 테스트나 평가 데이터에 원문을 쓰는 것은 괜찮지만, 그 밖의 개인정보(회원 이메일, FCM 토큰 등)는 이슈·PR·테스트 데이터에 넣지 마세요.

## 라이선스

기여한 코드는 이 저장소의 [MIT License](LICENSE) 로 배포되는 데 동의한 것으로 봅니다.
