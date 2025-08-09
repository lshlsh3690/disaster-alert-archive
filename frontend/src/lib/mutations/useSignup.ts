import { signupApi } from "@/api/authApi";
import useSignupStore from "@/store/signupStore";
import { ApiResponse } from "@/types/ApiResponse";
import { ErrorResponse } from "@/types/errorResponse";
import { SignupFormData } from "@/types/signup";
import { useMutation } from "@tanstack/react-query";
import { AxiosError } from "axios";
import { UseFormSetError } from "react-hook-form";

/**
 * Custom hook for handling user signup mutation.
 *
 * @param options - Options for the mutation, including error and success callbacks.
 * @returns The mutation object for signup.
 */
interface UseSignupMutationProps {
  setError: UseFormSetError<SignupFormData>;
  onSuccessCallback?: () => void;
  onErrorCallback: (errorMessage: string) => void;
}

export default function useSignup(options: UseSignupMutationProps) {
  return useMutation<ApiResponse<null>, AxiosError<ErrorResponse>, SignupFormData>({
    mutationFn: async (data) => {
      // 회원가입 formdata 유효성 전체 검사
      const isValid = useSignupStore.getState().validateSignupStateForForm(options.setError);

      if (!isValid) {
        console.error("회원가입 상태 미충족", isValid);
        options.onErrorCallback("회원가입 상태 미충족");
        throw new Error("회원가입 상태 미충족");
      }

      return await signupApi(data);
    },
    onSuccess: () => {
      options.onSuccessCallback?.();
      console.log("Signup successful.");
    },
    onError: (error: AxiosError<ErrorResponse>) => {
      if (error.message === "회원가입 상태 미충족") return;

      const code = error?.response?.data?.code;
      const message = error?.response?.data?.message || "회원가입 실패";

      switch (String(code)) {
        case "DUPLICATE_EMAIL":
          options.setError("email", { message });
          break;
        case "DUPLICATE_NICKNAME":
          options.setError("nickname", { message });
          break;
        default:
          alert(message);
      }

      options.onErrorCallback(message);
    },
  });
}
