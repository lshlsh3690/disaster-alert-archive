import axios, { AxiosError, InternalAxiosRequestConfig } from "axios";
import { reissue } from "./authApi";

const instance = axios.create({
  baseURL: process.env.BASE_API_URL || "http://localhost:8080", // 환경 변수에서 API URL 가져오기
  withCredentials: true, // 쿠키 기반 인증 시 필요
});


// 전역 플래그 & 대기열
let isRefreshing = false;
let waitQueue: Array<() => void> = [];

// 유틸: 재발급 요청인지 판별
const isReissueRequest = (config?: InternalAxiosRequestConfig) =>
  !!config?.url && config.url.includes("/api/v1/auth/reissue");

// 응답 에러 처리 (예: 401 시 refresh 등)
instance.interceptors.response.use(
  (res) => res,
  async (error: AxiosError) => {
    const original = error.config as InternalAxiosRequestConfig & { _retry?: boolean };

    // 1) 재발급 요청 자체는 재시도 금지 (여기서 루프 끊음)
    if (isReissueRequest(original)) {
      return Promise.reject(error);
    }

    // 2) 이미 재시도한 요청은 더 이상 건드리지 않음
    if (original?._retry) {
      return Promise.reject(error);
    }

    // 3) 401만 재발급 시도
    if (error.response?.status === 401) {
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

