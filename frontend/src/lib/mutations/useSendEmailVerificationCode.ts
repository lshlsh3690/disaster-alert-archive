import { useMutation } from "@tanstack/react-query";
import { sendEmailVerificationCode } from "@/api/authApi";
import { AxiosError } from "axios";
import { ErrorResponse } from "@/types/errorResponse";
import { ApiResponse } from "@/types/ApiResponse";  

export function useSendEmailVerificationCode(options: {
  onSuccessCallback?: () => void;
  onErrorCallback: (errorMessage: string) => void;
}) {
  return useMutation<ApiResponse<null>, AxiosError<ErrorResponse>, string>({
    mutationFn: (email: string) => sendEmailVerificationCode(email),
    onSuccess: () => {
      options.onSuccessCallback?.();
      console.log("이메일 인증 코드가 성공적으로 전송되었습니다.");
    },
    onError: (error: AxiosError<ErrorResponse>) => {
      console.error(`Error`, error);
      const message = error?.response?.data?.message || "이메일 전송 실패";

      options.onErrorCallback(message);
    },
  });
}
