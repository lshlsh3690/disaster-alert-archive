# 구현 계획: 위험도 계산 (Region Risk Scoring)

**브랜치**: `002-risk-scoring` | **날짜**: 2026-08-05 | **명세**: [spec.md](./spec.md)

**입력**: `specs/002-risk-scoring/spec.md`의 기능 명세

**참고**: 이 문서는 이미 구현·운영 중인 시스템을 역으로 분석해 작성한 **as-built
계획서**다. speckit의 표준 흐름(계획 → 구현)을 따르지 않았으므로, 이 문서의 목적은
"앞으로 어떻게 만들 것인가"가 아니라 "실제로 어떻게 만들어졌고 왜 그런 구조인가"를
설명하는 것이다. Phase 0/1 산출물(research.md, data-model.md, contracts/,
quickstart.md)은 코드에서 역추출 가능한 핵심 내용을 본 문서와 spec.md에 이미
포함시켰으므로 별도 파일로 분리하지 않았다.

## 요약

재난문자가 이벤트로 클러스터링되면(`domain/event`), 위험도 모듈은 `AlertClusteredEvent`
Spring 애플리케이션 이벤트를 커밋 후 비동기로 수신하여 3단계 파이프라인
(이벤트→법정동 영향 기록 → 시군구 자기 위험도(source) 재계산 → 인접 그래프 확산으로
effective 위험도 산출)을 실행한다. 점수는 `weight[유형] × intensity[강도] ×
severity[위급단계]`으로 합성되고, half-life 기반 지수 감쇠와 인접 시군구로의 계수형
공간 확산(BFS, MAX 결합)을 거쳐 0~1로 정규화된 `region_risk_index.risk_score`가 된다.
클러스터링 도메인은 위험도 계산 방식을 전혀 알지 못하며, 위험도 모듈도 클러스터링
임계값/병합 방식을 전혀 알지 못한다 — 둘은 `AlertClusteredEvent(eventId, alertId)`라는
얇은 payload로만 연결된다. 3분/1시간/일 단위 스케줄러(`RiskDecayScheduler`)가 시간
경과에 따른 감쇠 반영, 시계열 스냅샷, 일별 롤업, retention 정리를 담당한다.
35종에 없는 재난 유형은 `LlmRiskProfiler`가 `gpt-4o-mini`로 프로파일을 생성해
캐싱한다.

## 기술 컨텍스트

**언어/버전**: Java 17 (Gradle toolchain, `backend/build.gradle:16-18`), Spring Boot 3.4.4

**주요 의존성**: Spring Data JPA(`disaster_risk_profile` 등 엔티티/리포지토리), Spring
Web(REST 컨트롤러), Spring TX(`@Transactional`, `@TransactionalEventListener`),
Spring Scheduling(`@Scheduled`, `@EnableAsync`), Spring AI + OpenAI(`gpt-4o-mini`,
`LlmRiskProfiler`), Lombok. QueryDSL은 이 도메인에서는 사용하지 않으며, 집계·upsert는
JPQL(`@Query`) 또는 네이티브 SQL(`@Modifying @Query(nativeQuery=true)`,
`JdbcTemplate`)로 직접 작성되어 있다(`RegionRiskQueryRepository`는 JPA 리포지토리가
아닌 `JdbcTemplate` 기반 순수 조회 클래스).

**저장소**: PostgreSQL(pgvector 이미지 사용 — 단, 위험도 도메인 자체는 벡터 컬럼을
사용하지 않음). 관련 테이블: `disaster_risk_profile`, `intensity_bracket`,
`event_region_impact`, `region_risk_index`, `region_risk_history`,
`region_risk_daily`, `region_adjacency` (`V31/V32/V33/V35/V37/V111/V112` 마이그레이션).

**테스트**: 없음. `backend/src/test` 하위에 위험도 도메인(`RiskCalculationService`,
`RiskScore`, `LlmRiskProfiler` 등)을 대상으로 한 테스트 파일이 존재하지 않는다 — 이는
헌법 III 원칙과 배치되며 아래 "헌법 검사"에서 별도로 다룬다.

**대상 플랫폼**: Docker Compose 기반 배포되는 Spring Boot 백엔드 API 서버(Linux 컨테이너).

**성능 목표**: 코드/설정에 공식적으로 선언된 목표치는 없다. 전체 그래프 전파
(`propagateEffective`)의 "ms 단위" 완료 주장과 그 근거(비검증 상태)는
`spec.md`의 SC-003을 canonical 출처로 삼는다 — 이 문서에서는 반복하지 않는다.

