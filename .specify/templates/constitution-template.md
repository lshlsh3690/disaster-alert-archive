# [PROJECT_NAME] Constitution
<!-- 예: Spec Constitution, TaskFlow Constitution 등 -->

## 핵심 원칙

### [PRINCIPLE_1_NAME]
<!-- 예: I. Library-First -->
[PRINCIPLE_1_DESCRIPTION]
<!-- 예: 모든 기능은 독립 실행 가능한 라이브러리로 시작한다; 라이브러리는 자체 완결적이고, 독립적으로 테스트 가능하며, 문서화되어야 한다; 목적이 명확해야 한다 - 조직 편의용 라이브러리는 금지 -->

### [PRINCIPLE_2_NAME]
<!-- 예: II. CLI Interface -->
[PRINCIPLE_2_DESCRIPTION]
<!-- 예: 모든 라이브러리는 CLI로 기능을 노출한다; 텍스트 입출력 프로토콜: stdin/args → stdout, 에러 → stderr; JSON과 사람이 읽을 수 있는 형식을 모두 지원 -->

### [PRINCIPLE_3_NAME]
<!-- 예: III. Test-First (NON-NEGOTIABLE) -->
[PRINCIPLE_3_DESCRIPTION]
<!-- 예: TDD 필수: 테스트 작성 → 사용자 승인 → 테스트 실패 확인 → 그 다음 구현; Red-Green-Refactor 사이클을 엄격히 따름 -->

### [PRINCIPLE_4_NAME]
<!-- 예: IV. Integration Testing -->
[PRINCIPLE_4_DESCRIPTION]
<!-- 예: 통합 테스트가 필요한 영역: 신규 라이브러리 contract test, contract 변경, 서비스 간 통신, 공유 스키마 -->

### [PRINCIPLE_5_NAME]
<!-- 예: V. Observability, VI. Versioning & Breaking Changes, VII. Simplicity -->
[PRINCIPLE_5_DESCRIPTION]
<!-- 예: 텍스트 I/O로 디버깅 용이성 확보; 구조화된 로깅 필수; 또는: MAJOR.MINOR.BUILD 형식; 또는: 단순하게 시작, YAGNI 원칙 -->

## [SECTION_2_NAME]
<!-- 예: Additional Constraints, Security Requirements, Performance Standards 등 -->

[SECTION_2_CONTENT]
<!-- 예: 기술 스택 요구사항, 컴플라이언스 표준, 배포 정책 등 -->

## [SECTION_3_NAME]
<!-- 예: Development Workflow, Review Process, Quality Gates 등 -->

[SECTION_3_CONTENT]
<!-- 예: 코드 리뷰 요구사항, 테스트 게이트, 배포 승인 절차 등 -->

## 거버넌스
<!-- 예: 이 constitution은 다른 모든 관행보다 우선한다; 개정에는 문서화, 승인, 마이그레이션 계획이 필요 -->

[GOVERNANCE_RULES]
<!-- 예: 모든 PR/리뷰는 준수 여부를 확인해야 한다; 복잡성은 반드시 정당화되어야 한다; 런타임 개발 가이드는 [GUIDANCE_FILE]을 사용 -->

**버전**: [CONSTITUTION_VERSION] | **제정일**: [RATIFICATION_DATE] | **최종 개정일**: [LAST_AMENDED_DATE]
<!-- 예: Version: 2.1.1 | Ratified: 2025-06-13 | Last Amended: 2025-07-16 -->
