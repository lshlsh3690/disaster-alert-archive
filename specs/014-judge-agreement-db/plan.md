# 구현 계획: 판사 일치율 측정 결과를 DB 에 두고 현재 라벨로 계산한다

**브랜치**: `feature/judge-agreement-db` | **날짜**: 2026-11-25 | **명세**: [spec.md](spec.md)

**입력**: `specs/014-judge-agreement-db/spec.md`의 기능 명세

## 요약

판사 버전별 일치율을 코드 상수(`JudgeVersionCatalog` 의 `sameNum…diffDen`)에서 DB 로 옮긴다. 측정마다 **쌍별 판사 판정**을 저장하고, 일치율은 저장하지 않고 **현재 사람 라벨**(`evaluation_pair_label`)과 맞대어 그때그때 계산한다. 그래서 관리자 화면에서 라벨을 고치면 판사를 다시 부르지 않고 일치율이 바뀐다.

- 테이블 두 개(`judge_agreement_run`, `judge_agreement_run_pair`)를 V145 로 만들고, v1(합계만)·v2(쌍별 225건, 11-18 리포트에서 복원) 측정을 V146 시드로 넣는다.
- 일치율 계산 규칙은 순수 클래스 하나(`JudgeAgreementCalculator`)에 두고, 조회·조율은 `JudgeAgreementService` 가 한다.
- 일일 채점(`ClusteringDailyScoreService`)과 대시보드 판사 버전 이력(`ClusteringDashboardService.judgeVersions`)이 상수 대신 이 서비스를 읽는다.
- 측정 도구(`--eval.judge-agreement`)는 마크다운 리포트를 없애고 결과를 접속한 DB 에 저장한다.
- 판사 모델·지시문·단가는 코드에 남는다(`JudgeVersionCatalog` 는 버전·모델·단가만).

## 기술 컨텍스트

**언어/버전**: Java 17(Spring Boot 3.x), TypeScript(Next.js App Router)

**주요 의존성**: Spring Data JPA, JdbcTemplate(기존 평가 코드와 같음), Flyway, React Query

**저장소**: PostgreSQL. 새 테이블 2개(V145), 시드(V146). 기존 `evaluation_pair_label`(V144)·`clustering_daily_score` 는 읽기만 한다.

**테스트**: JUnit 5. 계산 규칙은 순수 단위 테스트, 시드·서비스는 `@IntegrationTest`(로컬 Postgres, `.env.test`). Red-Green 은 CLAUDE.md 규칙대로(Red 를 사용자가 확인한 뒤 Green).

**대상 플랫폼**: 운영 맥북 Docker(prod 프로필), 로컬 평가 사본(eval 프로필)

**프로젝트 유형**: 웹 서비스(backend + frontend)

**성능 목표**: 판사 버전 이력 조회 한 번에 측정 수 × (쌍 225 + 라벨 272) 행을 읽는다. 버전이 몇 개뿐이라 별도 목표를 두지 않는다. 일일 채점은 하루 한 번 현재 버전 하나만 계산한다.

**제약사항**:
- 판사(유료) 호출은 이 기능의 검증·이관에 쓰지 않는다. 측정 도구를 실제로 돌리는 것은 사용자 승인 뒤의 별개 작업이다.
- V146 시드는 `disaster_alert`·`evaluation_pair_label` 이 비어 있는 DB(테스트·새 개발 DB)에서도 들어가야 한다 → 알림 외래 키를 걸지 않고, 시드 값은 다른 표를 읽지 않는 고정값으로 쓴다.
- 운영 DB 의 272쌍 라벨은 사용자가 배포 전에 덤프로 넣는다(명세 FR-013). 라벨이 없으면 일일 채점은 비용 없이 건너뛴다.
- PR #358(라벨 JSON 읽기 삭제)이 머지된 뒤 구현한다 — 측정 도구가 라벨을 DB 에서만 읽는 상태를 전제로 한다.

**규모/범위**: 백엔드 새 파일 약 7개(엔티티 2·리포지토리 2·순수 클래스 1·서비스 1·마이그레이션 2), 수정 약 8개, 삭제 2개(`JudgeAgreementReport`, 그 사용처). 프론트 2개 파일 수정.

## 헌법 검사

| 원칙 | 검사 | 결과 |
|---|---|---|
| I 가독성·단순성 | 011 설계의 상태 머신(RUNNING/ABORTED), 비교 버전, 승인 열, 진행 표시를 가져오지 않는다. CLI 가 끝에 한 번 저장하므로 필요 없다(research R1). | 통과 |
| II 계층 | 리포지토리는 서비스에서만 쓴다. 대시보드 API 응답은 기존 `ApiResponse`·DTO 하나에 필드만 더한다. 예외는 기존 `CustomException`/`ErrorCode`(API) 와 `IllegalArgumentException`(CLI 도구, 기존 관례). | 통과 |
| III 검증 가능 | 계산 규칙은 DB 없이 단위 테스트. 시드 교차표(127/14/3/74/7)는 통합 테스트로 고정. 판사 호출은 테스트에서 쓰지 않는다. | 통과 |
| IV 정직한 문서 | 006 plan·008 spec/contract·CLAUDE.md 의 일치율 출처 설명을 바꾼다. `JudgeVersionCatalog` javadoc 의 측정 근거는 마이그레이션 주석으로 옮긴다. | 통과 |
| V 한글 커밋 | 해당 | 통과 |
| VI 순수 도메인 규칙 | 일치율 계산(라벨×판정 매칭, 분모 제외, 측정에 없는 쌍 세기)은 `JudgeAgreementCalculator`(JPA·Spring import 없음)에 둔다. 서비스는 읽고 넘기기만 한다. | 통과 |
| VII 맥락 경계 | 새 코드는 모두 `domain/event` 안이다. `evaluation_pair_label`(엔티티 `EvaluationLabel`)도 같은 맥락이라 리포지토리를 직접 쓴다. 알림은 ID 로만 참조한다(외래 키·연관 없음). | 통과 |

