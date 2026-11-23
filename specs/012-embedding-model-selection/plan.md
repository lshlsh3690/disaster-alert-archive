# 구현 계획: 클러스터링용 임베딩 모델 선정

**브랜치**: `feature/embedding-model-selection` | **날짜**: 2026-11-20 | **명세**: [spec.md](spec.md)

**입력**: `specs/012-embedding-model-selection/spec.md`의 기능 명세

## 요약

지금 임베딩(`text-embedding-3-small`)과 후보 4개(`text-embedding-3-large`, `Qwen3-Embedding-0.6B`, `multilingual-e5-large-instruct`, `BGE-m3-ko`)를 사람 라벨로 비교해 바꿀지 판단할 근거를 만든다. 운영은 바꾸지 않는다.

- **1단계(쌍 거리 실험, 1센트 미만)**: 272쌍 중 임베딩 거리로 병합이 정해지는 137쌍(같은 사건 81·다른 사건 56)만 다시 임베딩한다. "다른 사건은 지금만큼만 붙일 때 같은 사건을 몇 쌍 붙이나"를 두 지점에서 센다. 바로 병합 지점의 기준선은 같은 사건 81쌍 중 19쌍, LLM 후보 지점의 기준선은 70쌍이다.
- **2단계(재클러스터링, 최대 2개, 약 $2)**: 평가 사본의 임베딩을 기존 백필 도구(`--backfill.embed-only`)로 후보 모델로 전부 채운 뒤, 기존 재클러스터링·평가 도구로 점수를 낸다. 운영 코드는 코드에 고정된 병합 거리 0.15 를 기본값 그대로 설정으로 빼는 것 하나만 바꾼다.
- **3단계**: 성능·전환 비용·운영 부담 표를 만들고 사용자가 고른다.

모든 실험은 Java 로, 운영 클러스터링이 쓰는 Spring AI `EmbeddingModel` 그대로 한다. 공개 모델은 TEI(Hugging Face text-embeddings-inference) 컨테이너에 띄워 OpenAI 와 같은 형식의 주소로 부르고, 설정만 바꿔 모델을 고른다(research R3, 2026-11-20 사용자 지시로 Python 안을 기각). 기준선 재현 확인(다시 잰 `3-small` 거리가 저장 거리와 0.01 이내)으로 측정 경로가 운영과 같은지 확인한다.

## 기술 컨텍스트

**언어/버전**: Java 17 / Spring Boot 3.4, Spring AI 1.0.0(측정 모드 추가, 설정화 1건, 기존 재클러스터링·평가 도구)

**주요 의존성**: 새 라이브러리 없음. 공개 모델은 개발용 Docker 컨테이너 TEI(`ghcr.io/huggingface/text-embeddings-inference`, GPU `89-1.9` / CPU `cpu-1.9`)로 띄운다 — 앱 의존성이 아니다

**저장소**: 로컬 Docker Postgres 의 평가 사본(`disaster_alert_eval_v6` 와 그 복제본). 운영 DB 는 읽기 전용 조회(규모 확인)만

**테스트**: JUnit Red-Green — 대상 쌍 고르기(R1)와 두 지점 비교(R2) 순수 클래스, 병합 거리 설정화(R7). 측정 모드 전체는 기준선 재현 확인(SC-002)으로 검증한다

**대상 플랫폼**: 개발 PC(Windows, RTX 4050 6GB, Docker). 맥북 운영 서버는 측정만(조건부, 승인 후)

**프로젝트 유형**: 평가·측정 작업(web-service 저장소 안의 오프라인 도구)

**성능 목표**: 1단계 수 분. 2단계 사본 채우기 62,554건이 GPU 에서 모델당 수십 분 안

**제약사항**: 유료 호출은 명시 승인 후에만, 운영 임베딩 모델·운영 데이터·클러스터링 규칙 불변, 지표는 "N쌍 중 M쌍"과 두 비율·점수만

**규모/범위**: 후보 5개, 1단계 대상 137쌍(알림 수백 건), 2단계 사본 최대 3개(흔들림 확인 1 + 후보 2)

## 헌법 검사

*Phase 0 전 검사와 Phase 1 설계 후 재검사 결과가 같다.*

| 원칙 | 판정 | 근거 |
|---|---|---|
| I. 가독성과 단순성 | 통과 | 새 라이브러리 없음. 모델 선택은 설정만 바꾼다. 2단계 채우기·재클러스터링·평가는 기존 도구 그대로(research R6). 운영 코드 변경은 설정화 1건 |
| II. 계층형 아키텍처 | 해당 없음 | 새 API·계층 없음. 설정화는 기존 service 의 지역 변수를 `@Value` 필드로 옮길 뿐 |
| III. 검증 가능한 변경 | 통과 | 설정화는 Red-Green. 실험은 기준선 재현(저장 거리와 0.01 이내)을 통과해야 결과를 쓴다. 재클러스터링 비교는 흔들림 크기를 먼저 잰다(R8) |
| IV. 정직한 문서화 | 통과 | 기준선 값(19/81·70/81, 1/56·30/56)과 대상 쌍 수는 평가 사본과 라벨 파일에서 직접 쟀다. 리더보드 원자료와 모델 정보는 받은 그대로 커밋했다. 거리 범위 오기(0.249 → 0.300)를 고쳤다 |
| V. 한글 커밋 | 통과 | 단계별 커밋, `commit-message` 스킬 |
| VI. 순수 도메인 규칙 | 통과 | 대상 쌍 고르기·두 지점 비교는 JPA·Spring 을 import 하지 않는 순수 클래스(`evaluation` 패키지). 측정 모드는 조회·임베딩 호출·파일 쓰기만 조율 |
| VII. 맥락 경계 | 해당 없음 | 운영 코드 맥락 간 참조 변화 없음 |
| 기술 스택 제약 "임베딩 = Spring AI 경유 OpenAI" | 통과(이 기능 범위) | 실험만 하고 운영 교체는 하지 않는다. 공개 모델이 선정되면 교체 명세에서 헌법 개정이 선행돼야 한다고 선정 기록에 남긴다 |

