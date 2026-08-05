# 구현 계획: 이벤트 클러스터링 파이프라인 (as-built)

**브랜치**: `001-event-clustering-pipeline` | **날짜**: 2026-08-05 | **명세**: [spec.md](./spec.md)

**입력**: `specs/001-event-clustering-pipeline/spec.md`의 기능 명세

**참고**: 이 계획 역시 spec.md와 마찬가지로 **사후 문서화(as-built)** 다. "Phase 0/1 설계 산출물을 만든다"는 원래 워크플로우 대신, 이미 구현된 코드를 근거로 실제 아키텍처 결정과 그 근거를 역기술한다. research.md/data-model.md/contracts/quickstart.md 같은 Phase 산출물은 이번 문서화 범위에서 별도로 생성하지 않았다(아래 "프로젝트 구조" 참고).

## 요약

이벤트 클러스터링 파이프라인은 원본 재난문자(`disaster_alert`)를 "사건"(`disaster_event`) 단위로 자동 그룹핑하는 백그라운드 서브시스템이다. 핵심 요구사항(spec.md FR-001~FR-037)은 다음 기술 접근으로 구현돼 있다:

- **임베딩 기반 로컬 클러스터링**을 기본 경로로 삼되(OpenAI `text-embedding-3-small` + pgvector 코사인 유사도, 같은 시군구·7일 윈도우·임계값 0.85), 실측 데이터에서 드러난 실패 모드마다 **결정적 규칙 기반 특수 경로를 우선 분기**시켜 임베딩 경로 앞에서 가로챈다: 인물 신원(IDENTITY) → 전국 통합 유형(GLOBAL_TYPE) → 지역앵커 유형(REGIONAL_TYPE) → (그 다음 광역 span 게이트) → 로컬 임베딩(EMBEDDING) → LLM 폴백(LLM_FALLBACK).
- **광역/전국 브로드캐스트**는 별도 판정(시군구 span > 10, 시도 span 기준 8)으로 로컬 이벤트와 영구히 분리해(`is_broadcast` 플래그) 다지역 blob을 원천 차단한다.
- **동물 등 비정형 이동 사건**은 로컬 클러스터링 이후 별도 서비스(`EventCrossRegionService`)가 종(species) 하드게이트 + 지역 인접 + LLM(`gpt-4o-mini`) 판정으로 사후 병합한다.
- **비용이 드는 LLM 경로(cross-region, local borderline fallback)는 모두 기본 비활성(`enabled=false`) 플래그 뒤에 있다.**
- **"진행 중" 상태는 저장하지 않고 조회 시점에 유형별 cooldown으로 파생 계산**해, 행안부가 주지 않는 "사건 종료" 신호를 대체한다.
- **백필 도구**(`backfill` 프로파일 전용)가 임베딩 생성과 클러스터링을 분리해, 임계값 튜닝 시 OpenAI 재호출 없이 반복 재구성할 수 있게 한다.

## 기술 컨텍스트

**언어/버전**: Java 17+ / Spring Boot 3.x (다른 백엔드 도메인과 동일 프로젝트, 별도 버전 아님)

**주요 의존성**: Spring AI(OpenAI `text-embedding-3-small` 임베딩 + `gpt-4o-mini` 챗 모델), Spring Data JPA + QueryDSL, pgvector(PostgreSQL 확장), Spring `ApplicationEventPublisher`(도메인 이벤트 발행)

**저장소**: PostgreSQL(pgvector) — `disaster_events`, `event_alert_mapping`, `disaster_alert.embedding`(vector(1536)), `region_adjacency`

**테스트**: 없음 — `backend/src/test/java/.../domain/event/` 디렉터리 자체가 존재하지 않는다(전체 backend 테스트 8개 파일 중 이 도메인은 0개). `@SpringBootTest` + `.env.test` 통합 테스트 관례가 프로젝트에 있으나 이 서브시스템에는 적용된 바 없다. (아래 헌법 검사 III 참고.)

**대상 플랫폼**: 백엔드 서버 프로세스 내부 백그라운드 파이프라인(HTTP API 아님) — `@Scheduled` 스케줄러(10분 주기)와 `ApplicationRunner`(백필, `backfill` 프로파일 전용)로만 트리거됨. 사용자에게 직접 노출되는 컨트롤러 엔드포인트는 없다(조회는 `EventQueryService`/`EventController`가 담당하며 이 파이프라인의 산출물을 읽기만 함 — 별도 스코프).

