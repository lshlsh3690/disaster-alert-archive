# 번역 요청 병합(single-flight) 전후 측정

같은 재난문자를 같은 언어로 동시에 열었을 때 외부 번역 API(OpenAI)가 몇 번 불리는지를 재고,
`TranslationRequestCoalescer` 도입 전후를 비교하는 문서다. 구현은 PR #317, 설계 배경은
`specs/005-translation-pipeline/spec.md` 예외 상황 절과
`C:\lshlsh3690\TIL\개발일지\DisasterAlertArchive\번역캐시_동시성_PK충돌_UPSERT_원인분석.md` 를 본다.

## 무엇을 재는가

지표는 둘이다. **레이턴시는 쓰지 않는다**(아래 "왜 레이턴시가 아닌가").

### 지표 1 — 외부 번역 API 호출 횟수

같은 키로 동시 요청 N건을 보냈을 때 OpenAI 가 몇 번 불리는가. 곧 **요금과 쿼터**다.

### 지표 2 — 번역 중 DB 커넥션 점유 (가설이 틀렸다)

코얼레서를 넣으면서 `@Transactional` 을 호출부까지 걷어냈다.

| | `getAlertDetail` | `ensureTranslated` | `translateAndSaveAsync` |
|---|---|---|---|
| 전 (`534fb44`) | `@Transactional` | `@Transactional` | `@Transactional` |
| 후 (`develop`) | 없음 | 없음 | 없음 |

**가설**: 그 전에는 OpenAI 응답을 기다리는 동안 DB 커넥션을 쥐고 있었으니, 걷어내면 점유가 줄
것이다. 동시 요청 N건이면 커넥션 N개가 그동안 묶이고 풀(기본 10)을 넘으면 무관한 조회까지
대기하므로, 처리량 개선으로 이어질 것이다.

**측정 결과 차이가 없었다.** 전후 모두 점유 면적 6.40 커넥션·초로 같았다.

**이유는 OSIV(Open Session In View)다.** `spring.jpa.open-in-view` 를 설정한 적이 없어 Spring Boot
기본값인 `true` 가 적용된다(기동 로그에 경고가 찍힌다). 그러면 `@Transactional` 유무와 무관하게
**EntityManager 와 그 커넥션이 HTTP 요청 전체 동안 열려 있다.** 트랜잭션 경계를 걷어낸 것은
PK 충돌이 `try/catch` 안에서 잡히게 만드는 **정확성** 수정이었지, 커넥션 점유를 줄이는 수정이
아니었다.

> 커넥션 점유를 실제로 줄이려면 `spring.jpa.open-in-view: false` 를 함께 꺼야 한다. 이번 범위
> 밖이고, 끄면 지연 로딩 접근이 있는 다른 경로가 깨질 수 있어 따로 검토해야 한다.

이 지표는 **개선 수치로 쓸 수 없다.** 측정해서 아니라는 것을 확인했다는 사실만 남긴다.

### 왜 레이턴시가 아닌가

병합이 없어도 동시에 들어온 N개 요청이 **각자 동시에** 번역을 호출한다. 호출은 N번이지만 서로
병렬이라, 각 요청이 기다리는 시간은 번역 한 번 분량이다. 병합 후에는 1번만 부르고 나머지가 그
결과를 기다리는데, 역시 번역 한 번 분량을 기다린다.

```
전: 요청 10개 → 번역 API 10번(동시) → 각 요청 대기 ~번역 1회분
후: 요청 10개 → 번역 API 1번        → 각 요청 대기 ~번역 1회분
```

기다리는 시간은 같고 **부른 횟수만 줄어든다**. 레이턴시를 성과로 쓰면 사실과 다르다. 캐시 적중과
미스의 차이(229ms 대 1.47초)도 이 기능의 전후가 아니므로 쓰지 않는다.

> 레이턴시가 실제로 좋아지는 경우도 있다. OpenAI 가 속도 제한을 걸거나 커넥션 풀이 모자랄 만큼
> 동시 요청이 많을 때다. 지표 2 가 그 조건이 언제 오는지를 보여 준다 — 현재 트래픽은 그 지점에
> 닿지 않으므로, 포트폴리오에는 "동시 요청 조건에서" 라는 단서를 붙여 쓴다.

