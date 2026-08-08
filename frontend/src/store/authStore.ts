import { create } from "zustand";
import { persist } from "zustand/middleware";

type User = {
  memberId: number | null;
  nickname: string | null;
  email: string | null;
  role: "ADMIN" | "USER" | null;
};

type AuthStore = {
  user: User | null;
  // useInitAuth가 쿠키 기반 로그인 상태를 서버에서 복원 중인 동안 true.
  // 페이지의 "로그인 안 됐으면 /login으로" 가드는 이 값이 false가 될
  // 때까지 기다려야 한다 — 그렇지 않으면 새로고침/딥링크 시 user가 아직
  // null인 첫 렌더에서 곧바로 리다이렉트돼버린다(실제로 로그인 상태라도).
  isInitializing: boolean;
  setUser: (user: User) => void;
  setInitializing: (isInitializing: boolean) => void;
  logout: () => void;
};

export const useAuthStore = create<AuthStore>()(
  persist(
    (set) => ({
      user: null,
      isInitializing: true,
      setUser: (user) => set({ user }),
      setInitializing: (isInitializing) => set({ isInitializing }),
      logout: () => set({ user: null }),
    }),
    {
      name: "disaster-alert-auth",
      // isInitializing은 항상 새 로드마다 true로 시작해야 하므로 저장하지 않는다.
      partialize: (state) => ({ user: state.user }),
    },
  ),
);
