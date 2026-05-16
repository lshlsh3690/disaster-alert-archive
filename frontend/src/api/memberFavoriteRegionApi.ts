import instance from "@/api/axios";
import { SuccessResponse } from "@/types/SuccessResponse";
import { AddFavoriteRegionRequest, MemberFavoriteRegion } from "@/types/memberFavoriteRegion";

const BASE = "/api/v1/members/me/favorite-regions";

const AUTH_HEADER = { headers: { "X-Auth-Required": "true" } };

export const getMyFavoriteRegions = async (): Promise<SuccessResponse<MemberFavoriteRegion[]>> => {
  const res = await instance.get(BASE, AUTH_HEADER);
  return res.data;
};

export const addFavoriteRegion = async (
  body: AddFavoriteRegionRequest,
): Promise<SuccessResponse<MemberFavoriteRegion>> => {
  const res = await instance.post(BASE, body, AUTH_HEADER);
  return res.data;
};

export const deleteFavoriteRegion = async (legalDistrictCode: string): Promise<SuccessResponse<null>> => {
  const res = await instance.delete(`${BASE}/${legalDistrictCode}`, AUTH_HEADER);
  return res.data;
};