## 프로젝트 구조

### 문서 (이 기능)

```text
specs/012-embedding-model-selection/
├── spec.md
├── candidate-selection.md   # 후보 5개를 고른 근거(NLI·STS·PawsX)
├── data/                    # 리더보드 CSV, Hugging Face 모델 정보(받은 그대로)
├── plan.md                  # 이 파일
├── research.md              # 결정 기록 R1~R9
├── data-model.md            # 입력·결과 파일·평가 사본
├── quickstart.md            # 실행·검증 순서
├── contracts/experiment-cli.md
├── checklists/requirements.md
├── decision.md              # 3단계 선정 기록(실험 뒤 작성)
└── tasks.md                 # /speckit-tasks 에서 생성
```

### 소스 코드

```text
backend/src/main/java/com/disaster/alert/alertapi/domain/event/
├── evaluation/EmbeddingPairSubset.java       # 1단계 대상 쌍 고르기(순수, R1)
├── evaluation/EmbeddingPairComparison.java   # 두 지점 비교·바뀐 쌍(순수, R2)
├── evaluation/EmbeddingPairRunner.java       # 측정 모드: 조회 → EmbeddingModel 호출 → 거리 CSV / 요약 리포트
├── evaluation/EvaluationRunTool.java         # --eval.embedding-pairs, --eval.embedding-pairs-summary 추가
└── service/EventClusteringService.java       # 병합 거리 0.15 → clustering.merge-max-distance(기본 0.15)

backend/src/test/java/com/disaster/alert/alertapi/domain/event/
├── evaluation/EmbeddingPairSubsetTest.java   # 순수 단위(Red-Green)
├── evaluation/EmbeddingPairComparisonTest.java
└── service/…                                 # 설정화 Red-Green

backend/evaluation/reports/
├── embedding-pairs-<날짜>/                    # 1단계 모델별 거리 CSV + 요약 리포트
└── 2026-11_<날짜>-embed-<별칭>.md             # 2단계 평가 리포트(기존 도구 출력)

backend/evaluation/README.md                  # 측정 모드·TEI 실행 예시
CLAUDE.md                                     # "0.15 는 코드에 고정" 문구 갱신
```

**구조 결정**: 측정 모드는 기존 평가 도구(`EvaluationRunTool`, `eval` 프로필)에 붙인다. 판사 일치율 측정(`JudgeAgreementRunner`)과 같은 모양이다. 평가 사본·TEI 캐시는 커밋하지 않고, 결과 파일만 커밋한다.

## 구현 순서

| 단계 | 내용 | 승인 지점 |
|---|---|---|
| 1 | `EmbeddingPairSubset`·`EmbeddingPairComparison` 순수 클래스 Red | **사용자 Red 확인** |
| 2 | 1의 Green + 측정 모드(`EmbeddingPairRunner`, `EvaluationRunTool` 옵션) | 코드 리뷰(100줄 이상이면) |
| 3 | TEI 연결 시험, `stored`·공개 모델 3개 측정(무료) | — |
| 4 | OpenAI 2개 측정, 요약 리포트, 기준선 재현 확인, 결과 커밋 | **유료(1센트 미만) 사용자 승인** |
| 5 | 1단계 결과 보고 → 2단계 후보(최대 2개) 확인 | **사용자 확인** |
| 6 | `clustering.merge-max-distance` 설정화 Red | **사용자 Red 확인** |
| 7 | 6의 Green + CLAUDE.md 문구 | — |
| 8 | 흔들림 확인 재클러스터링(v6_rerun) | **유료(약 $0.3) 사용자 승인** |
| 9 | 후보 사본 만들기·`embed-only` 채우기·재클러스터링·평가(후보당) | **유료(`3-large` 채우기 약 $0.8 + 재클러스터링 약 $0.3) 사용자 승인** |
| 10 | 운영 규모 조회(읽기 전용), 공개 모델이 이겼으면 맥북 측정 | **맥북 측정은 사용자 승인** |
| 11 | `decision.md` 표 작성 → 사용자 판정 | **사용자 결정** |

1단계에서 어느 후보도 기준선보다 낫지 않으면 6~10을 건너뛰고 11로 간다. 결론은 "지금 모델 유지"와 그 근거 쌍이다.

## 복잡도 추적

헌법 위반 없음. (첫 판의 Python 스크립트 예외는 2026-11-20 사용자 지시로 Java·TEI 방식으로 바꾸며 없어졌다.)
