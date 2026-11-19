// frontend/src/lib/mutations/useLogin.ts
import { loginApi } from "@/api/authApi";
import { parseErrorResponse } from "@/types/errorResponse";
import { useAuthStore } from "@/store/authStore";
import { useMutation } from "@tanstack/react-query";
import { AxiosError } from "axios";
import { SuccessResponse } from "@/types/SuccessResponse";
import { getMyInfo } from "@/api/memberApi";
import { useTranslation } from "react-i18next";

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
  const { t } = useTranslation();
  return useMutation<SuccessResponse<LoginResponseBody>, AxiosError, LoginFormData>({
    mutationFn: async ({ email, password }) => {
      return await loginApi(email, password);
    },
    onSuccess: async (response) => {
      const { memberId, nickname, email } = response.data;

      // 로그인 응답에는 role 이 없어 내 정보를 한 번 더 읽는다. 실패하면 role 없이 진행한다
      // (다음 새로고침 때 useInitAuth 가 다시 채운다) — 관리자 메뉴가 새로고침 전까지 안 보이던 문제
      const role = await getMyInfo()
        .then((me) => me.data.role ?? null)
        .catch(() => null);
      useAuthStore.getState().setUser({ memberId, nickname, email, role });
      options.onSuccessCallback?.();
    },
    onError: (error: AxiosError) => {
      console.error("로그인 실패:", error);
      const errorResponse = parseErrorResponse(error.response?.data);
      if (!errorResponse) {
        options.onErrorCallback(t("login.unknownError"));
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