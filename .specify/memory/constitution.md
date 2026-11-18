<!--
Sync Impact Report (최신 개정. 개정 이력 전체는 파일 맨 아래 절들에 누적)
- Version change: 1.1.0 → 1.2.0
- Modified principles: I·II 근거 문구(존재하지 않는 `prompts/PROMPTS.md` 출처 표기 삭제), III 예시
  (2026-11-05 삭제된 "위험도 계산" → "같은 사건 판정") — 규칙 내용은 바뀌지 않음
- Added sections: Core Principles VI(도메인 규칙은 순수 클래스로), VII(맥락 경계)
- Removed sections: 없음
- Templates requiring updates:
  - .specify/templates/plan-template.md ✅ (헌법 검사 절이 범용 게이트 문구라 수정 불필요, 확인만 함)
  - .specify/templates/spec-template.md ✅ (원칙 참조 없음)
  - .specify/templates/tasks-template.md ✅ (원칙 참조 없음)
  - .specify/templates/commands/ — 디렉터리 없음
  - CLAUDE.md ✅ (팀 컨벤션 절에 VI·VII 요약 추가)
  - .claude/agents/code-reviewer.md ✅ (백엔드 체크리스트에 VI·VII 항목 추가)
  - specs/007-disaster-archive/plan.md ✅ (헌법 검사 표·소스 구조에 반영)
  - prompts/PROMPTS.md — 참조 삭제. `.gitignore`(`**/PROMPTS.md`)로 2025-10-05부터 커밋 대상에서
    빠진 로컬 전용 파일이었고 git 이력·디스크 어디에도 없다. 팀 컨벤션의 원본은 이제 CLAUDE.md "팀 컨벤션" 절이다
- Follow-up TODOs: 없음
-->

# Disaster Alert Archive Constitution

## Core Principles

### I. 가독성과 단순성 우선 (Readability & Simplicity First)
메서드는 30~50줄 이내를 지향하며, 필요 이상의 추상화나 불필요한 라이브러리 도입을
금지한다. 세 줄의 비슷한 코드가 섣부른 공통화보다 낫다. 존재하지 않는 미래 요구사항을
가정한 설계(YAGNI 위반)를 만들지 않는다.
**근거**: `CLAUDE.md` "팀 컨벤션" 절에 적힌 팀 규칙이며, 여러 세션에 걸쳐 일관되게
적용되어 왔다.

### II. 계층형 아키텍처 준수 (Layered Architecture)
백엔드는 controller → service → repository 계층을 따르며, 계층을 건너뛰는 직접 호출을
금지한다. 모든 API 응답은 공용 `ApiResponse`/`ApiErrorResponse` 포맷을 사용하고, 예외는
`CustomException` + `ErrorCode`로만 던진다. DTO는 응답 형태별 1개 타입으로 구성하며 리스트는
별도 타입이 아닌 내부 필드로 포함한다. 프론트엔드는 Next.js App Router 구조를 따르고, React
Query 키는 tuple로 구성한다.
**근거**: `CLAUDE.md`에 문서화된 기존 아키텍처 규약이며, 일관성 없는
계층 구조는 유지보수 비용을 급격히 높인다.

### III. 검증 가능한 변경 (Test-Verified Changes)
비즈니스 로직(같은 사건 판정, 쿨다운 판정, 클러스터링 임계값 로직 등)은 외부 의존성(DB, LLM
API)을 목킹한 단위 테스트로 우선 검증한다. DB 경계를 실제로 검증해야 하는 부분은 로컬/CI
모두 동일한 방식(도커 컨테이너로 띄운 Postgres, `.env.test` 설정)으로 통합 테스트한다. 외부
AI API 호출(임베딩 등)은 테스트에서 실제 호출 대신 고정된 픽스처 벡터를 사용해 결정적으로
검증한다. Docker 이미지 빌드 단계(`docker build`)는 네트워크가 격리되어 있으므로 테스트를
실행하지 않으며, 테스트는 항상 빌드 이전의 별도 CI 단계에서 수행한다.
**근거**: 현재 자동화 테스트 커버리지가 낮고(`docs/TEST_CASE.md` 참고), 상당 부분이 DB에
의존하는 통합 테스트라 목킹과 실제 검증을 구분하지 않으면 느리고 불안정한 테스트로 귀결된다.

