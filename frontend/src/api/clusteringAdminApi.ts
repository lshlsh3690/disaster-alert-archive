// 클러스터링 운영 검증 관리자 조회 API 래퍼(계약: specs/008-clustering-admin-dashboard/contracts/admin-clustering-api.md).
// DTO 필드명은 백엔드 ClusteringDashboard*Response record 와 정확히 같다.
// 서버는 ApiResponse({success, message, code, data}) 로 감싸 보내므로 안쪽 data 만 꺼내 돌려준다.
import instance from "./axios";

const BASE = "/api/v1/admin/clustering";
// 인증이 필요한 요청 표시 — axios 인터셉터는 이 헤더가 있을 때만 401 에 토큰 재발급 후 1회 재시도한다.
// 없으면 화면을 연 채 access 토큰이 만료됐을 때 기간을 바꾸면 영역에 오류만 남는다
const AUTH = { "X-Auth-Required": "true" };

/** 비율. 분모가 0 이면 백엔드가 percent 를 null 로 내려보낸다(키는 있다). */
export type Ratio = {
  numerator: number;
  denominator: number;
  percent: number | null;
};

/** 일일 채점 날짜의 상태. NO_RECORD 는 날짜와 상태만 온다. */
export type DayStatus = "SCORED" | "NO_PAIRS" | "NO_RECORD";

/** 불일치 쌍 종류. 과병합 = OVER_MERGED, 과소병합 = MISSED. */
export type DisagreementKind = "OVER_MERGED" | "MISSED";

/**
 * 불일치 쌍의 판사 판정. 백엔드 enum 에는 FORMAT_FAILURE 도 있지만 불일치(과병합·과소병합)는 판사가 SAME/DIFF 로
 * 답한 쌍에서만 생겨 이 응답에는 오지 않는다. UNSURE 도 같은 이유로 실제로는 오지 않으나 계약 문서에 맞춰 둔다.
 */
export type JudgeVerdict = "SAME" | "DIFF" | "UNSURE";

export type ClusteringJudge = {
  version: string;
  model?: string | null; // 판사 버전 목록에 없는 버전이면 비어 있다
  sameAgreement: Ratio;
  diffAgreement: Ratio;
};

/** 계산할 수 없는 값과 기록 없는 날은 키가 생략되므로 날짜·상태 외에는 모두 선택값이다. */
export type ClusteringDailyScoreDay = {
  date: string;
  status: DayStatus;
  incidentCount?: number;
  pairCount?: number;
  correctlyMerged?: number;
  overMerged?: number;
  missed?: number;
  correctlySeparated?: number;
  unsureCount?: number;
  formatFailureCount?: number;
  purity?: Ratio;
  completeness?: Ratio;
  separation?: Ratio;
  balancedAccuracy?: number;
  score?: number;
  judge?: ClusteringJudge;
  promptTokens?: number;
  completionTokens?: number;
  estimatedCostUsd?: number;
};

export type ClusteringDailyScoresResponse = {
  from: string;
  to: string;
  days: ClusteringDailyScoreDay[];
};

/** 알림을 찾을 수 없으면 id 만 오고 나머지는 null(화면은 "원문 없음"). */
export type ClusteringAlertSummary = {
  id: number;
  sentAt: string | null;
  type: string | null;
  message: string | null;
};

/** 사람 라벨(정답). 불일치 쌍마다 관리자가 매긴다. */
export type HumanLabel = "SAME" | "DIFF" | "UNSURE";

/** 사람 라벨을 정답으로 봤을 때 틀린 쪽. */
export type WrongSide = "JUDGE" | "SYSTEM";

export type ClusteringDisagreementPair = {
  date: string;
  kind: DisagreementKind;
  judgeVerdict: JudgeVerdict;
  alertA: ClusteringAlertSummary;
  alertB: ClusteringAlertSummary;
  eventA: number | null;
  eventB: number | null;
  humanLabel: HumanLabel | null; // 라벨이 없으면 null
  criteriaDate: string | null; // 라벨을 매길 때의 기준 시점
  wrongSide: WrongSide | null; // 라벨이 없거나 모르겠음이면 null
};

export type ClusteringDisagreementsResponse = {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  pairs: ClusteringDisagreementPair[];
};

export type ClusteringDisagreementsParams = {
  from: string; // yyyy-MM-dd (KST 날짜)
  to: string; // yyyy-MM-dd
  kind?: DisagreementKind | null; // 생략하면 두 종류 모두
  page?: number; // 기본 0
  size?: number; // 1~100, 기본 20
  unlabeledOnly?: boolean; // true 면 사람 라벨이 없는 쌍만
};

