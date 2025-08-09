import { ErrorResponse } from "@/types/errorResponse";
import { useMutation } from "@tanstack/react-query";
import { AxiosError } from "axios";
import { ApiResponse } from "@/types/ApiResponse";
import { sendNicknameDuplicationCheck } from "@/api/memberApi";

export default function useNicknameDuplicationCheck(options: {
  onSuccessCallback?: () => void;
  onErrorCallback: (errorMessage: string) => void;
}) {
  return useMutation<ApiResponse<null>, AxiosError<ErrorResponse>, string>({
    mutationFn: async (nickname) => await sendNicknameDuplicationCheck(nickname),
    onSuccess: () => {
      options.onSuccessCallback?.();
      console.log("Nickname duplication check successful.");
    },
    onError: (error: AxiosError<ErrorResponse>) => {
      const message = error?.response?.data?.message || "Nickname duplication check failed";
      console.error(`Error`, error);

      options.onErrorCallback(message);
    },
  });
}