**성능 목표**: 코드/설정에 명시된 목표치 없음. 백필 도구 주석에 "OpenAI 임베딩 배치 200건씩, 전체 4.3만 건이 분 단위"(일화적 관찰치, SLA 아님)라는 실측 경험치가 있을 뿐, SLA로 정의된 값은 아니다 (`EventClusteringBackfillTool.java:31`).

**제약사항**: LLM 폴백/cross-region은 호출당 비용이 발생해(`gpt-4o-mini`) 기본 비활성. 임베딩은 알림당 1회만 생성해 재사용(재클러스터링 비용 절감). 클러스터링 실패가 재난문자 수집·번역·FCM 발송 사이클을 막아서는 안 된다(각 진입점 try/catch 격리).

**규모/범위**: 이 문서 작성 시점 기준 정량 데이터 없음(운영 지표 미수집, spec.md SC 섹션 참고). 코드 주석에 남은 유일한 규모 근거는 튜닝 당시 실측 사례(산불 6,639건 등)로, 지속 집계되는 값이 아니다.

## 헌법 검사

*게이트: Phase 0 research 이전에 통과해야 함. Phase 1 design 이후 재검사.*

> as-built 문서화라 "게이트 통과 여부로 진행을 막는" 절차적 의미는 없다. 대신 `.specify/memory/constitution.md`의 5개 원칙에 **실제 코드가 부합하는지를 코드 대조로 검증**하고, 위반이 발견되면 정직하게 기록한다(원칙 IV 정직한 문서화에 따라 은폐하지 않음).

| 원칙 | 결과 | 근거 |
|---|---|---|
| **I. 가독성과 단순성 우선** (메서드 30~50줄, 과도한 추상화 금지) | ⚠️ 부분 위반 | `EventClusteringService.doCluster()`가 179~255행으로 약 76줄이며(공백/주석 포함), 인물→전국유형→지역앵커유형→광역→임베딩→LLM폴백까지 6단계 분기를 한 메서드가 순차 처리한다(`EventClusteringService.java:179-255`). `EventCrossRegionService.linkAnimal()`도 133~199행으로 약 67줄이며(종 게이트·인접 게이트·시간 게이트·LLM 호출·span-cap 체크가 한 메서드에 순차 누적) 같은 방식으로 가이드라인을 초과한다(`EventCrossRegionService.java:133-199`). 그 외 개별 헬퍼 메서드(`tryLlmFallback` 42줄, `clusterRegionalType` 38줄, `clusterBroadcast` 40줄 등)는 가이드라인 안에 든다. 새 라이브러리 도입은 없음(Spring AI는 이미 risk 모듈과 공유하는 기존 의존성) — 이 부분은 원칙에 부합. |
| **II. 계층형 아키텍처 준수** (controller→service→repository, ApiResponse/ErrorCode, setter 대신 엔티티 메서드) | ⚠️ 부분 위반 | (1) 이 파이프라인에는 controller가 없다(스케줄러/ApplicationRunner 트리거) — 계층 자체가 원칙이 상정한 API 요청-응답 흐름과 다른 형태라 `ApiResponse`/`CustomException`+`ErrorCode` 패턴이 애초에 적용 대상이 아니다(정상적인 예외; 실제로 `domain/event` 전체에서 `CustomException`을 쓰는 곳은 조회 전용인 `EventQueryService`뿐이다). (2) **엔티티 메서드 원칙은 실질적으로 위반**: `DisasterEvent.recordMergedAlert(LocalDateTime)`이라는 엔티티 메서드가 정의돼 있지만(`DisasterEvent.java:262-267`) 어디서도 호출되지 않는 죽은 코드이고, 실제 `last_alert_at`/`alert_count` 갱신은 서비스가 아니라 리포지토리의 네이티브 SQL `UPDATE`(`incrementOnMerge`, `recomputeAggregates`, `updateTitleIfEquals`)가 수행한다(`DisasterEventRepository.java:267-291`, `465-477`). JPA 영속성 컨텍스트를 우회하는 이 방식은 동시성/명시성 이유로 의도된 선택이라는 주석(`DisasterEventRepository.java:264-266`)이 있어 실용적 트레이드오프로 보이나, "도메인 상태 변경은 setter가 아닌 엔티티 메서드로"라는 원칙의 문언과는 어긋난다. |
| **III. 검증 가능한 변경** (비즈니스 로직 단위 테스트, DB 경계 통합 테스트) | ❌ 위반 | `backend/src/test/java/com/disaster/alert/alertapi/domain/event/` 디렉터리가 존재하지 않는다 — 유사도 임계값 판정, cooldown 분류, 안내성/사건 분류(`FireAlertClassifier`), 신원 추출(`MissingPersonIdentity`/`AnimalIdentity`) 등 순수 함수 로직조차 목킹 없는 단위 테스트가 전무하다. 실측 검증은 `EventClusteringBackfillTool`을 통한 수동 재클러스터링 + 로그 관찰로 대체되고 있다(코드 주석에 다수의 실측 사례가 남아있음). 이는 원칙 III("비즈니스 로직은 단위 테스트로 우선 검증")과 명백히 배치된다. |
| **IV. 정직한 문서화** | N/A (이 문서 자체가 원칙 준수 시도) | 이 spec.md/plan.md 작성 과정에서 `CLAUDE.md`가 기술한 `EventFragmentMergeService`, `ClusteringProperties`, `incident-specific-check`, "BFS 확산 hop" 등이 실제로는 존재하지 않거나 다른 모듈(risk) 소관임을 코드 대조로 확인해 spec.md "발견된 문서-코드 불일치" 절에 기록했다. |
| **V. 한글 커밋 컨벤션** | 해당 없음 | 이 문서화 작업 자체는 커밋 메시지를 생성하지 않음(작업 지시 범위 밖). |

