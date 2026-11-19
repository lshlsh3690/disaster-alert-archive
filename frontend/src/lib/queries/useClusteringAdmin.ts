import { useQuery, keepPreviousData } from "@tanstack/react-query";
import { isAxiosError } from "axios";
import {
  fetchClusteringDailyCheckAlerts,
  fetchClusteringDailyChecks,
  fetchClusteringDailyScores,
  fetchClusteringDisagreements,
  fetchClusteringJudgeVersions,
  type ClusteringDisagreementsParams,
} from "@/api/clusteringAdminApi";

// 클러스터링 관리자 조회 훅. 키는 ["admin", "clustering", 이름, ...인자] 튜플.
// 권한 검사는 여기서 하지 않는다 — app/admin/clustering/page.tsx 가 ADMIN 일 때만 영역 컴포넌트를 마운트한다.

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
  const { from, to, kind = null, page = 0, size = 20 } = params;
  return useQuery({
    queryKey: ["admin", "clustering", "disagreements", from, to, kind, page, size],
    queryFn: () => fetchClusteringDisagreements({ from, to, kind, page, size }),
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

// 구조 검사 행을 펼쳤을 때만 부른다(enabled). 펼치지 않은 날은 요청하지 않는다
export function useClusteringDailyCheckAlerts(date: string, enabled: boolean) {
  return useQuery({
    queryKey: ["admin", "clustering", "daily-check-alerts", date],
    queryFn: () => fetchClusteringDailyCheckAlerts(date),
    enabled,
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
