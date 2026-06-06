import { useEffect, useRef } from "react";
import { useAuthStore } from "@/store/authStore";
import { useGuestFavoriteRegionsStore } from "@/store/guestFavoriteRegionsStore";
import { addFavoriteRegion, getMyFavoriteRegions } from "@/api/memberFavoriteRegionApi";
import { linkGuestFcmToken } from "@/api/guestFcmApi";
import { useQueryClient } from "@tanstack/react-query";
import { FAVORITE_REGIONS_KEY } from "@/lib/queries/useFavoriteRegions";

/**
 * 로그인 직후 두 가지를 처리한다:
 * 1. localStorage의 게스트 관심지역을 서버에 병합
 * 2. localStorage의 게스트 FCM 토큰을 회원과 연결
 */
export function useGuestFavoriteSync() {
  const isLoggedIn = useAuthStore((s) => s.user !== null);
  const guestRegions = useGuestFavoriteRegionsStore((s) => s.regions);
  const clearGuest = useGuestFavoriteRegionsStore((s) => s.clear);
  const queryClient = useQueryClient();
  const syncedRef = useRef(false);

  useEffect(() => {
    if (!isLoggedIn || syncedRef.current) return;
    if (guestRegions.length === 0 && !localStorage.getItem("fcm-token")) return;

    syncedRef.current = true;

    (async () => {
      try {
        // 1. 관심지역 병합
        if (guestRegions.length > 0) {
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
        }

        // 2. 게스트 FCM 토큰 회원 연결
        const guestToken = localStorage.getItem("fcm-token");
        if (guestToken) {
          await linkGuestFcmToken(guestToken);
        }
      } catch {
        syncedRef.current = false;
      }
    })();
  }, [isLoggedIn, guestRegions, clearGuest, queryClient]);
}
