/**
 * GeoJSON fetch 결과 메모이즈.
 *
 * sido/sigungu.geojson 은 합쳐서 6MB 수준이라, SPA 내 페이지 이동마다
 * 다시 fetch + JSON 파싱하면 메인 스레드 비용이 크다.
 * 모듈 레벨에서 파싱된 객체의 Promise 를 캐싱해 세션 내 1회만 처리한다.
 */
const cache = new Map<string, Promise<any>>();

export function fetchGeoJsonCached(url: string): Promise<any> {
  let p = cache.get(url);
  if (!p) {
    p = fetch(url, { cache: "force-cache" })
      .then((r) => {
        if (!r.ok) throw new Error(`geojson fetch failed: ${url} (${r.status})`);
        return r.json();
      })
      .catch((e) => {
        cache.delete(url); // 실패한 Promise 가 캐시에 남지 않도록
        throw e;
      });
    cache.set(url, p);
  }
  return p;
}
