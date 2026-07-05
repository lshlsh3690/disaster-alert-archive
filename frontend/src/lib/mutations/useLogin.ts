// frontend/src/lib/mutations/useLogin.ts
import { loginApi } from "@/api/authApi";
import { parseErrorResponse } from "@/types/errorResponse";
import { useAuthStore } from "@/store/authStore";
import { useMutation } from "@tanstack/react-query";
import { AxiosError } from "axios";
import { SuccessResponse } from "@/types/SuccessResponse";
import { getMyInfo } from "@/api/memberApi";
import { useI18n } from "@/hooks/useI18n";

interface LoginFormData {
  email: string;
  password: string;
}

interface LoginResponseBody {
  accessToken: string;
  refreshToken: string;
  memberId: number;
  nickname: string;
  email: string;
}

export default function useLogin(options: {
  onSuccessCallback?: () => void;
  onErrorCallback: (errorMessage: string) => void;
}) {
  const t = useI18n();
  return useMutation<SuccessResponse<LoginResponseBody>, AxiosError, LoginFormData>({
    mutationFn: async ({ email, password }) => {
      return await loginApi(email, password);
    },
    onSuccess: async (response) => {
      const { memberId, nickname, email } = response.data;

      useAuthStore.getState().setUser({ memberId, nickname, email, role: null });
      options.onSuccessCallback?.();
    },
    onError: (error: AxiosError) => {
      console.error("로그인 실패:", error);
      const errorResponse = parseErrorResponse(error.response?.data);
      if (!errorResponse) {
        options.onErrorCallback(t.login.unknownError);
        return;
      }

      const { message, field } = errorResponse;
      if (field) {
        options.onErrorCallback(message);
        return;
      }

      options.onErrorCallback(message!);
    },
  });
}