> **참고(브랜치 명명 규칙 불일치)**: 이 문서의 브랜치명 `001-event-clustering-pipeline`은 constitution.md 개발 워크플로우가 요구하는 `feature/`/`fix/`/`chore/`/`docs/`/`ci/` 접두사 중 어느 것도 쓰지 않는다 — speckit 도구의 번호-슬러그 명명 규칙을 그대로 따른 것으로, 실제 git 브랜치로 만들어지지 않는 스펙 문서 식별자이기 때문에 이 문서 작성 시점에는 별도로 맞추지 않았다. 이 기능에 대한 실제 구현 브랜치를 팔 때는 기존 접두사 규칙(`feature/`/`fix/` 등)을 따라야 한다.

**결론**: 게이트를 "통과/차단"하는 문서가 아니라 실태 보고이므로 진행을 막지 않는다. 다만 원칙 III(테스트 부재)과 원칙 II(엔티티 메서드 우회)는 향후 이 파이프라인에 변경을 가할 때 우선 해소를 고려할 가치가 있는 실질적 부채로 기록해 둔다.

## 프로젝트 구조

### 문서 (이 기능)

```text
specs/001-event-clustering-pipeline/
├── plan.md              # 이 파일 — as-built 계획
└── spec.md              # as-built 명세 (사용자 스토리 9개, FR-001~FR-037)
```

> research.md / data-model.md / contracts/ / quickstart.md는 생성하지 않았다. 이 기능은 신규 설계가 아니라 이미 병합·운영 중인 코드를 사후 서술하는 것이라, "설계 결정을 위한 리서치"나 "구현 전 계약(contract) 정의"가 시간순으로 존재하지 않기 때문이다 — 대신 spec.md의 각 FR/시나리오 자체가 코드에서 역추출한 data-model/contract 정보를 겸한다.

### 소스 코드 (저장소 루트)

**구조 결정**: 이 저장소는 `backend/`(Spring Boot) + `frontend/`(Next.js) 투트랙 구조이며, 이벤트 클러스터링 파이프라인은 전적으로 `backend` 내부의 백그라운드 서브시스템이다(프론트엔드 소비자는 클러스터링 결과를 읽기만 하는 `EventQueryService`/`EventController` 경유로, 이 파이프라인 자체의 구조에는 속하지 않는다). 아래는 실제 경로다.