### IV. 정직한 문서화 (Verified Documentation)
`docs/` 이하의 설계 문서(PRD, TRD, REQUIREMENTS, API_SPEC, DATA_MODEL, COMPONENT_SPEC,
TEST_CASE)는 반드시 실제 코드(엔티티, 컨트롤러, 마이그레이션, 컴포넌트)를 직접 확인한 뒤
작성하며, 확인되지 않은 내용을 추측으로 채우지 않는다. 미구현 기능, 알려진 결함, 고아
코드를 발견하면 숨기지 않고 문서에 이슈로 남긴다.
**근거**: 이번 세션에서 관리자 API 인증 누락, 매핑되지 않은 `embedding` 컬럼, 고아
컴포넌트 등 문서화 과정에서 실제 결함을 다수 발견했다 — 문서가 코드와 어긋나면 그 가치를
잃는다.

### V. 한글 커밋 컨벤션 (Korean Commit Convention)
커밋 메시지와 PR 제목/본문은 항상 한글로, `type(scope): 한글 설명` 형식(Conventional
Commits)을 따른다. `type`은 `feat`/`fix`/`chore`/`docs`/`refactor`/`test`/`ci` 등을 사용하고,
`scope`는 도메인/모듈명을 쓰되 애매하면 생략한다.
**근거**: 기존 커밋 히스토리(`git log`)가 이 컨벤션을 따르고 있으며, 팀이 명시적으로
확인한 규칙이다(`.claude/skills/commit-message`, `.claude/skills/pr-message` 참고).

### VI. 도메인 규칙은 순수 클래스로 (Pure Domain Rules)
판정·계산 규칙(같은 사건 판정, 쿨다운, 표기 추출 등)은 JPA·Spring 을 import 하지 않는
클래스에 둔다(MUST). DB나 외부에서 오는 데이터는 생성자나 메서드 인자로 받는다. service 는
조회·저장·외부 호출의 순서만 조율하고 규칙 자체를 담지 않는다(SHOULD — 규칙이 SQL 안에서
실행돼야 하는 경우, 예: pgvector 거리 검색·목록 필터는 예외이며 그 이유를 주석으로 남긴다).
외부 연동 포트(인터페이스)는 실제 교체가 일어날 때 도입한다. 단, 크롤링처럼 같은 역할의
연동 대상이 둘 이상이 되는 경계에는 처음부터 포트를 둔다. 도메인 모델과 JPA 엔티티를 분리하는
헥사고날 전면 전환은 하지 않는다 — `@Entity` 클래스가 도메인 모델을 겸하고 상태 변경은 엔티티
메서드로 한다(원칙 II).
**근거**: `DisasterCooldown`·`MissingPersonIdentity`·`AnimalIdentity`·`AlertPlaceFacts`(시군구 표를
생성자로 받음)가 이미 이 방식으로 Spring 컨텍스트 없이 단위 테스트된다. 반대로 규칙과 조율이
섞인 `EventClusteringService`(1,067줄, 2026-11-18 기준)가 가장 고치기 어렵다. 도메인 모델/엔티티
분리는 엔티티마다 클래스·변환기를 두 배로 만들지만, 효과가 큰 규칙은 이미 순수 클래스로 떼어
두었고 남은 규칙 다수는 SQL 안에 있어야 해 분리 이득이 작다.

### VII. 맥락 경계 (Context Boundaries)
새 코드는 다른 bounded context(`domain/*` 패키지)의 리포지토리를 직접 주입하지 않고 그 맥락의
service 를 거친다(MUST). 맥락을 넘는 참조는 JPA 객체 연관(`@ManyToOne` 등) 대신 ID 로
한다(MUST). 같은 맥락 안에서는 객체 참조를 허용한다. 기존 위반(2026-11-18 집계: 맥락 간 JPA
연관 18건, 맥락 간 리포지토리 주입 12건)은 그 코드를 만질 때 정리하며 일괄 이전하지 않는다.
**근거**: 크롤러 분리 등 향후 서비스 분리(MSA) 때 끊어야 할 지점은 계층 구조가 아니라 맥락
사이의 직접 결합이다. 모놀리스 안에서 경계를 먼저 지켜 두면 자를 선이 미리 드러나고, 서비스를
나누지 않더라도 손해가 없다(모듈러 모놀리스).

