import { deleteFavoriteRegion } from "@/api/memberFavoriteRegionApi";
import { FAVORITE_REGIONS_KEY } from "@/lib/queries/useFavoriteRegions";
import { parseErrorResponse } from "@/types/errorResponse";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { AxiosError } from "axios";

export function useDeleteFavoriteRegion(options: {
  onSuccessCallback?: () => void;
  onErrorCallback?: (msg: string) => void;
}) {
  const queryClient = useQueryClient();

  return useMutation({
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
}
