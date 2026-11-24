"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { updateEvalLabel, type EvalLabelResponse, type UpdateEvalLabelRequest } from "@/api/clusteringAdminApi";

// 평가 셋 라벨 수정. 낙관적 업데이트 없이, 저장이 끝나면 평가 셋 라벨 쪽 쿼리(목록·셋 요약·이력)를 모두 다시 불러온다(팀 컨벤션).
// 다시 불러오기가 끝날 때까지 저장 중으로 둔다 — 그 전에 버튼이 풀리면 옛 라벨이 보이는 표에서 한 번 더 누르기 쉽다.
export function useUpdateEvalLabel() {
  const queryClient = useQueryClient();
  return useMutation<EvalLabelResponse, Error, { id: number; body: UpdateEvalLabelRequest }>({
    mutationFn: ({ id, body }) => updateEvalLabel(id, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["admin", "clustering", "eval-labels"] }),
  });
}
