import { deleteFavoriteRegion } from "@/api/memberFavoriteRegionApi";
import { FAVORITE_REGIONS_KEY } from "@/lib/queries/useFavoriteRegions";
import { parseErrorResponse } from "@/types/errorResponse";
import { useAuthStore } from "@/store/authStore";
import { useGuestFavoriteRegionsStore } from "@/store/guestFavoriteRegionsStore";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { AxiosError } from "axios";

export function useDeleteFavoriteRegion(options: {
  onSuccessCallback?: () => void;
  onErrorCallback?: (msg: string) => void;
}) {
  const queryClient = useQueryClient();
  const isLoggedIn = useAuthStore((s) => s.user !== null);
  const guestRemove = useGuestFavoriteRegionsStore((s) => s.remove);

  const serverMutation = useMutation({
    mutationFn: (legalDistrictCode: string) => deleteFavoriteRegion(legalDistrictCode),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: FAVORITE_REGIONS_KEY });
      options.onSuccessCallback?.();
    },
    onError: (error: AxiosError) => {
      const parsed = parseErrorResponse(error.response?.data);
      options.onErrorCallback?.(parsed?.message ?? "관심지역 삭제에 실패했습니다.");
    },
  });

  if (isLoggedIn) return serverMutation;

  // 비로그인: localStorage에서 삭제
  return {
    mutate: (legalDistrictCode: string) => {
      guestRemove(legalDistrictCode);
      options.onSuccessCallback?.();
    },
    isPending: false,
    isError: false,
  };
}