## 기술 스택 제약

- 백엔드: Spring Boot 3.x + JPA/QueryDSL + PostgreSQL(pgvector) + Redis + Flyway. 스키마 변경은
  반드시 새 Flyway 마이그레이션 파일로 관리하며 이미 적용된 파일은 수정하지 않는다.
- 프론트엔드: Next.js(App Router) + TypeScript + TailwindCSS + React Query + Zustand.
- AI/임베딩: Spring AI 경유 OpenAI 정식 API. 임베딩 차원(1536)과
  모델을 변경할 때는 기존 임베딩 데이터 마이그레이션 계획을 함께 세운다.
- 인프라: Docker Compose 기반 로컬/운영 환경. 로컬 개발은 Postgres/Redis만 컨테이너로
  띄우고 백엔드/프론트엔드는 직접 실행하는 것을 기본으로 한다.

## 개발 워크플로우

- 모든 작업은 `develop`에서 분기한 브랜치에서 진행하고, PR의 base는 항상 `develop`으로
  고정한다(`main`으로의 직접 PR은 예외적 승인 없이 만들지 않는다).
- 브랜치명은 성격에 따라 `feature/`, `fix/`, `chore/`, `docs/`, `ci/` 접두사를 사용한다.
- 커밋/PR 생성은 사용자가 명시적으로 요청했을 때만 수행하며, 민감 파일(`.env`,
  `credentials.json`, `firebase-service-account.json` 등)은 절대 커밋하지 않는다.
  - **예외(2026-08-12 사용자 상설 승인)**: `tdd-developer` agent는 RED 단계에서 테스트 작성을 마치면
    되묻지 않고 `commit-message` 스킬로 바로 커밋한다(`.claude/agents/tdd-developer.md` RED 단계 커밋 절).
    테스트 작성은 되돌리기 쉽고 결과물이 명확해 매번 확인을 받는 것이 왕복만 늘리기
    때문이다. 이 예외의 범위는 **커밋까지이며 `git push`에는 적용되지 않는다.** 또한
    스테이징 범위는 그 agent 가 작성한 테스트와 테스트 컴파일에 필요한 최소 스텁으로
    제한되고, 다른 agent(GREEN 단계 구현 등)나 메인 에이전트의 커밋에는 확대되지
    않는다.
- 원격 브랜치는 PR이 머지되면 정리 대상이며, 머지되지 않은 브랜치를 삭제하기 전에는
  반드시 unmerged 커밋 여부를 확인하고 사용자에게 확인받는다.

## Governance

이 헌법은 다른 임시 관행보다 우선한다. 개정은 PR을 통해 제안하고, 변경 사유와 영향받는
템플릿/문서 목록을 Sync Impact Report로 남긴 뒤 병합한다. 버전은 시맨틱 버저닝을 따른다:
MAJOR는 기존 원칙의 하위 호환 불가능한 제거/재정의, MINOR는 원칙 추가나 실질적 확장,
PATCH는 표현 수정이나 오탈자 교정에 해당한다. 코드 리뷰(및 CodeRabbit 자동 리뷰)는 이
원칙들과의 정합성을 확인해야 하며, 원칙을 벗어난 복잡성은 반드시 근거를 문서화해야 한다.
런타임 개발 가이드는 `CLAUDE.md`를 따른다.

**Version**: 1.2.0 | **Ratified**: 2026-07-03 | **Last Amended**: 2026-11-18

---

## Sync Impact Report — 1.0.0 → 1.1.0 (2026-08-12)