설계 뒤 재검사: 위와 같다. 위반 없음 — 복잡도 추적 표는 비운다.

## 프로젝트 구조

### 문서 (이 기능)

```text
specs/014-judge-agreement-db/
├── spec.md
├── plan.md              # 이 파일
├── research.md          # 결정과 근거
├── data-model.md        # 테이블 2개, 계산 값, 바뀌는 상수
├── quickstart.md        # 검증 절차
├── contracts/
│   └── judge-agreement.md   # 관리자 API 변경분, 측정 도구(CLI) 계약
└── checklists/requirements.md
```

### 소스 코드 (저장소 루트)

```text
backend/src/main/resources/db/migration/
├── V145__create_judge_agreement_run.sql          # 새: 테이블 2개
└── V146__seed_judge_agreement_v1_v2.sql          # 새: v1 합계, v2 측정 + 쌍별 225건(고정값)

backend/src/main/java/com/disaster/alert/alertapi/domain/event/
├── model/JudgeAgreementRun.java                  # 새: 엔티티(수정 메서드 없음)
├── model/JudgeAgreementRunPair.java              # 새: 엔티티
├── repository/JudgeAgreementRunRepository.java   # 새
├── repository/JudgeAgreementRunPairRepository.java  # 새
├── judge/JudgeAgreementCalculator.java           # 새: 순수 계산 규칙
├── service/JudgeAgreementService.java            # 새: 버전별 일치율 조회·측정 저장
├── judge/JudgeVersionCatalog.java                # 고침: 일치율·측정일 제거(버전·모델·단가만)
├── judge/ClusteringJudge.java                    # 고침: agreementOf 제거, Agreement 레코드는 유지
├── service/ClusteringDailyScoreService.java      # 고침: 일치율을 JudgeAgreementService 에서
├── service/ClusteringDashboardService.java       # 고침: judgeVersions 가 서비스 값 사용
├── dto/ClusteringDashboardJudgeVersionsResponse.java  # 고침: recomputable·unmeasuredPairs 추가
├── evaluation/JudgeAgreementRunner.java          # 고침: 리포트 대신 DB 저장 + 콘솔 요약
├── evaluation/EvaluationRunTool.java             # 고침: judge-agreement 와 --eval.report 동시 지정 거부
└── evaluation/JudgeAgreementReport.java          # 삭제

backend/src/test/java/.../domain/event/
├── judge/JudgeAgreementCalculatorTest.java       # 새: 순수 단위
└── service/JudgeAgreementServiceTest.java        # 새: @IntegrationTest(시드 교차표, 라벨 변경 반영)

frontend/src/
├── api/clusteringAdminApi.ts                     # 고침: 응답 타입 두 필드
└── app/admin/clustering/_sections.tsx            # 고침: 판사 버전 이력 표시
```

**구조 결정**: 기존 웹 애플리케이션 구조(backend + frontend) 그대로다. 판사 관련 순수 규칙은 `domain/event/judge` 에, 조율은 `domain/event/service` 에 둔다(011 의 `JudgeMistakeGrouping` 등과 같은 배치). 테이블 이름은 011 설계와 같게 둬서 011 을 나중에 재개할 때 그대로 이어 쓸 수 있게 한다.

## 구현 순서 (요약 — 상세는 tasks.md)

1. **Red**: `JudgeAgreementCalculatorTest`(핵심 3~4개) + 틀린 기본값을 돌려주는 계산기 뼈대. 사용자 확인 후 Green.
2. **Green**: 계산기 구현.
3. V145·V146 마이그레이션(시드는 로컬 DB 에서 research R4 의 SQL 로 뽑은 고정값), 엔티티·리포지토리.
4. `JudgeAgreementService` + 시드 교차표·라벨 변경 반영 통합 테스트.
5. 일일 채점·대시보드 연결, 상수 제거(`JudgeVersionCatalog`, `agreementOf`).
6. 측정 도구: 리포트 제거, DB 저장, 콘솔 요약.
7. 프론트 표시, 문서(006 plan, 008 spec/contract, CLAUDE.md, `backend/evaluation/README.md`).
8. 배포 전: 사용자가 운영 DB 에 라벨 덤프 → 배포 → 다음 날 채점 기록 확인(SC-004).

## 복잡도 추적

위반 없음.
