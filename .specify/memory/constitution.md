<!--
Sync Impact Report
- Version change: (template) → 1.0.0
- Modified principles: N/A (initial ratification)
- Added sections: 전체 신규 작성 (Core Principles I–V, 기술 스택 제약, 개발 워크플로우, Governance)
- Removed sections: 템플릿 placeholder 전체 제거
- Templates requiring updates:
  - .specify/templates/plan-template.md ✅ (Constitution Check 섹션은 범용 게이트 문구라 별도 수정 불필요, 확인만 함)
  - .specify/templates/spec-template.md ✅ (원칙 참조 없음, 수정 불필요)
  - .specify/templates/tasks-template.md ✅ (원칙 참조 없음, 수정 불필요)
- Follow-up TODOs: 없음
-->

# Disaster Alert Archive Constitution

## Core Principles

### I. 가독성과 단순성 우선 (Readability & Simplicity First)
메서드는 30~50줄 이내를 지향하며, 필요 이상의 추상화나 불필요한 라이브러리 도입을
금지한다. 세 줄의 비슷한 코드가 섣부른 공통화보다 낫다. 존재하지 않는 미래 요구사항을
가정한 설계(YAGNI 위반)를 만들지 않는다.
**근거**: 이 원칙은 `prompts/PROMPTS.md`에 명시된 팀 규칙이며, 여러 세션에 걸쳐 일관되게
적용되어 왔다.

### II. 계층형 아키텍처 준수 (Layered Architecture)
백엔드는 controller → service → repository 계층을 따르며, 계층을 건너뛰는 직접 호출을
금지한다. 모든 API 응답은 공용 `ApiResponse`/`ApiErrorResponse` 포맷을 사용하고, 예외는
`CustomException` + `ErrorCode`로만 던진다. DTO는 응답 형태별 1개 타입으로 구성하며 리스트는
별도 타입이 아닌 내부 필드로 포함한다. 프론트엔드는 Next.js App Router 구조를 따르고, React
Query 키는 tuple로 구성한다.
**근거**: `CLAUDE.md`와 `prompts/PROMPTS.md`에 문서화된 기존 아키텍처 규약이며, 일관성 없는
계층 구조는 유지보수 비용을 급격히 높인다.

### III. 검증 가능한 변경 (Test-Verified Changes)
비즈니스 로직(위험도 계산, 쿨다운 판정, 클러스터링 임계값 로직 등)은 외부 의존성(DB, LLM
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
- 원격 브랜치는 PR이 머지되면 정리 대상이며, 머지되지 않은 브랜치를 삭제하기 전에는
  반드시 unmerged 커밋 여부를 확인하고 사용자에게 확인받는다.

## Governance

이 헌법은 다른 임시 관행보다 우선한다. 개정은 PR을 통해 제안하고, 변경 사유와 영향받는
템플릿/문서 목록을 Sync Impact Report로 남긴 뒤 병합한다. 버전은 시맨틱 버저닝을 따른다:
MAJOR는 기존 원칙의 하위 호환 불가능한 제거/재정의, MINOR는 원칙 추가나 실질적 확장,
PATCH는 표현 수정이나 오탈자 교정에 해당한다. 코드 리뷰(및 CodeRabbit 자동 리뷰)는 이
원칙들과의 정합성을 확인해야 하며, 원칙을 벗어난 복잡성은 반드시 근거를 문서화해야 한다.
런타임 개발 가이드는 `CLAUDE.md`를 따른다.

**Version**: 1.0.0 | **Ratified**: 2026-07-03 | **Last Amended**: 2026-07-03