**제약사항**: 위험도 재계산은 클러스터링 트랜잭션과 별도 스레드 풀(`riskTaskExecutor`,
core=2/max=4/queue=500)에서 비동기 실행되어야 하며, 실패해도 클러스터링/수집
파이프라인에 영향을 주면 안 된다(`ClusteringEventListener.java:49-52`). 자기 자신을
호출하는 `@Transactional` 프록시 우회 문제를 피하기 위해 `recomputeEventRisk`는
`region_risk_index`를 직접 갱신하지 않고 호출자(리스너, 외부 빈)가 별도로
`recomputeRegionSource`/`propagateEffective`를 호출하는 3단계 분리 구조를 강제한다.

**규모/범위**: 재난 유형 35종(V33 시드) + LLM 런타임 생성분, 시군구 약 230개, 인접
쌍 약 1,300개(양방향 1,314행, V37), 강도 구간이 정의된 유형은 6종(지진/호우/폭염/
한파/산불/강풍)뿐.

## 헌법 검사

*게이트: Phase 0 research 이전에 통과해야 함. Phase 1 design 이후 재검사.*
*(본 기능은 이미 구현되어 있으므로, 여기서는 "게이트 통과 여부"가 아니라 "실제
구현이 헌법 원칙을 준수하는지"를 사후 검증한다.)*

| 원칙 | 결과 | 근거 |
|---|---|---|
| I. 가독성과 단순성 우선 | **일부 위반** | 위험도 도메인 핵심 서비스 클래스(`RiskCalculationService`, `RiskMaintenanceService`, `LlmRiskProfiler`, `RegionRiskQueryService` 등) 중 30~50줄 권장 범위를 벗어나는 메서드는 2개뿐이다: `recomputeEventRisk`(`RiskCalculationService.java:85-147`, 약 63줄)와 `propagateEffective`(`RiskCalculationService.java:193-247`, 약 55줄). 나머지 메서드는 모두 범위 이내. 두 메서드 모두 불필요한 라이브러리 도입 없이 JDK 표준 컬렉션 + JPA만 사용하며, `RiskCalculationService`가 "3경로 분리"를 클래스 주석에 명시해 YAGNI 위반이 아니라 실제 트랜잭션 제약(자기호출 프록시 문제, spec.md FR-034)에서 나온 설계임을 밝히고 있다(`RiskCalculationService.java:43-55`) — 정당화는 되지만 원칙 문면(30~50줄) 기준으로는 위반이므로 "일부 위반"으로 기록하고, 근거는 아래 복잡도 추적 표에 남긴다. |
| II. 계층형 아키텍처 준수 | **일부 위반(2건)** | 컨트롤러→서비스→리포지토리 계층은 잘 지켜짐(`RegionRiskController` → `RegionRiskQueryService`/`RiskCalculationService` → `*Repository`). **(1) `ApiResponse` 포맷 미준수**: 5개 엔드포인트 중 4개는 `ApiResponse`로 일관 포장하지만(`RegionRiskController.java:26,33,41,50`), **`GET /alerts/{alertId}/risk`(`alertRisk`, `RegionRiskController.java:61-66`)는 `ApiResponse<AlertRiskResponse>`로 감싸지 않고 `ResponseEntity<AlertRiskResponse>`를 그대로 반환한다** — 204 분기는 정상이나 200 분기의 페이로드가 미포장이라 "모든 API 응답은 공용 ApiResponse/ApiErrorResponse 포맷을 사용한다"는 규정을 위반한다. **(2) 예외 처리 미준수**: `LlmRiskProfiler`가 `CustomException`/`ErrorCode`가 아닌 `IllegalStateException`을 직접 던진다("기타" 시드 프로파일 누락 시) — `LlmRiskProfiler.java:51,73`. 이는 "예외는 CustomException + ErrorCode로만 던진다"는 규정 위반이다. |
| III. 검증 가능한 변경 | **위반** | 헌법이 "비즈니스 로직(위험도 계산...)은 외부 의존성을 목킹한 단위 테스트로 우선 검증한다"고 명시적으로 위험도 계산을 예시로 들고 있음에도, `backend/src/test`에는 `RiskCalculationService`/`RiskScore`(순수 값 객체라 단위 테스트가 특히 쉬움에도 불구)/`LlmRiskProfiler`/`RiskMaintenanceService`를 대상으로 한 테스트가 전혀 없다. |
| IV. 정직한 문서화 | 준수(본 문서 작성 과정에서) | 본 spec.md/plan.md는 실제 코드를 직접 읽고 작성되었으며, 발견된 결함(테스트 부재, 고아 메서드 `applyOperatorOverride`, stale 주석 `findHistoricalEvents`의 "7일" vs 실제 30일)을 숨기지 않고 "가정" 섹션에 남김. |
| V. 한글 커밋 컨벤션 | 해당 없음 | 본 계획 문서 자체는 커밋/PR 메시지가 아니므로 직접 해당하지 않음. 관련 기존 커밋 이력은 별도 확인 대상 아님(본 작업 범위 밖). |

**결론**: 원칙 I(메서드 길이 초과 2건, 근거는 있으나 문면상 위반)·II(2건: `alertRisk`
엔드포인트의 `ApiResponse` 미포장, `LlmRiskProfiler`의 비표준 예외)·III(테스트
부재)에서 실제 위반이 발견되었다. 이번 문서화 작업(retroactive spec) 자체는 코드를
변경하지 않으므로 즉시 수정하지는 않지만, 향후 위험도 도메인을 수정하는 PR에서는
(a) `RegionRiskController.alertRisk`가 `ApiResponse<AlertRiskResponse>`를
반환하도록 수정하고, (b) `LlmRiskProfiler`의 예외를 `CustomException`+전용
`ErrorCode`로 교체하고, (c) 최소한 `RiskScore`(순수 함수 값 객체)와
`RiskCalculationService`의 핵심 경로(감쇠, MAX 결합, BFS 전파 가지치기, 동점 처리)에
대한 단위 테스트를 추가하는 것을 권장한다.

## 프로젝트 구조

### 문서 (이 기능)

```text
specs/002-risk-scoring/
├── plan.md               # 이 파일
└── spec.md               # 기능 명세 (요구사항/시나리오/가정 — Phase 0/1 내용 포함)
```

research.md/data-model.md/quickstart.md/contracts/를 별도 파일로 분리하지 않은 이유:
이미 구현이 끝난 시스템을 문서화하는 작업이라 "설계 리서치"가 아니라 "코드 판독"이
전부이며, 데이터 모델(주요 엔티티)과 계약(API 엔드포인트)은 spec.md의 "주요 엔티티"
및 FR-028~031에 실제 스키마/라우트로 이미 기술되어 있다. 신규 산출물을 만들면
동일 정보가 두 곳에 흩어져 오히려 정합성 리스크가 커진다.

### 소스 코드 (저장소 루트)

```text
backend/src/main/java/com/disaster/alert/alertapi/domain/risk/
├── RiskConstants.java                      # 활성 윈도우(30일)/정규화 분모(2.0)/기본 half-life(24h)/retention(90일) 공통 상수
├── controller/
│   └── RegionRiskController.java           # GET /api/v1/regions/{risk-map,{code}/risk,risk-map/history,{code}/risk/history,alerts/{id}/risk}
├── controller/dto/
│   └── RiskResponses.java                  # 응답 DTO 5종(레코드 nested)
├── event/
│   └── AlertClusteredEvent.java            # domain/event가 발행, 여기서 구독하는 payload record(eventId, alertId)
├── listener/
│   └── ClusteringEventListener.java        # @Async("riskTaskExecutor") + @TransactionalEventListener(AFTER_COMMIT)
├── model/                                  # JPA 엔티티 7종 + 임베디드 ID
│   ├── DisasterRiskProfile.java            # 유형별 weight/half-life/spread_coeff (35종 seed + LLM 생성분)
│   ├── IntensityBracket.java               # 유형별 강도 구간 → multiplier
│   ├── EventRegionImpact.java              # 이벤트→법정동 감쇠 전 impact
│   ├── RegionRiskIndex.java                # 시군구 현재 위험도(source/effective 분리)
│   ├── RegionRiskHistory.java              # 시간별 시계열 스냅샷
│   ├── RegionRiskDaily.java                # 일별 요약(다운샘플링)
│   ├── RegionAdjacency.java                # 시군구 인접 그래프
│   └── RiskScore.java                      # 점수 합성/감쇠/정규화 규칙을 소유하는 순수 값 객체(record)
├── repository/                             # JpaRepository + 네이티브 upsert/집계 쿼리
│   ├── DisasterRiskProfileRepository.java
│   ├── IntensityBracketRepository.java
│   ├── EventRegionImpactRepository.java
│   ├── RegionRiskIndexRepository.java
│   ├── RegionAdjacencyRepository.java
│   ├── RegionRiskHistoryRepository.java
│   ├── RegionRiskDailyRepository.java
│   ├── RegionRiskQueryRepository.java      # JdbcTemplate 기반 조회 전용(JpaRepository 아님)
│   └── projection/                         # 네이티브 쿼리 결과 매핑용 record 4종
├── service/
│   ├── RiskCalculationService.java         # 핵심 3경로: recomputeEventRisk / recomputeRegionSource / propagateEffective
│   ├── RiskMaintenanceService.java         # 스케줄러가 호출하는 유지보수 로직(스냅샷/롤업/정리/전체재계산)
│   ├── RegionRiskQueryService.java         # 컨트롤러용 읽기 전용 오케스트레이션
│   ├── IntensityExtractor.java             # 알림 본문 정규식 → 강도 수치 추출
│   ├── LlmRiskProfiler.java                # 35종 외 유형의 LLM 프로파일 생성/캐싱
│   └── dto/LlmProfileResponse.java         # Spring AI .entity() 매핑 대상 DTO
├── scheduler/
│   └── RiskDecayScheduler.java             # 3분/1시간/일 23:50/일 04:30 cron 트리거 → RiskMaintenanceService 위임
└── tool/
    └── RiskBackfillTool.java               # @Profile("risk-backfill") ApplicationRunner — 과거 이벤트 위험도 일괄 백필

backend/src/main/resources/db/migration/
├── V31__create_risk_model.sql              # disaster_risk_profile/intensity_bracket/event_region_impact/region_risk_index/region_risk_history
├── V32__add_event_cooldown_hours.sql
├── V33__seed_risk_profile.sql              # 35종 프로파일 + 6종 강도구간 시드
├── V35__create_region_risk_daily.sql
├── V37__region_risk_spatial_spread.sql     # region_adjacency(1,314행) + spread_coeff 컬럼 + source/effective 분리
├── V111__migrate_historical_alert_event_risk_data_to_merged_codes.sql
└── V112__cleanup_stale_jeonnam_gwangju_adjacency_and_risk_leftovers.sql

backend/src/main/java/com/disaster/alert/alertapi/global/config/
└── AsyncConfig.java                        # riskTaskExecutor 빈 정의(core=2/max=4/queue=500)

backend/src/main/java/com/disaster/alert/alertapi/domain/event/service/
└── EventClusteringService.java             # AlertClusteredEvent 발행 지점 3곳(:510, :583, :600) — 위험도 도메인 외부, 클러스터링 소유
```

**구조 결정**: 이 저장소는 백엔드(Spring Boot)/프론트엔드(Next.js) 분리형 웹
애플리케이션이며, 위험도 계산은 프론트엔드 코드 없이 백엔드 `domain/risk` 패키지
전체로 구현되어 있다(프론트엔드는 `RegionRiskController`가 제공하는 REST API를
소비하는 클라이언트일 뿐이며, 별도 위험도 계산 로직을 갖지 않는다 — `frontend/`
쪽 조사는 본 계획 범위에 포함하지 않았다). 템플릿의 "Option 1/2/3" placeholder는
모두 삭제하고 실제 패키지 트리로 대체했다.

## 복잡도 추적

> 헌법 검사에서 위반 사항이 있어 정당화가 필요한 경우에만 작성

| 위반 사항 | 필요한 이유 | 더 단순한 대안을 거부한 이유 |
|-----------|------------|-------------------------------------|
| [원칙 I 위반 항목 — 위 헌법 검사 표 참고] `RiskCalculationService`가 3개 public 메서드(`recomputeEventRisk`/`recomputeRegionSource`/`propagateEffective`)로 계산을 분리하고, 호출 순서를 리스너/스케줄러에 위임 — 단일 메서드로 합치면 더 간단해 보일 수 있음 | Spring `@Transactional`은 같은 빈 내부의 self-invocation에는 적용되지 않는다(프록시 우회). `recomputeEventRisk` 안에서 `recomputeRegionSource`를 직접 호출하면 감쇠 재계산이 별도 트랜잭션으로 커밋되지 않는다(spec.md FR-034) | 단일 메서드로 합치면 즉시 감쇠 반영은 되지만 트랜잭션 경계가 깨져 부분 실패 시 정합성이 깨진다. 현재 구조는 리스너(외부 빈)가 프록시를 통해 호출하므로 각 단계가 독립 트랜잭션으로 커밋된다(`RiskCalculationService.java:44-55` 클래스 주석에 이 근거가 명시됨) |
| `LlmRiskProfiler`가 `CustomException`이 아닌 `IllegalStateException`을 던짐(원칙 II 위반) | 정당화 없음 — 실수/누락으로 판단됨 | 해당 사항 없음(정당화 대상이 아니라 개선 대상). 향후 PR에서 `ErrorCode.RISK_PROFILE_SEED_MISSING` 같은 전용 코드로 교체 권장 |
