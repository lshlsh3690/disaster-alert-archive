// 대시보드 지도의 시군구/읍면동 경계(public/map/**.json). 시도 경계는 koreaSido.data.ts 에 번들돼 있어 여기서 받지 않는다.
// 경계 데이터는 frontend/scripts/map-regions 의 SQL 로 만든다(README 참고).
import { useQuery } from "@tanstack/react-query";
import type { BBox } from "@/lib/mapViewBox";

export interface MapRegion {
  /** 법정동 코드 — 시군구 5자리 / 읍면동 8자리 (백엔드 /stats/region-code 의 code 와 같다) */
  c: string;
  /** 공식 법정동 명칭 (예: "경기도 수원시 장안구 파장동") */
  n: string;
  /** SVG path (koreaSido.data.ts 와 같은 좌표계) */
  d: string;
  /** 말풍선 기준점 [x, y] */
  p: [number, number];
  /** 경계 상자 [minX, minY, maxX, maxY] — 시군구 파일에만 있다(눌렀을 때 확대할 영역) */
  b?: BBox;
}

// 경계 데이터를 다시 만들 때(행정구역 개편) 올린다. 정적 파일이 브라우저/CDN 에 최대 8일 캐시되므로(next.config.ts) URL 을 바꿔 옛 캐시를 건너뛴다.
const MAP_DATA_VERSION = "2026-09";

async function fetchRegions(url: string): Promise<MapRegion[]> {
  const res = await fetch(`${url}?v=${MAP_DATA_VERSION}`);
  if (!res.ok) throw new Error(`지도 경계 데이터를 불러오지 못했습니다 (${url}: ${res.status})`);
  const json = (await res.json()) as { regions: MapRegion[] };
  return json.regions;
}

/**
 * 시군구 경계(전국 256개). enabled=false 면 요청하지 않는다(시도 보기).
 * 경계는 바뀌지 않는 정적 파일이라 한 번 받으면 다시 받지 않는다.
 */
export function useSigunguRegions(enabled: boolean) {
  return useQuery({
    queryKey: ["map-regions", "sigungu"],
    queryFn: () => fetchRegions("/map/sigungu.json"),
    enabled,
    staleTime: Infinity,
    gcTime: Infinity,
  });
}

/**
 * 한 시도의 읍면동 경계. 시도 코드 앞 2자리(예: "41")를 주면 /map/emd/41.json 만 받는다 —
 * 읍면동 전체(약 1.1MB)가 아니라 필요한 시도 파일만 받으려고 시도별로 나눠 두었다.
 * sido 가 null 이면 요청하지 않는다.
 */
export function useEmdRegions(sido: string | null) {
  return useQuery({
    queryKey: ["map-regions", "emd", sido],
    queryFn: () => fetchRegions(`/map/emd/${sido}.json`),
    enabled: sido !== null,
    staleTime: Infinity,
    gcTime: Infinity,
  });
}
