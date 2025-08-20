import { loginApi } from "@/api/authApi";
import { parseErrorResponse } from "@/types/errorResponse";
import { useAuthStore } from "@/store/authStore";
import { useMutation } from "@tanstack/react-query";
import { AxiosError } from "axios";
import {SuccessResponse } from "@/types/successResponse";

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
  return useMutation<SuccessResponse<LoginResponseBody>, AxiosError, LoginFormData>({
    mutationFn: async ({ email, password }) => {
      return await loginApi(email, password);
    },
    onSuccess: (response) => {
      const { memberId, nickname, email } = response.data;
      useAuthStore.getState().setUser({
        memberId,
        nickname,
        email,
      });

      options.onSuccessCallback?.();
    },
    onError: (error: AxiosError) => {
      console.error("로그인 실패:", error);
      const errorResponse = parseErrorResponse(error.response?.data);
      if (!errorResponse) {
        options.onErrorCallback("알 수 없는 오류가 발생했습니다.");
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
