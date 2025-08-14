import { signupApi } from "@/api/authApi";
import useSignupStore from "@/store/signupStore";
import { useMutation } from "@tanstack/react-query";
import { AxiosError } from "axios";
import { UseFormSetError } from "react-hook-form";
import { SignupFormData } from "@/schemas/signupSchema";
import { SuccessResponse } from "@/types/SuccessResponse";
import { z } from "zod";
import { parseErrorResponse } from "@/schemas/errorResponseSchema";
import { makeMutationFn } from "@/utils/makeMutationFn";

interface UseSignupMutationProps {
  setError: UseFormSetError<SignupFormData>;
  onSuccessCallback?: () => void;
  onErrorCallback: (errorMessage: string) => void;
}

interface SignupApiResponse {
  memberId: number;
  message: string;
}

export default function useSignup(options: UseSignupMutationProps) {
  const dataSchema = z.object({
    memberId: z.number(),
    message: z.string(),
  });


  return useMutation<SuccessResponse<SignupApiResponse>, AxiosError, SignupFormData>({
    mutationFn: async(data)  => {
      useSignupStore.getState().validateSignupStateForForm(options.setError);
      return await makeMutationFn<SignupFormData, SignupApiResponse>(
        signupApi,
        dataSchema
      )(data);
    },
    onSuccess: () => {
      options.onSuccessCallback?.();
    },
    onError: (error: AxiosError) => {
      const errorResponse = parseErrorResponse(error.response?.data);
      if (!errorResponse) {
        options.onErrorCallback("알 수 없는 오류가 발생했습니다.");
        return;
      }

      // 일반적인 오류 처리
      const { message, key, code, field } = errorResponse!;
      const keyOrCode = key ?? code;

      // field 우선 매핑
      if (field) {
        options.setError(field as keyof SignupFormData, { message });
        options.onErrorCallback(message);
        return;
      }

      switch (keyOrCode) {
        case "DUPLICATE_EMAIL":
        case "M401":
          options.setError("email", { message });
          break;
        case "DUPLICATE_NICKNAME":
        case "M402":
          options.setError("nickname", { message });
          break;
        case "EMAIL_NOT_VERIFIED":
        case "A102":
          options.setError("email", { message });
          break;
        case "PASSWORD_MISMATCH":
        case "A103":
          options.setError("password", { message });
          options.setError("confirmPassword", { message });
          break;
      }
      options.onErrorCallback(message);
    },
  });
}