## 비교 대상

| | 커밋 | 상태 |
|---|---|---|
| 전 | `534fb44` | upsert 제거됨, 코얼레서 배선 없음 (check-then-act + `save()`) |
| 후 | `develop` (`b83d9a3` 이후) | 코얼레서 + 실행자 안쪽 캐시 재확인 |

양쪽 다 저장이 `save()` 라, 둘의 차이는 **코얼레서 하나뿐**이다. 분기 시점의 develop(UPSERT 보유)을
"전"으로 잡으면 저장 방식과 코얼레서 두 가지가 동시에 바뀌어 원인이 섞인다.

> **"전" 상태의 HTTP 500 을 개선 수치로 쓰지 말 것.** 이 상태는 동시 요청에서 500 이 날 수 있지만,
> **운영에 배포돼 있던 코드는 UPSERT 가 있어 500 이 나지 않았다.** 그 500 은 이 작업 과정에서만
> 존재한 중간 상태의 것이다. 이 비교의 목적은 호출 횟수 하나를 격리하는 것이다.

## 측정이 성립하기 위한 두 조건

**1. 번역 캐시가 비어 있어야 한다.** `ensureTranslated` 는 맨 앞에서 캐시를 확인하고 있으면 바로
돌아간다. 번역이 이미 저장돼 있으면 전후 둘 다 호출 0 회가 나와 차이가 보이지 않는다. **회차마다**
지운다.

**2. 요청이 실제로 겹쳐야 한다.** 겹치지 않으면 "전" 상태에서도 2번째 요청이 1번째가 저장한 번역을
보고 그냥 돌아가, 호출이 1 회로 나온다. 병합이 없는데 있는 것처럼 보인다. mock 의 `DELAY_MS`(기본
300ms)가 요청들이 출발하는 시간 간격보다 충분히 길어야 한다.

결과를 읽을 때 이것으로 검증한다.

- "전"에서 호출이 요청 수에 가깝게 나오면 제대로 겹친 것이다
- "전"인데 1~2 회가 나왔다면 겹치지 않은 것이니 `DELAY_MS` 를 늘려 다시 돌린다

대상 알림은 **`disaster_type` 이 비어 있는 것**을 고른다. 유형이 있으면 `translateAndSaveInternal`
이 본문과 유형을 각각 번역해 호출이 2배로 찍힌다(전후 비율은 같지만 숫자 읽기가 헷갈린다).

## 준비

```bash
# 1) OpenAI mock — 호출 수를 세고 지연을 준다
node backend/loadtest/mock/openai-mock.mjs          # PORT=4000 DELAY_MS=300 기본

# 2) 백엔드 — base-url 을 mock 으로 덮어쓴다. 프로필 없음(스케줄러 비활성)
cd backend && ./gradlew bootRun --args='--spring.ai.openai.base-url=http://localhost:4000 --spring.ai.openai.api-key=mock'

# 3) actuator 접근용 로컬 계정 (지표 2 에 필요. 한 번만)
curl -s -X POST http://localhost:18080/api/v1/auth/signup -H "Content-Type: application/json" \
  -d '{"email":"perftest@local.test","password":"perftest1234","confirmPassword":"perftest1234","nickname":"perftest"}'
```

`/actuator/**` 는 `SecurityConfig` 의 `anyRequest().authenticated()` 에 걸려 인증이 필요하다.
샘플러가 이 계정으로 로그인해 httpOnly 쿠키를 받아 들고 폴링한다.

"전"을 잴 때는 한글 없는 경로에 worktree 를 만들어 거기서 기동한다.

```bash
git worktree add /c/tmp/wt-before 534fb44
```

"전" 코드에는 코얼레서 메트릭(`translation.coalesce.*`)이 아예 없다. 샘플러의 그 줄이 0 으로
나오는 것이 정상이고, 커넥션 지표는 양쪽 다 나온다.

## 회차마다

