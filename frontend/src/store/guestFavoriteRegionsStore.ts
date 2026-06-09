import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { MemberFavoriteRegion } from "@/types/memberFavoriteRegion";

const MAX_REGIONS = 5;

type GuestFavoriteRegionsStore = {
  regions: MemberFavoriteRegion[];
  add: (region: MemberFavoriteRegion) => { success: boolean; message?: string };
  remove: (legalDistrictCode: string) => void;
  clear: () => void;
};

export const useGuestFavoriteRegionsStore = create<GuestFavoriteRegionsStore>()(
  persist(
    (set, get) => ({
      regions: [],
      add: (region) => {
        const { regions } = get();
        if (regions.some((r) => r.legalDistrictCode === region.legalDistrictCode)) {
          return { success: false, message: "이미 등록된 관심 지역입니다." };
        }
        if (regions.length >= MAX_REGIONS) {
          return { success: false, message: `관심지역은 최대 ${MAX_REGIONS}개까지 등록할 수 있습니다.` };
        }
        set({ regions: [...regions, { ...region, createdAt: new Date().toISOString() }] });
        return { success: true };
      },
      remove: (legalDistrictCode) =>
        set((s) => ({ regions: s.regions.filter((r) => r.legalDistrictCode !== legalDistrictCode) })),
      clear: () => set({ regions: [] }),
    }),
    { name: "disaster-alert-guest-favorites" }
  )
);
