// src/ui/metros.ts
export const METROS = [
  "서울특별시",
  "부산광역시",
  "대구광역시",
  "인천광역시",
  "광주광역시",
  "대전광역시",
  "울산광역시",
  "세종특별자치시",

  // 도/특별자치도
  "경기도",
  "강원특별자치도",
  "충청북도",
  "충청남도",
  "전북특별자치도", // (구) 전라북도
  "전라남도",
  "경상북도",
  "경상남도",
  "제주특별자치도",
] as const;
export type Metro = (typeof METROS)[number];

export const METRO_COORDS: Record<Metro, { lat: number; lng: number }> = {
  서울특별시: { lat: 37.5665, lng: 126.978 },
  부산광역시: { lat: 35.1796, lng: 129.0756 },
  대구광역시: { lat: 35.8714, lng: 128.6014 },
  인천광역시: { lat: 37.4563, lng: 126.7052 },
  광주광역시: { lat: 35.1595, lng: 126.8526 },
  대전광역시: { lat: 36.0504, lng: 127.845 },
  울산광역시: { lat: 35.5384, lng: 129.3114 },
  세종특별자치시: { lat: 36.48, lng: 127.289 },

  경기도: { lat: 37.4138, lng: 127.5183 },
  강원특별자치도: { lat: 37.8228, lng: 128.55 },
  충청북도: { lat: 36.6356, lng: 127.9914 },
  충청남도: { lat: 36.3184, lng: 127 },
  전북특별자치도: { lat: 35.4575, lng: 127.353 },
  전라남도: { lat: 34.8194, lng: 126.893 },
  경상북도: { lat: 36.1919, lng: 128.8889 },
  경상남도: { lat: 35.2283, lng: 128.281 },
  제주특별자치도: { lat: 33.489, lng: 126.4983 },
};

// 구 표기 → 최신 표기 별칭(앞부분 startsWith 매칭에 사용)
const REGION_ALIASES: Record<string, Metro> = {
  강원도: "강원특별자치도",
  전라북도: "전북특별자치도",
  // 필요 시 추가: "전북특별자치도" 축약표기 등
};

export function toMetroOrNull(name?: string | null): Metro | null {
  if (!name) return null;
  const label = name.replace(/\s+/g, " ").trim();

  // 1) 공식 명칭으로 매칭
  const hit = METROS.find((m) => label.startsWith(m));
  if (hit) return hit;

  // 2) 별칭(구 표기)으로 매칭
  for (const [alias, canonical] of Object.entries(REGION_ALIASES)) {
    if (label.startsWith(alias)) return canonical;
  }
  return null;
}

export function groupToMetros(
  regionStats: Array<{ region?: string; name?: string; count: number }>
): Record<Metro, number> {
    console.log("Grouping region stats to metros", regionStats);
  const init = Object.fromEntries(METROS.map((m) => [m, 0])) as Record<Metro, number>;
  for (const row of regionStats ?? []) {
    const label = row.region ?? row.name ?? "";
    const metro = toMetroOrNull(label);
    if (metro) init[metro] += row.count ?? 0;
  }
  return init;
}