export type ClusteringDailyCheckDay = {
  date: string;
  recorded: boolean;
  incidentCount?: number;
  unassignedCount?: number;
  noEmbeddingCount?: number;
  attention?: boolean;
};

export type ClusteringDailyChecksResponse = {
  from: string;
  to: string;
  days: ClusteringDailyCheckDay[];
};

/**
 * 일치율은 그 버전 최신 측정의 쌍별 판정 × 현재 사람 라벨(라벨을 고치면 바뀜). 합계만 옮긴 옛 측정이면 recomputable=false.
 * 측정이 없거나 셀 수 없는 버전, 목록에 없는 버전은 measuredOn·일치율·recomputable·unmeasuredPairs 가 비어 있다.
 */
export type ClusteringJudgeVersion = {
  version: string;
  model: string | null;
  measuredOn: string | null;
  sameAgreement: Ratio | null;
  diffAgreement: Ratio | null;
  recomputable: boolean | null; // 라벨이 바뀌면 다시 계산되는 값인가
  unmeasuredPairs: number | null; // 셋에 SAME/DIFF 라벨이 있는데 그 측정에 판정이 없는 쌍 수
  firstScoredDate: string | null;
  lastScoredDate: string | null;
  scoredDays: number;
};

export type ClusteringJudgeVersionsResponse = {
  versions: ClusteringJudgeVersion[];
};

/** 날짜별 일일 채점(최신 날짜 먼저). */
export async function fetchClusteringDailyScores(from: string, to: string): Promise<ClusteringDailyScoresResponse> {
  const res = await instance.get(`${BASE}/daily-scores`, { params: { from, to }, headers: AUTH });
  return res.data.data as ClusteringDailyScoresResponse;
}

/** 불일치 쌍(날짜 내림차순 → 쌍 id 오름차순). kind 가 없으면 파라미터를 빼서 두 종류 모두 받는다. */
export async function fetchClusteringDisagreements(
  params: ClusteringDisagreementsParams,
): Promise<ClusteringDisagreementsResponse> {
  const { from, to, kind, page = 0, size = 20, unlabeledOnly = false } = params;
  const res = await instance.get(`${BASE}/disagreements`, {
    params: { from, to, page, size, ...(kind ? { kind } : {}), ...(unlabeledOnly ? { unlabeledOnly } : {}) },
    headers: AUTH,
  });
  return res.data.data as ClusteringDisagreementsResponse;
}

/** 날짜별 구조 검사(미배정·임베딩 없음). */
export async function fetchClusteringDailyChecks(from: string, to: string): Promise<ClusteringDailyChecksResponse> {
  const res = await instance.get(`${BASE}/daily-checks`, { params: { from, to }, headers: AUTH });
  return res.data.data as ClusteringDailyChecksResponse;
}

/** 구조 검사에서 문제가 된 사건 알림 하나(미배정 또는 임베딩 없음). 미배정이면 eventId·mergeMethod 가 null. */
export type ClusteringDailyCheckAlert = {
  id: number;
  sentAt: string;
  type: string | null;
  message: string | null;
  unassigned: boolean;
  noEmbedding: boolean;
  eventId: number | null;
  mergeMethod: string | null;
};

export type ClusteringDailyCheckAlertsResponse = {
  date: string;
  alerts: ClusteringDailyCheckAlert[];
};

/** 그날의 문제 사건 알림(발송 시각 순). 숫자는 검사 당시 값이고 이 목록은 지금 조회한 값이다. */
export async function fetchClusteringDailyCheckAlerts(date: string): Promise<ClusteringDailyCheckAlertsResponse> {
  const res = await instance.get(`${BASE}/daily-checks/${date}/alerts`, { headers: AUTH });
  return res.data.data as ClusteringDailyCheckAlertsResponse;
}

export type SaveDisagreementLabelRequest = { alertA: number; alertB: number; label: HumanLabel };

export type SaveDisagreementLabelResponse = {
  alertA: number;
  alertB: number;
  label: HumanLabel;
  criteriaDate: string;
  updatedAt: string;
};

/** 불일치 쌍에 사람 라벨을 저장한다(이미 있으면 덮어쓴다). 불일치로 기록된 적 없는 쌍이면 404. */
export async function saveDisagreementLabel(body: SaveDisagreementLabelRequest): Promise<SaveDisagreementLabelResponse> {
  const res = await instance.put(`${BASE}/disagreement-labels`, body, { headers: AUTH });
  return res.data.data as SaveDisagreementLabelResponse;
}

/** 판사 버전 하나의 어려운 쌍(불일치 쌍 사람 라벨 기준) 성적. 목록에 없는 버전이면 model 이 null. */
export type HardPairGradeVersion = {
  version: string;
  model: string | null;
  judgeRight: Ratio;
  systemRight: Ratio;
  unsureCount: number;
};