```bash
# 1) 캐시를 비운다 (위 조건 1)
docker exec postgres psql -U $POSTGRES_USER -d $POSTGRES_DB -c \
  "DELETE FROM disaster_alert_translation WHERE disaster_alert_id = <ALERT_ID> AND language_code = 'JA';"

# 2) 샘플러를 먼저 띄운다 (지표 2). 터미널을 하나 더 쓴다
node backend/loadtest/sampler/hikari-sampler.mjs    # INTERVAL_MS=100 DURATION_S=30 기본

# 3) 샘플러가 "지금 k6 를 실행한다" 를 찍으면 k6 를 돌린다 (지표 1)
k6 run --env ALERT_ID=<ALERT_ID> --env VUS=10 backend/loadtest/k6/translation-coalescing.js
```

k6 의 `teardown` 이 mock 의 `/__stats` 를 읽어 그 회차의 번역 API 호출 수를 출력한다. `setup` 에서
`/__reset` 을 치므로 회차끼리 섞이지 않는다. 샘플러는 `DURATION_S` 가 끝나면 커넥션 점유 요약과
코얼레서 카운터 증가분을 찍는다. 같은 조건으로 **3회씩** 돌려 중앙값을 쓴다.

## 포트

로컬 5432 를 다른 프로젝트 컨테이너가 점유 중이면 먼저 정리한다. 백엔드 포트가 8080 이 아니면
`--env BASE=http://localhost:<port>` 로 넘긴다(스크립트 기본값은 18080).

## 측정 결과

2026-11-05 측정. 동시 요청 10건, 캐시 미스, mock 지연 400ms, 3회 중앙값. 알림 `700000001`
(`disaster_type` 없음 → 본문 1회만 번역).

| 조건 | 번역 API 호출 | HTTP 500 | 커넥션 점유 면적 |
|---|---|---|---|
| 전 (`534fb44`) | **10회** | **4 / 10건** | 6.40 커넥션·초 |
| 후 (`develop`) | **1회** | **0 / 10건** | 6.40 커넥션·초 |

3회 모두 같은 값이 나왔다. "후" 의 점유 면적만 5.60 / 6.40 / 7.20 으로 흔들려 중앙값을 썼다.

**요청이 실제로 겹쳤다는 근거**: "후" 3회 모두 코얼레서 카운터가 실행자 1 / 합류자 9 였다.
10건 중 1건만 번역하고 9건이 그 결과를 공유했다는 뜻이다.

**"전" 의 500 은 PK 충돌이 맞다.** 백엔드 로그에
`duplicate key value violates unique constraint "pk_disaster_alert_translation"` 이 찍혔다.
2026-08 에 보고된 결함과 같은 증상이다.

### 이 수치를 어떻게 쓰나

**쓸 수 있는 것은 번역 API 호출 10회 → 1회 하나다.**

쓰면 안 되는 것이 셋이다.

1. **커넥션 점유** — 전후가 같았다(위 지표 2).
2. **레이턴시** — 측정하지 않았고, 구조상 차이가 나지 않는다.
3. **HTTP 500 4건 → 0건** — 기준점인 `534fb44` 는 upsert 를 되돌린 **작업 중간 상태**다.
   운영에 배포돼 있던 코드에는 upsert 가 있어 이 500 이 나지 않았다. 이걸 "개선 전 운영 장애"로
   쓰면 자기 중간 상태를 기준점에 넣는 셈이다. 500 은 "코얼레서가 upsert 없이도 충돌을 막는다"는
   **동작 확인**으로만 쓴다.

조건도 반드시 붙인다. 현재 운영 트래픽은 하루 10~20건이고 수집 스케줄러가 미리 번역해 두므로
**실제로 중복 호출이 일어나는 빈도는 낮다.** "요금 90% 절감" 은 과장이고,
"동시 요청 10건 조건에서 외부 번역 API 호출이 10회에서 1회로" 처럼 조건부로 쓴다.

## 관련 파일

| 파일 | 역할 |
|---|---|
| `backend/loadtest/mock/openai-mock.mjs` | OpenAI mock. 호출 수 집계(`/__stats`), 초기화(`/__reset`), 지연 |
| `backend/loadtest/k6/translation-coalescing.js` | 동시 요청 발생과 호출 수 보고 (지표 1) |
| `backend/loadtest/sampler/hikari-sampler.mjs` | 커넥션 점유·코얼레서 카운터 샘플링 (지표 2) |
| `global/translation/TranslationRequestCoalescer.java` | 측정 대상 구현 |
