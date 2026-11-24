import { useQuery, keepPreviousData } from "@tanstack/react-query";
import { isAxiosError } from "axios";
import {
  fetchClusteringDailyCheckAlerts,
  fetchEvalLabelDatasets,
  fetchEvalLabelHistory,
  fetchEvalLabels,
  fetchClusteringDailyChecks,
  fetchClusteringDailyScores,
  fetchClusteringDisagreements,
  fetchClusteringJudgeVersions,
  fetchHardPairGrades,
  type ClusteringDisagreementsParams,
  type EvalLabelsParams,
} from "@/api/clusteringAdminApi";

// 클러스터링 관리자 조회 훅. 키는 ["admin", "clustering", 이름, ...인자] 튜플.
// 권한 검사는 여기서 하지 않는다 — app/admin/clustering/layout.tsx 가 ADMIN 일 때만 화면(영역 컴포넌트)을 마운트한다.

// 4xx(범위 오류·권한 없음)는 다시 보내도 같은 답이라 재시도하지 않는다. 그 밖의 실패는 기본처럼 3번까지
function retryUnlessClientError(failureCount: number, error: unknown) {
  const status = isAxiosError(error) ? error.response?.status : undefined;
  if (status !== undefined && status >= 400 && status < 500) return false;
  return failureCount < 3;
}

export function useClusteringDailyScores(from: string, to: string) {
  return useQuery({
    queryKey: ["admin", "clustering", "daily-scores", from, to],
    queryFn: () => fetchClusteringDailyScores(from, to),
    retry: retryUnlessClientError,
  });
}

export function useClusteringDisagreements(params: ClusteringDisagreementsParams) {
  const { from, to, kind = null, page = 0, size = 20, unlabeledOnly = false } = params;
  return useQuery({
    queryKey: ["admin", "clustering", "disagreements", from, to, kind, page, size, unlabeledOnly],
    queryFn: () => fetchClusteringDisagreements({ from, to, kind, page, size, unlabeledOnly }),
    placeholderData: keepPreviousData, // 페이지를 넘길 때 표가 깜빡이며 비지 않게
    retry: retryUnlessClientError,
  });
}

export function useClusteringDailyChecks(from: string, to: string) {
  return useQuery({
    queryKey: ["admin", "clustering", "daily-checks", from, to],
    queryFn: () => fetchClusteringDailyChecks(from, to),
    retry: retryUnlessClientError,
  });
}

// 구조 검사 펼침 칸 안에서만 쓴다 — 칸이 펼친 날에만 그려지므로 펼치지 않은 날은 요청하지 않는다
export function useClusteringDailyCheckAlerts(date: string) {
  return useQuery({
    queryKey: ["admin", "clustering", "daily-check-alerts", date],
    queryFn: () => fetchClusteringDailyCheckAlerts(date),
    retry: retryUnlessClientError,
  });
}

export function useHardPairGrades() {
  return useQuery({
    queryKey: ["admin", "clustering", "hard-pair-grades"],
    queryFn: () => fetchHardPairGrades(),
    retry: retryUnlessClientError,
  });
}

export function useClusteringJudgeVersions() {
  return useQuery({
    queryKey: ["admin", "clustering", "judge-versions"],
    queryFn: () => fetchClusteringJudgeVersions(),
    retry: retryUnlessClientError,
  });
}

// ---- 평가 셋 라벨 ----
// 키는 모두 ["admin", "clustering", "eval-labels", ...] 로 시작한다 — 라벨을 고친 뒤 이 접두어 하나로 목록·셋 요약·이력을 다시 불러온다

export function useEvalLabelDatasets() {
  return useQuery({
    queryKey: ["admin", "clustering", "eval-labels", "datasets"],
    queryFn: () => fetchEvalLabelDatasets(),
    retry: retryUnlessClientError,
  });
}

// 셋을 아직 고르지 않았으면(셋 목록을 불러오는 중) 요청하지 않는다 — 서버는 셋 이름이 필수다
export function useEvalLabels(params: EvalLabelsParams | null) {
  const { dataset = "", label = null, page = 0, size = 20 } = params ?? {};
  return useQuery({
    queryKey: ["admin", "clustering", "eval-labels", "list", dataset, label, page, size],
    queryFn: () => fetchEvalLabels({ dataset, label, page, size }),
    enabled: dataset !== "",
    placeholderData: keepPreviousData, // 페이지를 넘길 때 표가 깜빡이며 비지 않게
    retry: retryUnlessClientError,
  });
}

// 이력 칸을 펼친 줄에서만 그려지므로 펼치지 않은 줄은 요청하지 않는다
export function useEvalLabelHistory(id: number) {
  return useQuery({
    queryKey: ["admin", "clustering", "eval-labels", "history", id],
    queryFn: () => fetchEvalLabelHistory(id),
    retry: retryUnlessClientError,
  });
}
