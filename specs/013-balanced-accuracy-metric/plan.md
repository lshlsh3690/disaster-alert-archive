# 구현 계획: 클러스터링 평가의 대표 지표를 균형 정확도로 바꾼다

**브랜치**: `feature/balanced-accuracy-metric` | **날짜**: 2026-11-24 | **명세**: [spec.md](spec.md)

**입력**: `specs/013-balanced-accuracy-metric/spec.md` 의 기능 명세

## 요약

쌍 평가가 지금 버리는 "맞게 나눈 쌍"(사람 다른 사건, 시스템 다른 이벤트)을 세서, 섞지 않은 비율과 균형 정확도 = (빠짐없이 묶은 비율 + 섞지 않은 비율) ÷ 2 를 대표 점수로 낸다. 계산은 오프라인 평가와 운영 일일 채점이 함께 쓰는 순수 계산기(`PairCounter`·`PairCountResult`) 한 곳에 더하고, 판단 없는 기준선(전부 묶음·전부 따로)은 결과 개수에서 계산하는 순수 클래스로 둔다. 일일 기록은 기존 여섯 집계값에서 맞게 나눈 쌍 수를 복원하며, 새 열·백필·LLM 재호출을 추가하지 않는다. 대시보드는 기존 필드를 그대로 두고 필드를 더한다. 마지막으로 006·008 문서의 지표 서술을 고친다. 결정 근거는 [research.md](research.md).

## 기술 컨텍스트

**언어/버전**: Java 17 (Spring Boot 3.4), TypeScript (Next.js 15)

**주요 의존성**: Spring Data JPA, Flyway, React Query — 새 의존성 없음

**저장소**: PostgreSQL — 기존 `clustering_daily_score` 집계 재사용, 스키마·저장 형식 변경 없음

**테스트**: JUnit 5 순수 단위 테스트(Red-Green, Red 는 사용자 확인 후 Green), 프론트는 기존 Jest 테스트가 있으면 갱신

**대상 플랫폼**: 기존 백엔드(맥북 셀프호스팅)와 관리자 화면

**프로젝트 유형**: web-service (backend + frontend)

**성능 목표**: 해당 없음 — 개수 하나를 더 셀 뿐이다

**제약사항**: 기존 세 개수·F1·틀린 쌍 표가 변하지 않아야 한다(명세 FR-005). 유료 호출 0(명세 FR-009). 이미 적용된 마이그레이션은 고치지 않는다.

**규모/범위**: 272쌍 평가 1개 셋, 일일 기록 2026-11-17 분부터 수십 일치

## 헌법 검사

*게이트: Phase 0 research 이전에 통과해야 함. Phase 1 design 이후 재검사.*

| 원칙 | 결과 | 근거 |
|---|---|---|
| I. 가독성과 단순성 | 통과 | 새 계산은 기존 계산기에 메서드 둘·개수 하나를 더하는 정도. 판단 없는 기준선은 결과 개수만 받는 작은 클래스 |
| II. 계층형 아키텍처 | 통과 | 대시보드는 기존 controller → service 흐름 그대로, 응답 DTO 에 필드만 더함 |
| III. 검증 가능한 변경 | 통과 | 순수 계산은 Red-Green, 회귀는 같은 배정의 변경 전후 리포트 diff(quickstart 2) |
| IV. 정직한 문서화 | 통과 | 분모 0은 계산 불가, 날짜별 기록 부재는 기록 없음으로 구분. 유효 집계가 있으면 쌍별 기록 부재와 무관하게 복원. 새 API 필드 생략 규칙을 기존 직렬화와 맞춤 |
| V. 한글 커밋 | 통과 | 커밋 단위는 Red / Green / 운영 로그·대시보드 / 문서로 나눔. 마이그레이션 없음 |
| VI. 순수 도메인 규칙 | 통과 | `PairCounter`·`PairCountResult`·`TrivialBaselines` 는 JPA·Spring 을 import 하지 않음 |
| VII. 맥락 경계 | 통과 | 모두 `domain/event` 맥락 안. 다른 맥락의 리포지토리를 주입하지 않음 |

Phase 1 설계 뒤 재검사: 변동 없음.

## 프로젝트 구조

### 문서 (이 기능)