**변경 사유**: 사용자가 2026-08-12에 `test-writer` agent의 테스트 커밋을 상설 승인했다.
기존 "커밋은 명시적 요청 시에만" 조항과 정면으로 충돌해, CodeRabbit 자동 리뷰(PR #241)가
이 모순을 지적했다. 사용자 지시가 우선하므로 조항을 되돌리는 대신 헌법에 예외를 명시한다.

**버전**: MINOR — 기존 원칙을 제거·재정의하지 않고 한정된 예외를 추가한 실질적 확장.

**변경 내용**: "개발 워크플로우" 절의 커밋/PR 조항에 `test-writer` 예외를 하위 항목으로 추가.
예외 범위를 커밋까지로 못 박고, push·다른 agent·메인 에이전트로 확대되지 않음을 명시.

**영향받는 문서**:

| 문서 | 상태 |
|---|---|
| `.claude/agents/test-writer.md` | 이미 반영됨 (절차 3, "하지 않는 것") |
| `CLAUDE.md` | 이미 반영됨 (Red-Green 절의 예외 문구) |
| `.claude/skills/commit-message/SKILL.md` | **미반영** — "커밋은 명시적 요청 시에만"이 그대로. test-writer 가 이 스킬을 호출하므로 agent 정의가 우선한다고 명시돼 있으나, 스킬 자체에는 예외가 없다 |
| `.claude/agents/green-implementer.md` | 해당 없음 (커밋 권한 없음, 명시적 요청 필요) |

---

## Sync Impact Report — 1.1.0 → 1.2.0 (2026-11-18)

**변경 사유**: 기록관(`specs/007-disaster-archive`) 착수 전, 사용자가 도메인 규칙 변화와 향후
크롤링·서비스 분리(MSA)에 대비한 구조를 검토했다. 헥사고날 전면 전환 대신 (VI) 규칙을 순수
클래스로 두는 원칙과 (VII) 맥락 경계 원칙을 명문화하기로 2026-11-18 사용자가 승인했다.

**버전**: MINOR — 기존 원칙 I~V 를 제거·재정의하지 않고 원칙 2개를 추가한 실질적 확장.
원칙 II(controller → service → repository)는 그대로 유지한다.

**검토 근거(2026-11-18 집계)**:
- 이미 순수 클래스인 규칙: `DisasterCooldown`, `MissingPersonIdentity`, `AnimalIdentity`, `AlertPlaceFacts`
- 규칙과 조율이 섞인 곳: `EventClusteringService` 1,067줄, `DisasterAlertService` 874줄
- SQL 안의 규칙: `DisasterEventRepository` 네이티브 쿼리 13개(쿨다운 진행 중 판정은 `DisasterEvent.isActive()` 와 SQL 두 곳에 있음)
- 외부 연동 포트: 거의 없음(Spring AI 직접 사용 5곳). 번역 DeepL→OpenAI 교체(`73a9a749`)는 20개 파일·문서 포함
- 맥락 간 결합: JPA 연관 18건, 리포지토리 주입 12건

**함께 정리한 낡은 표기(규칙 내용 불변)**:
- 원칙 I·II 근거의 `prompts/PROMPTS.md` 출처 삭제 — `.gitignore` 의 `**/PROMPTS.md`(2025-10-05 `0d4ea216`)로
  커밋된 적이 없는 로컬 전용 파일이며 git 이력·디스크 어디에도 없다. 출처를 `CLAUDE.md` "팀 컨벤션" 절로 바꿨다.
- 원칙 III 예시의 "위험도 계산"(2026-11-05 기능 삭제)을 "같은 사건 판정"으로 교체.

**기각한 대안**: 헥사고날 전면 전환(도메인 모델/JPA 엔티티 분리, 패키지 재배치) — 엔티티마다
클래스·변환기가 두 배가 되고 헌법 II 의 MAJOR 개정이 필요하며, 맥락 간 결합 30곳은 그대로 남는다.

**영향받는 문서**:

| 문서 | 상태 |
|---|---|
| `.specify/templates/plan-template.md` | 수정 불필요 (헌법 검사 절이 범용 게이트 문구) |
| `.specify/templates/spec-template.md`, `tasks-template.md` | 수정 불필요 (원칙 참조 없음) |
| `CLAUDE.md` | 반영 (팀 컨벤션 절) |
| `.claude/agents/code-reviewer.md` | 반영 (백엔드 체크리스트) |
| `specs/007-disaster-archive/plan.md` | 반영 (헌법 검사 표, 소스 구조) |
