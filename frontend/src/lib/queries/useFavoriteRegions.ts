import { useQuery } from "@tanstack/react-query";
import { getMyFavoriteRegions } from "@/api/memberFavoriteRegionApi";
import { useAuthStore } from "@/store/authStore";
import { useGuestFavoriteRegionsStore } from "@/store/guestFavoriteRegionsStore";
import type { MemberFavoriteRegion } from "@/types/memberFavoriteRegion";

export const FAVORITE_REGIONS_KEY = ["favorite-regions"] as const;

export function useFavoriteRegions() {
  const isLoggedIn = useAuthStore((s) => s.user !== null);
  const guestRegions = useGuestFavoriteRegionsStore((s) => s.regions);

  const serverQuery = useQuery({
    queryKey: FAVORITE_REGIONS_KEY,
    queryFn: async () => {
      const res = await getMyFavoriteRegions();
      return res.data;
    },
    enabled: isLoggedIn,
  });

  if (isLoggedIn) {
    return serverQuery;
  }

  // 비로그인: localStorage 데이터를 Query 형태로 반환
  return {
    data: guestRegions as MemberFavoriteRegion[],
    isLoading: false,
    isError: false,
    isPending: false,
  };
}
