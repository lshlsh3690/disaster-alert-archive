import { QueryClient } from "@tanstack/react-query";

// 기본값(staleTime: 0, refetchOnWindowFocus: true)이 페이지 재방문·탭 전환마다
// 불필요한 재요청을 유발해 전역 기본 staleTime을 지정. 실시간성이 필요한 쿼리는
// 훅 단에서 개별적으로 더 짧은 staleTime을 지정해 오버라이드한다.
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      refetchOnWindowFocus: false,
    },
  },
});