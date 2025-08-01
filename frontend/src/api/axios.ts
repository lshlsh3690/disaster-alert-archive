import axios from "axios";
import { useAuthStore } from "store/auth";

const instance = axios.create({
  baseURL: process.env.BASE_API_URL || "http://localhost:8080", // 환경 변수에서 API URL 가져오기
  timeout: 5000,
  withCredentials: true, // 쿠키 기반 인증 시 필요
});

// 요청 시 토큰 자동 주입
instance.interceptors.request.use(
  (config) => {
    const token = useAuthStore.getState().accessToken;
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// 응답 에러 처리 (예: 401 시 refresh 등)
instance.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      // 여기에 refresh token 처리 로직 추가 가능
      console.warn("401 Unauthorized. Token 만료 혹은 로그인 필요.");
    }
    return Promise.reject(error);
  }
);

export default instance;
