"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  saveDisagreementLabel,
  type SaveDisagreementLabelRequest,
  type SaveDisagreementLabelResponse,
} from "@/api/clusteringAdminApi";

// 불일치 쌍 사람 라벨 저장. 낙관적 업데이트 없이, 저장이 끝나면 불일치 쌍 표와 어려운 쌍 성적을 다시 불러온다(팀 컨벤션).
// 다시 불러오기가 끝날 때까지 저장 중으로 둔다 — 그 전에 버튼이 풀리면 옛 라벨이 보이는 표에서 한 번 더 누르기 쉽다.
export function useSaveDisagreementLabel() {
  const queryClient = useQueryClient();
  return useMutation<SaveDisagreementLabelResponse, Error, SaveDisagreementLabelRequest>({
    mutationFn: (body) => saveDisagreementLabel(body),
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: ["admin", "clustering", "disagreements"] }),
        queryClient.invalidateQueries({ queryKey: ["admin", "clustering", "hard-pair-grades"] }),
      ]),
  });
}
