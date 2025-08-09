import { verifyEmailCode } from "@/api/authApi";
import { ApiResponse } from "@/types/ApiResponse";
import { ErrorResponse } from "@/types/errorResponse";
import { useMutation } from "@tanstack/react-query";
import { AxiosError } from "axios";

export function useEmailCodeVerify(options: {
  onErrorCallback: (errorMessage: string) => void;
  onSuccessCallback?: () => void;
}) {
  return useMutation<ApiResponse<null>, AxiosError<ErrorResponse>, { email: string; code: string }>({
    mutationFn: ({ email, code }) => verifyEmailCode(email, code),
    onSuccess: () => {
      options.onSuccessCallback?.();
      console.log("Verification code sent successfully.");
    },
    onError: (error: AxiosError<ErrorResponse>) => {
      const message = error?.response?.data?.message || "Failed to send verification code";

      console.warn("Error status:", error.response?.data?.status);
      console.warn("Error code:", error.response?.data?.code);

      options.onErrorCallback(message);
    },
  });
}