/** labeledPairs 는 서로 다른 쌍 수가 아니라 쌍×날짜 수(같은 쌍이 여러 날 불일치면 날짜마다 판정). */
export type HardPairGradesResponse = {
  labeledPairs: number;
  versions: HardPairGradeVersion[];
};

export async function fetchHardPairGrades(): Promise<HardPairGradesResponse> {
  const res = await instance.get(`${BASE}/hard-pair-grades`, { headers: AUTH });
  return res.data.data as HardPairGradesResponse;
}

/** 판사 버전 이력(최신 버전 먼저). */
export async function fetchClusteringJudgeVersions(): Promise<ClusteringJudgeVersionsResponse> {
  const res = await instance.get(`${BASE}/judge-versions`, { headers: AUTH });
  return res.data.data as ClusteringJudgeVersionsResponse;
}

// ---- 평가 셋 라벨(evaluation_pair_label) — 클러스터링 평가의 정답을 관리자 화면에서 보고 고친다 ----

/** 두 알림의 지역 관계. 옛 행에는 없어 null 일 수 있다. */
export type EvalLabelRelation = "SAME_SIGUNGU" | "ADJACENT_SIGUNGU";

/** 평가 셋 하나의 요약. lastUpdatedAt 은 라벨을 고친 적 없으면 null. */
export type EvalLabelDataset = {
  name: string;
  pairs: number;
  same: number;
  diff: number;
  unsure: number;
  lastUpdatedAt: string | null;
};

export type EvalLabelDatasetsResponse = { datasets: EvalLabelDataset[] };

/** 라벨 쌍 하나. criteriaDate 는 라벨을 매긴(마지막으로 고친) 때의 판정 기준 시점, updatedAt 은 고친 적 없으면 null. */
export type EvalLabelPair = {
  id: number;
  relation: EvalLabelRelation | null;
  label: HumanLabel;
  criteriaDate: string | null;
  updatedAt: string | null;
  alertA: ClusteringAlertSummary;
  alertB: ClusteringAlertSummary;
};

/** labelCounts 는 label 필터와 상관없이 그 셋 전체 기준. */
export type EvalLabelsResponse = {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  labelCounts: { same: number; diff: number; unsure: number };
  pairs: EvalLabelPair[];
};

export type EvalLabelsParams = { dataset: string; label?: HumanLabel | null; page?: number; size?: number };

/** 사유는 필수(공백만이면 서버가 400). 기준 시점·수정자는 서버가 붙인다. */
export type UpdateEvalLabelRequest = { label: HumanLabel; reason: string };

export type EvalLabelResponse = { id: number; label: HumanLabel; criteriaDate: string | null; updatedAt: string | null };

/** 변경 한 번. changedBy 는 바꾼 관리자의 회원 id. */
export type EvalLabelChange = { oldLabel: HumanLabel; newLabel: HumanLabel; reason: string; changedBy: number; changedAt: string };

export type EvalLabelHistoryResponse = { labelId: number; changes: EvalLabelChange[] };

/** 평가 셋 목록(이름순). */
export async function fetchEvalLabelDatasets(): Promise<EvalLabelDatasetsResponse> {
  const res = await instance.get(`${BASE}/eval-labels/datasets`, { headers: AUTH });
  return res.data.data as EvalLabelDatasetsResponse;
}

/** 평가 셋 하나의 라벨 목록(넣은 순서). label 이 없으면 파라미터를 빼서 모든 라벨을 받는다. */
export async function fetchEvalLabels(params: EvalLabelsParams): Promise<EvalLabelsResponse> {
  const { dataset, label, page = 0, size = 20 } = params;
  const res = await instance.get(`${BASE}/eval-labels`, {
    params: { dataset, page, size, ...(label ? { label } : {}) },
    headers: AUTH,
  });
  return res.data.data as EvalLabelsResponse;
}

/** 라벨을 고친다. 같은 라벨이면 서버가 아무것도 바꾸지 않고 현재 값을 돌려준다. */
export async function updateEvalLabel(id: number, body: UpdateEvalLabelRequest): Promise<EvalLabelResponse> {
  const res = await instance.put(`${BASE}/eval-labels/${id}`, body, { headers: AUTH });
  return res.data.data as EvalLabelResponse;
}

/** 라벨 하나의 변경 이력(최신 먼저). */
export async function fetchEvalLabelHistory(id: number): Promise<EvalLabelHistoryResponse> {
  const res = await instance.get(`${BASE}/eval-labels/${id}/history`, { headers: AUTH });
  return res.data.data as EvalLabelHistoryResponse;
}
