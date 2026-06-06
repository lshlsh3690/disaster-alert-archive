import { useEffect, useRef } from "react";
import { useAuthStore } from "@/store/authStore";
import { useGuestFavoriteRegionsStore } from "@/store/guestFavoriteRegionsStore";
import { addFavoriteRegion, getMyFavoriteRegions } from "@/api/memberFavoriteRegionApi";
import { useQueryClient } from "@tanstack/react-query";
import { FAVORITE_REGIONS_KEY } from "@/lib/queries/useFavoriteRegions";

/**
 * 로그인 직후 localStorage의 게스트 즐겨찾기를 서버에 병합한다.
 * 이미 서버에 있는 항목은 건너뛰고, 최대 5개 제한을 지킨다.
 */
export function useGuestFavoriteSync() {
  const isLoggedIn = useAuthStore((s) => s.user !== null);
  const guestRegions = useGuestFavoriteRegionsStore((s) => s.regions);
  const clearGuest = useGuestFavoriteRegionsStore((s) => s.clear);
  const queryClient = useQueryClient();
  const syncedRef = useRef(false);

  useEffect(() => {
    if (!isLoggedIn || guestRegions.length === 0 || syncedRef.current) return;

    syncedRef.current = true;

    (async () => {
      try {
        const res = await getMyFavoriteRegions();
        const serverCodes = new Set(res.data.map((r) => r.legalDistrictCode));

        const toSync = guestRegions.filter((r) => !serverCodes.has(r.legalDistrictCode));
        for (const region of toSync) {
          try {
            await addFavoriteRegion({ legalDistrictCode: region.legalDistrictCode });
          } catch {
            // 한도 초과 등 개별 실패는 조용히 무시
          }
        }

        clearGuest();
        queryClient.invalidateQueries({ queryKey: FAVORITE_REGIONS_KEY });
      } catch {
        syncedRef.current = false;
      }
    })();
  }, [isLoggedIn, guestRegions, clearGuest, queryClient]);
}
