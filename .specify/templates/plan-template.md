# 구현 계획: [FEATURE]

**브랜치**: `[###-feature-name]` | **날짜**: [DATE] | **명세**: [link]

**입력**: `/specs/[###-feature-name]/spec.md`의 기능 명세

**참고**: 이 템플릿은 `/speckit-plan` 명령이 채워 넣습니다. 실행 워크플로는 `.specify/templates/plan-template.md`를 참고하세요.

## 요약

[기능 명세에서 추출: 핵심 요구사항 + research 단계에서 도출한 기술적 접근]

## 기술 컨텍스트

<!--
  액션 필요: 이 섹션의 내용을 프로젝트에 맞는 실제 기술 세부사항으로 교체하세요.
  아래 구조는 반복 작업을 안내하기 위한 참고용입니다.
-->

**언어/버전**: [예: Python 3.11, Swift 5.9, Rust 1.75 또는 NEEDS CLARIFICATION]

**주요 의존성**: [예: FastAPI, UIKit, LLVM 또는 NEEDS CLARIFICATION]

**저장소**: [해당 시, 예: PostgreSQL, CoreData, files 또는 N/A]

**테스트**: [예: pytest, XCTest, cargo test 또는 NEEDS CLARIFICATION]

**대상 플랫폼**: [예: Linux server, iOS 15+, WASM 또는 NEEDS CLARIFICATION]

**프로젝트 유형**: [예: library/cli/web-service/mobile-app/compiler/desktop-app 또는 NEEDS CLARIFICATION]

**성능 목표**: [도메인별, 예: 1000 req/s, 10k lines/sec, 60 fps 또는 NEEDS CLARIFICATION]

**제약사항**: [도메인별, 예: <200ms p95, <100MB memory, offline-capable 또는 NEEDS CLARIFICATION]

**규모/범위**: [도메인별, 예: 10k users, 1M LOC, 50 screens 또는 NEEDS CLARIFICATION]

## 헌법 검사

*게이트: Phase 0 research 이전에 통과해야 함. Phase 1 design 이후 재검사.*

[constitution 파일을 기반으로 결정된 게이트]

## 프로젝트 구조

### 문서 (이 기능)

```text
specs/[###-feature]/
├── plan.md              # 이 파일 (/speckit-plan 명령 결과물)
├── research.md          # Phase 0 결과물 (/speckit-plan 명령)
├── data-model.md        # Phase 1 결과물 (/speckit-plan 명령)
├── quickstart.md        # Phase 1 결과물 (/speckit-plan 명령)
├── contracts/           # Phase 1 결과물 (/speckit-plan 명령)
└── tasks.md             # Phase 2 결과물 (/speckit-tasks 명령 - /speckit-plan에서는 생성하지 않음)
```

### 소스 코드 (저장소 루트)
<!--
  액션 필요: 아래 플레이스홀더 트리를 이 기능에 맞는 실제 구조로 교체하세요.
  사용하지 않는 옵션은 삭제하고, 선택한 구조를 실제 경로(예: apps/admin,
  packages/something)로 확장하세요. 완성된 계획에는 Option 라벨이 남아있으면 안 됩니다.
-->

```text
# [사용하지 않으면 삭제] Option 1: Single project (기본값)
src/
├── models/
├── services/
├── cli/
└── lib/

tests/
├── contract/
├── integration/
└── unit/

# [사용하지 않으면 삭제] Option 2: Web application ("frontend" + "backend" 감지 시)
backend/
├── src/
│   ├── models/
│   ├── services/
│   └── api/
└── tests/

frontend/
├── src/
│   ├── components/
│   ├── pages/
│   └── services/
└── tests/

# [사용하지 않으면 삭제] Option 3: Mobile + API ("iOS/Android" 감지 시)
api/
└── [위 backend와 동일]

ios/ or android/
└── [플랫폼별 구조: 기능 모듈, UI 플로우, 플랫폼 테스트]
```

**구조 결정**: [선택한 구조를 기술하고 위에서 정리한 실제 디렉토리를 참조]

## 복잡도 추적

> **헌법 검사에서 위반 사항이 있어 정당화가 필요한 경우에만 작성**

| 위반 사항 | 필요한 이유 | 더 단순한 대안을 거부한 이유 |
|-----------|------------|-------------------------------------|
| [예: 4번째 프로젝트] | [현재 필요성] | [3개 프로젝트로 불충분한 이유] |
| [예: Repository 패턴] | [구체적 문제] | [직접 DB 접근으로 불충분한 이유] |
