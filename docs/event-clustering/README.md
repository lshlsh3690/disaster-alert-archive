# 이벤트 클러스터링 문서

재난문자를 사건 단위 이벤트로 묶는 기능(`domain/event`)의 기준·평가·결과 문서를 모았다.

| 문서 | 내용 |
|---|---|
| [CLUSTERING_CRITERIA.md](CLUSTERING_CRITERIA.md) | 유형별 병합·분리 기준(경로, 키, 윈도우, 임계값). 기준을 바꾸면 이 문서를 먼저 고친다 |
| [CLUSTERING_EVALUATION.md](CLUSTERING_EVALUATION.md) | 데이터 적재 → 검색 → 생성 → 엔드투엔드 단계별 평가 방법과 최신 측정 결과 |
| [CLUSTERING_EVAL_GUIDE.md](CLUSTERING_EVAL_GUIDE.md) | 평가 도구(`EvaluationRunTool`) 읽는 법·실행법 — 점수·순도·완성도와 이전 대비(diff). 튜닝 전후 비교는 이 도구로 한다 |
| [ADMIN_EVAL_DASHBOARD_DESIGN.md](ADMIN_EVAL_DASHBOARD_DESIGN.md) | 관리자 페이지에서 단계별 성능을 운영 데이터로 보려던 설계안. **보류** — 근거로 삼던 판정 기록(`clustering_trace`)을 2026-11-08 에 들어내 검색·생성 단계는 출처를 잃었다 |
| [CLUSTERING_QUALITY_REPORT.md](CLUSTERING_QUALITY_REPORT.md) | 이전 품질 개선(PR #292~#298) 보고서. 삭제된 개발용 150쌍 기준의 기록 |
| `images/` | 이전 평가 문서의 도식(mermaid 원본은 `images/src/`) |

관련 위치
- 구현 상세(as-built): `specs/001-event-clustering-pipeline/spec.md`, `plan.md`
- 평가 데이터: `backend/evaluation/datasets/`. 분류기 평가 도구는 `AlertKindEvalTool`(backend, 프로필 `alert-kind-eval`)
