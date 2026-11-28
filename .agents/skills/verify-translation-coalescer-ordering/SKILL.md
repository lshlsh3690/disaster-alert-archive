---
name: verify-translation-coalescer-ordering
description: TranslationRequestCoalescer.coalesce() 가 "완료 후 제거" 순서를 지키는지 TLA+ 모델체커(TLC)로 검증한다. 번역/single-flight 코알레싱 로직(global/translation/TranslationRequestCoalescer.java)의 try/finally 순서를 바꾸는 변경, 또는 동시성 버그가 의심될 때 사용. 테스트로는 좁은 레이스 윈도우를 안정적으로 재현할 수 없는 영역이다.
---

# TranslationRequestCoalescer 순서 보증 검증

## 검증하는 약속

`TranslationRequestCoalescer.coalesce(key, work)`는 같은 키로 동시에 들어온 호출을 하나로 묶어
`work`(번역 API 호출)를 정확히 1회만 실행하고 결과를 공유한다(single-flight). 이 보증이 깨지면
유료 번역 API가 같은 키에 중복 호출된다 — 비용이 바로 새는 지점이고, 레이스 윈도우가 좁아 테스트로
안정적으로 재현하기 어렵다.

이 보증이 구현에서 성립하는 이유는 **순서** 하나다: 실행자(executor)는 `work`를 끝내고
`myFuture.complete(result)`로 future를 먼저 채운 뒤에야(`try` 블록), `finally`에서 맵 항목을
제거한다(`TranslationRequestCoalescer.java:55-70`). 합류자(joiner)는 맵에서 자기 키의 future를 찾으면
무조건 `existing.join()`으로 대기하므로, "맵에 항목이 없다"는 상태를 본 시점엔 이미 future가 완료돼
있어야만 안전하다. 이 순서가 뒤집히면(제거를 먼저 하고 완료를 나중에 하면) 그 틈에 새로 들어온 세
번째 호출이 "진행 중인 작업 없음"으로 오인해 새 실행자가 되어 `work`를 또 실행할 수 있다 — 바로
single-flight 보증이 깨지는 지점.

이 속성을 안전성 불변식으로 모델링했다: `OrderInvariant == (started /\ ~mapPresent) => futureDone`
("시작된 적이 있는데 맵에 항목이 없다면, future는 반드시 완료돼 있어야 한다").

## 왜 TLA+

이건 "두 스레드가 어떤 순서로 단계를 밟을 수 있는가"의 문제이고, 입력값이 아니라 **인터리빙**이
변수다. 모델체커가 이런 문제에 가장 싸게 답을 준다 — 상태 공간이 작아(스레드 2개, 각자 몇 단계뿐)
TLC가 전체 상태 공간을 수 초 안에 완전 탐색한다. 코드 자체를 실행하는 퍼징/스트레스 테스트로는
이 레이스를 안정적으로 때릴 수 없다(좁은 타이밍 윈도우).

## 모델이 코드와 대응하는 지점

`Coalescer.tla`의 PlusCal 알고리즘(파일 상단 주석)은 `coalesce`의 실행자 경로를 그대로 따라간다:

| TLA+ 라벨 | 코드 대응 |
|---|---|
| `Register` | `inFlight.putIfAbsent(key, myFuture)` — 원자적으로 실행자/합류자 결정 |
| `Work` | `work.call()` 실행 |
| `CompleteFirst` → `RemoveAfterComplete` | **현재 코드의 순서**: `myFuture.complete(result)` (try) 후 `inFlight.remove(key, myFuture)` (finally) |
| `RemoveFirst` → `CompleteAfterRemove` | **버그 변형**: 제거를 먼저, 완료를 나중에 — `BuggyOrder = TRUE`일 때만 이 경로를 탐 |
| `Join` | 합류자가 `existing.join()`으로 대기 |

`CONSTANT BuggyOrder`로 두 순서를 토글한다. `Coalescer.cfg`(`BuggyOrder = FALSE`)가 현재 코드의
순서이고, `CoalescerBuggy.cfg`(`BuggyOrder = TRUE`)가 깨진 변형이다.

## 돌리는 법

