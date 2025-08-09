import { create } from "zustand";
import { persist, createJSONStorage } from "zustand/middleware";

type AuthState = {
  accessToken: string | null;
  memberId: number | null;
  nickname: string | null;
  email: string | null;
  setAuth: (auth: {
    accessToken: string;
    memberId: number;
    nickname: string;
    email: string;
  }) => void;
  logout: () => void;
};

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      memberId: null,
      nickname: null,
      email: null,
      setAuth: ({ accessToken, memberId, nickname, email }) =>
        set({ accessToken, memberId, nickname, email }),
      logout: () =>
        set({
          accessToken: null,
          memberId: null,
          nickname: null,
          email: null,
        }),
    }),
    {
      name: "disaster-auth", // localStorage 키
      storage: createJSONStorage(() => localStorage),
    }
  )
);
