import { useMutation } from "@tanstack/react-query";
import { sendEmailVerificationCode } from "@/api/authApi";
import { AxiosError } from "axios";
import { SuccessResponse } from "@/types/successResponse";
import { makeMutationFn } from "@/utils/makeMutationFn";
import { z } from "zod";
import { parseErrorResponse } from "@/types/errorResponse";
import { FieldValues, Path, UseFormSetError } from "react-hook-form";

export function useSendEmailVerificationCode<T extends FieldValues>(options: {
  setError: UseFormSetError<T>;
  onSuccessCallback?: () => void;
  onErrorCallback: (errorMessage: string) => void;
}) {
  const dataSchema = z.null();

  return useMutation<SuccessResponse<null>, AxiosError, string>({
    mutationFn: async (email) => makeMutationFn<string, null>(sendEmailVerificationCode, dataSchema)(email),
    onSuccess: () => {
      options.onSuccessCallback?.();
      console.log("이메일 인증 코드가 성공적으로 전송되었습니다.");
    },
    onError: (error: AxiosError) => {
      const errorResponse = parseErrorResponse(error.response?.data);
      if (!errorResponse) {
        // 에러 응답을 파싱할 수 없을 때
        console.error("알 수 없는 오류가 발생했습니다.", error);
        options.onErrorCallback("알 수 없는 오류가 발생했습니다.");
        return;
      }
      const { message, field } = errorResponse!;

      console.error(`Error: ${message}`, errorResponse);
      if (field) {
        console.error(`Field: ${field}`);
        options.setError(field as Path<T>, { message });
        options.onErrorCallback(message);
        return;
      }

      options.onErrorCallback(message);
    },
  });
}
