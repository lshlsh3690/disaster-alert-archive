import { create } from "zustand";
import { persist } from "zustand/middleware";

type User = {
  memberId: number | null;
  nickname: string | null;
  email: string | null;
};

type AuthStore = {
  user: User | null;
  setUser: (user: User) => void;
  logout: () => void;
};

export const useAuthStore = create<AuthStore>()(
  persist(
    (set) => ({
      user: null,
      setUser: (user) => set({ user }),
      logout: () => set({ user: null }),
    }),
    {
      name: "disaster-alert-auth",
    }
  )
);