TLA+ tools(`tla2tools.jar`)가 필요하다 — 저장소에 커밋하지 않았으니
[tlaplus/tlaplus releases](https://github.com/tlaplus/tlaplus/releases)에서 받아 아무 경로에나 둔다.

```bash
cd .agents/skills/verify-translation-coalescer-ordering

# 1) 현재 코드 순서가 안전한지 확인 — 통과해야 한다 (No error has been found)
java -cp <tla2tools.jar 경로> tlc2.TLC -config Coalescer.cfg Coalescer.tla

# 2) 버그 변형이 실제로 잡히는지 확인(체크 자체가 살아있다는 증거) — 반드시 위반이 나와야 한다
java -cp <tla2tools.jar 경로> tlc2.TLC -config CoalescerBuggy.cfg Coalescer.tla
```

PlusCal 알고리즘(파일 상단 `(* --algorithm ... end algorithm; *)` 블록)을 고쳤다면, TLA+ 번역부(`\* BEGIN
TRANSLATION` ~ `\* END TRANSLATION`)를 다시 생성해야 한다:

```bash
java -cp <tla2tools.jar 경로> pcal.trans Coalescer.tla
```

결과를 읽을 때는 statechart 스킬을 쓴다 — 위반이 나오면 반례를 최단 경로로 보여주고,
통과하면 상태 전이 그림을 그려준다(이름만 참조, 정확한 호출 방법·커맨드/옵션은 도구·버전마다
달라지니 이 파일에 베껴 쓰지 않는다 — CLAUDE.md/AGENTS.md의 스킬 호출 방식을 따른다).

## 검증 결과 (2026-11-28 확인)

- `Coalescer.cfg`(`BuggyOrder = FALSE`, 현재 코드 순서): **통과**. 108 states generated, 69 distinct,
  깊이 13까지 전부 탐색, 위반 없음.
- `CoalescerBuggy.cfg`(`BuggyOrder = TRUE`, 버그 변형): **위반 발견**(able to fail 증명). 반례:
  실행자가 `Work` → `RemoveFirst`로 맵에서 먼저 빠지면, `started=TRUE, mapPresent=FALSE,
  futureDone=FALSE`인 상태가 바로 나타나 `OrderInvariant`를 깬다. 이게 바로 "새 실행자가 끼어들
  수 있는 틈"이다.

## 범위와 한계 — 반드시 코드를 다시 봐야 하는 경우

- **스레드 2개만 모델링했고, 3개 이상으로 그냥 일반화할 수 없다.** 처음에는 "3번째 이상의 동시 호출도
  맵 상태만 보면 합류자/새 실행자 둘 중 하나라 2스레드 케이스와 동형(symmetry)"이라고 적었으나, 틀린
  주장이었다 — 이 모델은 future의 완료 여부를 세대(generation) 구분 없이 전역 변수 하나(`futureDone`)로
  표현한다. 실제 코드에서는 합류자가 **등록 시점에 잡은 특정 `CompletableFuture` 레퍼런스**(`existing`)를
  기다리므로, 그 뒤에 새 실행자가 다른 future로 등록해도 이미 완료된 레퍼런스는 영향을 받지 않는다. 하지만
  이 모델을 그대로 3스레드로 확장하면(세대 구분 없는 전역 변수를 그대로 공유) 다음 순서에서 가짜 위반이
  날 수 있다: 1) 스레드 1이 실행자로 등록·완료·제거, 2) 스레드 2가 등록 시 스레드 1의 future를 합류자로
  잡고 대기, 3) 스레드 3이 새 실행자로 등록하며 전역 변수를 다시 `FALSE`로 되돌림 — 이때 스레드 2는 이미
  끝난 future를 들고 있는데도 모델의 전역 변수만 보면 `Safe`가 깨진 것처럼 보인다. 이건 **모델의 단순화가
  만든 인공물이지 실제 코드의 버그가 아니다** — 다만 이 모델은 세대를 구분하지 않으므로 3개 이상이 겹치는
  조합은 **검증되지 않았다**. 필요해지면 future를 세대별 변수(예: `captured[t] \in done`)로 바꿔 다시
  검증해야 한다. `putIfAbsent`/`remove(k, v)`가 `ConcurrentHashMap`이 보장하는 원자적 연산이라는 전제는
  유지하되, 그 원자성 자체도 이 모델이 검증하지 않고 JDK 계약으로 가정했다.
- **예외 경로(`completeExceptionally`)는 별도로 모델링하지 않았다.** 코드 구조상 `catch`(완료)도
  `finally`(제거)보다 먼저 실행되므로 순서는 성공 경로와 동일하지만, 모델이 직접 이를 체크하진
  않는다.
- **liveness(종료 보장)는 범위 밖이다.** 실행자가 `work` 실행 중 죽거나(JVM 크래시 등) future를
  영영 완료시키지 못하면 합류자는 `existing.join()`에서 영원히 블록된다 — 이건 실제로 존재하는
  약점이지만 이 검증이 잡는 속성이 아니다(여기서 검증하는 건 순서/안전성뿐).
- **Java 메모리 모델의 가시성(visibility)은 모델링하지 않았다** — `ConcurrentHashMap`과
  `CompletableFuture`가 happens-before를 보장한다는 JDK 계약을 그대로 가정한다.

## 커버리지 기록 (코드와 보조를 맞추는 용도)

- 검증 대상: `backend/src/main/java/com/disaster/alert/alertapi/global/translation/TranslationRequestCoalescer.java`
  의 `coalesce` 메서드, 특히 55~70행(실행자 경로의 `try`/`finally` 순서)
- 동기화 커밋(sync commit): `9bef40329a8b7d74d90954c5579ee3744a72af73` (이 파일을 마지막으로 바꾼 커밋)
- 이 파일이 저 커밋 이후 바뀌었다면, 특히 `complete`/`remove` 순서나 synchronization 구조가
  바뀌었다면: 위 "모델이 코드와 대응하는 지점" 표가 아직 맞는지 다시 보고, 맞으면 이 sync-commit
  줄을 새 HEAD로만 옮긴다. 순서 자체가 바뀌었다면 `Coalescer.tla`의 `BuggyOrder = FALSE` 쪽
  (`CompleteFirst`/`RemoveAfterComplete`)이 새 순서와 맞는지 고치고, 다시 둘 다(통과/위반) 확인한
  뒤에만 sync-commit을 옮긴다.

유지보수(지도를 최신으로 유지하는 루프)는 maintain-formal-verification-skill 스킬을 참고한다.