```text
backend/src/main/java/com/disaster/alert/alertapi/
├── scheduler/
│   └── DisasterFetchScheduler.java          # 진입점 — 10분 cron, 알림 저장 후 클러스터링 호출
├── domain/event/
│   ├── service/
│   │   ├── EventClusteringService.java      # 메인 클러스터링 (로컬 임베딩 + 인물/전국유형/지역앵커/광역 분기)
│   │   ├── EventCrossRegionService.java     # 동물 등 비정형 이동사건 cross-region LLM 병합
│   │   ├── EventLLMDecisionService.java     # LLM 판정 공용 호출부 (Spring AI ChatModel, gpt-4o-mini)
│   │   ├── EventQueryService.java           # [스코프 밖] 조회 전용 — 클러스터링 산출물을 읽기만 함
│   │   └── EventTranslationService.java     # [스코프 밖] 이벤트 제목/타임라인 번역
│   ├── model/
│   │   ├── DisasterEvent.java               # 이벤트 엔티티 — cooldown/active 파생, 제목 자동생성
│   │   ├── EventAlertMapping.java           # 알림-이벤트 매핑 (merge_method, similarity)
│   │   ├── MergeMethod.java                 # 병합 방식 enum (감사 추적용)
│   │   ├── DisasterCooldown.java            # 유형→cooldown 시간 정적 룩업
│   │   ├── FireAlertClassifier.java         # 산불 사건/안내성 분류 규칙
│   │   ├── MissingPersonIdentity.java       # 실종자 이름/나이/키 정규식 추출
│   │   └── AnimalIdentity.java              # 동물 종 식별 사전 + 인접 단위(시군구/시도) 분류
│   ├── repository/
│   │   ├── DisasterEventRepository.java     # 후보 검색 native query 전체(코사인/시군구/인접/신원)
│   │   ├── EventAlertMappingRepository.java # 매핑 CRUD + cross-region 재배정
│   │   └── AlertEmbeddingRepository.java    # disaster_alert.embedding 저장/조회
│   └── tool/
│       └── EventClusteringBackfillTool.java # backfill 프로파일 전용 재처리 도구
├── domain/disasteralert/                    # [스코프 밖, 의존] 원본 재난문자 엔티티/저장
├── domain/risk/
│   ├── event/AlertClusteredEvent.java       # [경계] 클러스터링→위험도 연결 도메인 이벤트
│   └── listener/ClusteringEventListener.java # [스코프 밖] 위험도 계산 트리거 (별도 서브시스템)
└── domain/legaldistrict/                    # [스코프 밖, 의존] 법정동 코드/인접(region_adjacency) 마스터

backend/src/main/resources/
└── application.yml                          # clustering.* 설정 블록 (123-163행)

backend/src/test/java/com/disaster/alert/alertapi/domain/event/  # 부재 — 헌법 검사 III 참고
```

## 복잡도 추적

> 헌법 검사에서 발견된 위반 사항 중, "정당화 필요"가 아니라 "실태를 있는 그대로 기록"하는 as-built 문서 특성상 아래처럼 정리한다(향후 리팩터링 우선순위 판단 자료).

| 위반 사항 | 왜 이렇게 됐는지(코드 근거) | 완전 해소가 간단하지 않은 이유 |
|-----------|------------|-------------------------------------|
| `EventClusteringService.doCluster()`가 50줄 가이드라인 초과(~76줄) | 인물→전국유형→지역앵커유형→광역span→로컬임베딩→LLM폴백의 6단계 분기가 실측 실패 사례 발견 순서대로 순차 추가돼 누적됨(각 분기 도입 배경이 메서드 내 상세 주석으로 남아 있음) | 각 분기가 "앞 단계에서 처리 안 됐으면 다음으로" 라는 조기 반환(guard clause) 체인이라 단순 추출 시 상태(후보 검색 결과, 지역 코드 등) 전달이 늘어나 오히려 가독성이 떨어질 수 있음 — 분리하려면 전략 패턴 등 추상화 도입이 필요한데, 이는 원칙 I("불필요한 추상화 금지")과 충돌할 여지가 있어 트레이드오프 판단이 필요 |
| `DisasterEvent.recordMergedAlert()` 죽은 코드 + 네이티브 UPDATE로 상태 변경 | 동시성(다중 스레드/프로세스에서의 경합) 및 "증분 UPDATE"의 명시성을 위해 JPA dirty checking 대신 원자적 SQL을 의도적으로 선택(리포지토리 주석에 명시) | 엔티티 메서드로 되돌리면 낙관적 락 없이는 동시 병합 시 `alert_count` 증분이 유실될 수 있어, 단순 원칙 준수가 오히려 데이터 정합성 리스크를 만듦 — 낙관적/비관적 락 도입 등 별도 설계가 선행돼야 함 |
| `domain/event` 테스트 0건 | 클러스터링 튜닝이 `EventClusteringBackfillTool` + 실 DB 재구성 + 로그 관찰이라는 "준통합" 워크플로우로 이미 자리잡아, 별도 단위 테스트 없이도 임계값 튜닝 사이클이 돌아가고 있었음(운영상 급한 필요가 없었던 것으로 추정 — 코드에서 확인 가능한 사실은 아님) | 순수 로직(`FireAlertClassifier`, `MissingPersonIdentity`, `AnimalIdentity`, `DisasterCooldown`)은 외부 의존성이 없어 단위 테스트 도입 자체는 기술적으로 쉬움 — 반면 `EventClusteringService`/`EventCrossRegionService`의 분기 로직은 리포지토리 native query·`EmbeddingModel`·`ChatModel`에 강결합돼 있어 원칙 III가 요구하는 "외부 의존성 목킹" 테스트를 짜려면 상당한 리팩터링(인터페이스 분리 등)이 필요함 |
