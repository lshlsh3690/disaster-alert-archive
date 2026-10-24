# 이벤트 클러스터링 문서

재난문자를 사건 단위 이벤트로 묶는 기능(`domain/event`)의 기준·평가·결과 문서를 모았다.

| 문서 | 내용 |
|---|---|
| [CLUSTERING_CRITERIA.md](CLUSTERING_CRITERIA.md) | 유형별 병합·분리 기준(경로, 키, 윈도우, 임계값). 기준을 바꾸면 이 문서를 먼저 고친다 |
| [CLUSTERING_EVALUATION.md](CLUSTERING_EVALUATION.md) | 데이터 적재 → 검색 → 생성 → 엔드투엔드 단계별 평가 방법과 최신 측정 결과 |
| [CLUSTERING_QUALITY_REPORT.md](CLUSTERING_QUALITY_REPORT.md) | 이전 품질 개선(PR #292~#298) 보고서. 삭제된 개발용 150쌍 기준의 기록 |
| `images/` | 이전 평가 문서의 도식(mermaid 원본은 `images/src/`) |

관련 위치
- 구현 상세(as-built): `specs/001-event-clustering-pipeline/spec.md`, `plan.md`
- 평가 데이터와 측정 스크립트: `backend/evaluation/` (`datasets/`, `eval.sh`, `parse_log.py`, `stages.py`)
