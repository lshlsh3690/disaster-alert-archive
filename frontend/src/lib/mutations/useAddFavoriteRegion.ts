import { addFavoriteRegion } from "@/api/memberFavoriteRegionApi";
import { FAVORITE_REGIONS_KEY } from "@/lib/queries/useFavoriteRegions";
import { parseErrorResponse } from "@/types/errorResponse";
import { useAuthStore } from "@/store/authStore";
import { useGuestFavoriteRegionsStore } from "@/store/guestFavoriteRegionsStore";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { AxiosError } from "axios";

export function useAddFavoriteRegion(options: {
  onSuccessCallback?: () => void;
  onErrorCallback?: (msg: string) => void;
  getRegionName?: (code: string) => string;
}) {
  const queryClient = useQueryClient();
  const isLoggedIn = useAuthStore((s) => s.user !== null);
  const guestAdd = useGuestFavoriteRegionsStore((s) => s.add);

  const serverMutation = useMutation({
    mutationFn: (legalDistrictCode: string) => addFavoriteRegion({ legalDistrictCode }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: FAVORITE_REGIONS_KEY });
      options.onSuccessCallback?.();
    },
    onError: (error: AxiosError) => {
      const parsed = parseErrorResponse(error.response?.data);
      options.onErrorCallback?.(parsed?.message ?? "관심지역 추가에 실패했습니다.");
    },
  });

  if (isLoggedIn) return serverMutation;

  // 비로그인: localStorage에 저장
  return {
    mutate: (legalDistrictCode: string) => {
      const regionName = options.getRegionName?.(legalDistrictCode) ?? legalDistrictCode;
      const result = guestAdd({ legalDistrictCode, regionName });
      if (result.success) {
        options.onSuccessCallback?.();
      } else {
        options.onErrorCallback?.(result.message ?? "관심지역 추가에 실패했습니다.");
      }
    },
    isPending: false,
    isError: false,
  };
}