```text
specs/013-balanced-accuracy-metric/
├── spec.md
├── plan.md              # 이 파일
├── research.md          # 결정 R1~R10
├── data-model.md        # 계산 결과·기록·응답 필드
├── quickstart.md        # 검증 안내
├── contracts/
│   └── report-and-api.md  # 리포트 요약 형식, 일일 점수 API 필드
└── checklists/
    └── requirements.md
```

### 소스 코드 (저장소 루트)

```text
backend/src/main/java/com/disaster/alert/alertapi/domain/event/
├── evaluation/
│   ├── PairCounter.java            # 맞게 나눈 쌍 세기
│   ├── PairCountResult.java        # correctlySeparated, fromDailyCounts(...), separation(), balancedAccuracy(), differentEventPairs()
│   ├── TrivialBaselines.java       # 신규: 전부 묶음·전부 따로 결과
│   ├── ReportFormatter.java        # 요약 첫 줄 균형 정확도, 기준선 줄
│   └── EvaluationRunTool.java      # 기준선을 리포트에 넘김
├── service/ClusteringDailyScoreService.java   # 저장 형식 유지, 로그 대표 점수 변경
├── service/ClusteringDashboardService.java    # 응답에 separation·balancedAccuracy
├── dashboard/ClusteringDashboardMetrics.java  # (필요하면) 표시 반올림 재사용
└── dto/ClusteringDashboardDailyScoresResponse.java

# ClusteringDailyScore 엔티티·저장 형식과 DB 스키마는 변경하지 않는다.

backend/src/test/java/com/disaster/alert/alertapi/domain/event/evaluation/
├── PairCounterTest.java, PairCountResultTest.java(신규), TrivialBaselinesTest.java(신규), ReportFormatterTest.java

frontend/src/api/clusteringAdminApi.ts            # Day 타입에 필드 3개
frontend/src/app/admin/clustering/_sections.tsx   # 점수 칸 순서·머리글

specs/006-clustering-evaluation/{spec,quickstart}.md, specs/008-clustering-admin-dashboard/{spec,quickstart}.md, backend/evaluation/README.md
specs/008-clustering-admin-dashboard/contracts/admin-clustering-api.md # 새 필드·계산 불가 키 생략
```

**구조 결정**: 기존 web-service 구조(backend + frontend) 그대로. 새 소스·테스트 파일은 `TrivialBaselines`·그 테스트·`PairCountResultTest` 셋뿐이다. 일일 집계 복원은 기존 순수 record의 `fromDailyCounts(...)`에 두고 서비스나 엔티티에 식을 중복하지 않는다.

## 구현 순서와 승인 지점

| 단계 | 내용 | 승인 |
|---|---|---|
| 1 | Red: 신규 핵심 테스트 4개(맞게 나눔 집계, 서로 다른 배수의 구성비 불변성, 일일 집계 복원, 기준선 50%). 기존 리포트 테스트의 요약 기대값도 새 계약으로 고치고 뼈대와 함께 커밋 | **사용자 Red 확인 후 Green** |
| 2 | Green: `PairCounter`·`PairCountResult`(일일 집계 복원 포함)·`TrivialBaselines`·`ReportFormatter`·`EvaluationRunTool`. 테스트는 변경하지 않음 | 변경이 100줄 이상이면 커밋 전 code-reviewer |
| 3 | 회귀 확인: 같은 사본·라벨로 평가한 두 리포트에서 실행 시각 한 줄만 제거한 뒤 diff. 요약 밖은 동일하고 기존 세 개수·F1 값은 보존(quickstart 2) | 무료 |
| 4 | 기존 일일 집계 복원 전제를 읽기 전용 SQL로 확인하고 일일 로그 대표 점수를 변경. 엔티티·저장 형식·DB 스키마 유지(quickstart 3) | 무료 |
| 5 | 대시보드 응답·화면: 새 필드는 선택값, 계산 불가 `balancedAccuracy`는 키 생략. 분모 0인 Ratio는 객체를 유지하고 `percent: null` | 화면 점검 |
| 6 | 문서: 006·008·평가 README | — |
| 7 | 운영 배포 — 기존 날짜별 집계에서 새 지표를 즉시 조회, DB 변경·과거 기록 갱신 없음 | 배포는 사용자 |

## 복잡도 추적

헌법 위반이 없어 작성하지 않는다.
