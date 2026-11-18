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
  unsureCount?: number;
  formatFailureCount?: number;
  purity?: Ratio;
  completeness?: Ratio;
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

export type ClusteringDisagreementPair = {
  date: string;
  kind: DisagreementKind;
  judgeVerdict: JudgeVerdict;
  alertA: ClusteringAlertSummary;
  alertB: ClusteringAlertSummary;
  eventA: number | null;
  eventB: number | null;
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

/** 채점 기록에는 있는데 판사 버전 목록에 없는 버전은 model·measuredOn·일치율이 비어 있다. */
export type ClusteringJudgeVersion = {
  version: string;
  model: string | null;
  measuredOn: string | null;
  sameAgreement: Ratio | null;
  diffAgreement: Ratio | null;
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
  const { from, to, kind, page = 0, size = 20 } = params;
  const res = await instance.get(`${BASE}/disagreements`, {
    params: { from, to, page, size, ...(kind ? { kind } : {}) },
    headers: AUTH,
  });
  return res.data.data as ClusteringDisagreementsResponse;
}

/** 날짜별 구조 검사(미배정·임베딩 없음). */
export async function fetchClusteringDailyChecks(from: string, to: string): Promise<ClusteringDailyChecksResponse> {
  const res = await instance.get(`${BASE}/daily-checks`, { params: { from, to }, headers: AUTH });
  return res.data.data as ClusteringDailyChecksResponse;
}

/** 판사 버전 이력(최신 버전 먼저). */
export async function fetchClusteringJudgeVersions(): Promise<ClusteringJudgeVersionsResponse> {
  const res = await instance.get(`${BASE}/judge-versions`, { headers: AUTH });
  return res.data.data as ClusteringJudgeVersionsResponse;
}
