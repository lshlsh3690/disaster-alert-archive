import { useMutation } from "@tanstack/react-query";
import { AxiosError } from "axios";
import { sendNicknameDuplicationCheck } from "@/api/memberApi";
import { SuccessResponse } from "@/types/successResponse";
import { makeMutationFn } from "@/utils/makeMutationFn";
import { z } from "zod";
import { parseErrorResponse } from "@/types/errorResponse";

export default function useNicknameDuplicationCheck(options: {
  onSuccessCallback?: () => void;
  onErrorCallback: (errorMessage: string) => void;
}) {
  const resBodySchema = z.null();

  return useMutation<SuccessResponse<null>, AxiosError, string>({
    mutationFn: (nickname) => makeMutationFn(sendNicknameDuplicationCheck, resBodySchema)(nickname),
    onSuccess: () => {
      options.onSuccessCallback?.();
    },
    onError: (error: AxiosError) => {
      const errorResponse = parseErrorResponse(error.response?.data);
      if (!errorResponse) {
        // 에러 응답을 파싱할 수 없을 때
        console.error("알 수 없는 오류가 발생했습니다.", error);
        options.onErrorCallback("알 수 없는 오류가 발생했습니다.");
        return;
      }

      const { message, field } = errorResponse;
      if (field) {
        console.error(`Field: ${field}`);
        options.onErrorCallback(message);
        return;
      }
      options.onErrorCallback(message!);
    },
  });
}
