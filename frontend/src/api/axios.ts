import axios, { AxiosError, InternalAxiosRequestConfig } from "axios";
import { reissue } from "./authApi";

const baseURL = process.env.NEXT_PUBLIC_API_BASE_URL;

console.log("baseURL", baseURL);
if (!baseURL) {
  throw new Error("NEXT_PUBLIC_API_BASE_URL is not set");
}

const instance = axios.create({
  baseURL: baseURL || undefined,
  withCredentials: true,
});

// 전역 플래그 & 대기열
let isRefreshing = false;
let waitQueue: Array<() => void> = [];

// 유틸: 재발급/로그인 요청 판별
const isReissueRequest = (config?: InternalAxiosRequestConfig) =>
  !!config?.url && config.url.includes("/api/v1/auth/reissue");

const isAuthLoginRequest = (config?: InternalAxiosRequestConfig) =>
  !!config?.url && config.url.includes("/api/v1/auth/login");

// 응답 에러 처리 (예: 401 시 refresh 등)
instance.interceptors.response.use(
  (res) => res,
  async (error: AxiosError) => {
    const original = error.config as InternalAxiosRequestConfig & {
      _retry?: boolean;
    };

    // 1) 재발급 요청 자체는 재시도 금지 (여기서 루프 끊음)
    if (isReissueRequest(original)) {
      return Promise.reject(error);
    }
    if (isAuthLoginRequest(original)) {
      // 로그인 요청은 재시도하지 않음
      return Promise.reject(error);
    }

    // 2) 이미 재시도한 요청은 더 이상 건드리지 않음
    if (original?._retry) {
      return Promise.reject(error);
    }

    // 3) 401만 재발급 시도 (단, 인증이 필요한 요청에 한함)
    if (error.response?.status === 401) {
      const authRequired = original?.headers?.["X-Auth-Required"] === "true";
      if (!authRequired) {
        // 공개 API 요청이면 재발급 시도하지 않고 그대로 실패 반환
        return Promise.reject(error);
      }
      if (isRefreshing) {
        // 이미 재발급 중이면 대기 → 완료 후 원요청 1회 재시도
        await new Promise<void>((resolve) => waitQueue.push(resolve));
        original._retry = true;
        return instance(original);
      }

      try {
        isRefreshing = true;

        // 쿠키 기반이므로 바디 불필요
        await reissue();

        // 대기중이던 요청 깨우기
        waitQueue.forEach((resolve) => resolve());
        waitQueue = [];

        // 원요청 1회만 재시도
        original._retry = true;
        return instance(original);
      } catch (e) {
        // 재발급 실패: 큐 정리 후 그대로 실패시켜 상위에서 로그아웃/알림 처리
        waitQueue = [];
        return Promise.reject(e);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  }
);

export default instance;
