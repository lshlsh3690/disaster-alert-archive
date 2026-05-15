import { useQuery } from "@tanstack/react-query";
import { getMyFavoriteRegions } from "@/api/memberFavoriteRegionApi";

export const FAVORITE_REGIONS_KEY = ["favorite-regions"] as const;

export function useFavoriteRegions() {
  return useQuery({
    queryKey: FAVORITE_REGIONS_KEY,
    queryFn: async () => {
      const res = await getMyFavoriteRegions();
      return res.data;
    },
  });
}
