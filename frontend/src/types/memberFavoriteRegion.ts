export type MemberFavoriteRegion = {
  legalDistrictCode: string;
  regionName: string;
  createdAt?: string;
};

export type AddFavoriteRegionRequest = {
  legalDistrictCode: string;
};