import { verifyEmailCode } from "@/api/authApi";
import { parseErrorResponse } from "@/types/errorResponse";
import { makeMutationFn } from "@/utils/makeMutationFn";
import { useMutation } from "@tanstack/react-query";
import { AxiosError } from "axios";
import { SuccessResponse } from "@/types/successResponse";
import { z } from "zod";

interface reqParams {
  email: string;
  code: string;
}

export function useEmailCodeVerify(options: {
  onErrorCallback: (errorMessage: string) => void;
  onSuccessCallback?: () => void;
}) {
  const resBodySchema = z.null();

  return useMutation<SuccessResponse<null>, AxiosError, reqParams>({
    mutationFn: ({ email, code }) =>
      makeMutationFn<reqParams, null>(
        ({ email, code }) => verifyEmailCode(email, code),
        resBodySchema
      )({ email, code }),
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
