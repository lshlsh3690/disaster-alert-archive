import { useQuery, keepPreviousData } from "@tanstack/react-query";
import {
  fetchClusteringDailyChecks,
  fetchClusteringDailyScores,
  fetchClusteringDisagreements,
  fetchClusteringJudgeVersions,
  type ClusteringDisagreementsParams,
} from "@/api/clusteringAdminApi";

// 클러스터링 관리자 조회 훅. 키는 ["admin", "clustering", 이름, ...인자] 튜플.
// enabled 는 호출하는 쪽(ADMIN 이 아니면 요청 자체를 보내지 않음)이 넘긴다.

export function useClusteringDailyScores(from: string, to: string, enabled = true) {
  return useQuery({
    queryKey: ["admin", "clustering", "daily-scores", from, to],
    queryFn: () => fetchClusteringDailyScores(from, to),
    enabled,
  });
}

export function useClusteringDisagreements(params: ClusteringDisagreementsParams, enabled = true) {
  const { from, to, kind = null, page = 0, size = 20 } = params;
  return useQuery({
    queryKey: ["admin", "clustering", "disagreements", from, to, kind, page, size],
    queryFn: () => fetchClusteringDisagreements({ from, to, kind, page, size }),
    placeholderData: keepPreviousData, // 페이지를 넘길 때 표가 깜빡이며 비지 않게
    enabled,
  });
}

export function useClusteringDailyChecks(from: string, to: string, enabled = true) {
  return useQuery({
    queryKey: ["admin", "clustering", "daily-checks", from, to],
    queryFn: () => fetchClusteringDailyChecks(from, to),
    enabled,
  });
}

export function useClusteringJudgeVersions(enabled = true) {
  return useQuery({
    queryKey: ["admin", "clustering", "judge-versions"],
    queryFn: () => fetchClusteringJudgeVersions(),
    enabled,
  });
}
