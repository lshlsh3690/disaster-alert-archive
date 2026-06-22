/**
 * Kakao 지도 GeoJSON 공용 헬퍼.
 * (KakaoPolygonMap.tsx 의 동일 로직을 재사용 가능하도록 분리)
 */

/** GeoJSON geometry → kakao LatLng 경로 배열. */
export function toLatLngPaths(kakao: any, geometry: any): any[][] {
  const rings: number[][][] =
    geometry.type === "MultiPolygon" ? geometry.coordinates.flat(1) : geometry.coordinates;
  return rings.map((ring) => ring.map((c) => new kakao.maps.LatLng(c[1], c[0])));
}

/** geometry 전체를 감싸는 LatLngBounds. */
export function calcBounds(kakao: any, geometry: any) {
  const b = new kakao.maps.LatLngBounds();
  const coords: number[][] =
    geometry.type === "MultiPolygon" ? geometry.coordinates.flat(2) : geometry.coordinates.flat(1);
  coords.forEach((c) => b.extend(new kakao.maps.LatLng(c[1], c[0])));
  return b;
}

/** 가장 큰 링(본토)의 중심 좌표 — 먼 섬 때문에 중심이 벗어나는 것 방지. */
export function largestRingCenter(geometry: any): [number, number] {
  const rings: number[][][] =
    geometry.type === "MultiPolygon" ? geometry.coordinates.flat(1) : geometry.coordinates;
  let best = rings[0], bestA = -Infinity;
  rings.forEach((ring) => {
    let n = Infinity, s = -Infinity, w = Infinity, e = -Infinity;
    ring.forEach((c) => { if (c[1]<n) n=c[1]; if (c[1]>s) s=c[1]; if (c[0]<w) w=c[0]; if (c[0]>e) e=c[0]; });
    const a = (s-n)*(e-w); if (a > bestA) { bestA=a; best=ring; }
  });
  let n=Infinity, s=-Infinity, w=Infinity, e=-Infinity;
  best.forEach((c) => { if (c[1]<n) n=c[1]; if (c[1]>s) s=c[1]; if (c[0]<w) w=c[0]; if (c[0]>e) e=c[0]; });
  return [(n+s)/2, (w+e)/2];
}
