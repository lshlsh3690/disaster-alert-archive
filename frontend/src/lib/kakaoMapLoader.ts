declare global {
  interface Window {
    kakao: any;
  }
}

export function loadKakaoMapSdk(appKey?: string): Promise<typeof window.kakao> {
  return new Promise((resolve, reject) => {
    if (typeof window === "undefined") return reject(new Error("window unavailable"));
    if (window.kakao && window.kakao.maps) {
      window.kakao.maps.load(() => resolve(window.kakao));
      return;
    }
    const key = appKey ?? process.env.NEXT_PUBLIC_KAKAO_MAP_APP_KEY;
    if (!key) return reject(new Error("Kakao app key missing"));

    const scriptId = "kakao-maps-sdk";
    const existing = document.getElementById(scriptId);
    if (existing) {
      // 이미 추가됨. 단, script 태그가 DOM에 있다고 해서 로드가 끝났다는 보장은 없다
      // (async/defer라 아직 실행 전일 수 있음) — window.kakao가 비어있는 상태에서
      // .maps.load()를 바로 호출하면 동기 TypeError로 Promise가 즉시 reject된다.
      // (React StrictMode의 effect 이중 실행이나, 같은 페이지의 다른 컴포넌트가
      //  먼저 태그를 추가한 직후 등에서 실제로 재현됨.) 아직 준비 안 됐으면 기존
      // 태그의 load 이벤트를 한 번 더 기다린다.
      if (window.kakao && window.kakao.maps) {
        window.kakao.maps.load(() => resolve(window.kakao));
      } else {
        existing.addEventListener("load", () => {
          if (!window.kakao) return reject(new Error("kakao not found"));
          window.kakao.maps.load(() => resolve(window.kakao));
        });
        existing.addEventListener("error", () => reject(new Error("failed to load kakao sdk")));
      }
      return;
    }
    const s = document.createElement("script");
    s.id = scriptId;
    s.async = true;
    s.defer = true;
    s.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${key}&autoload=false&libraries=services`;
    s.onload = () => {
      if (!window.kakao) return reject(new Error("kakao not found"));
      window.kakao.maps.load(() => resolve(window.kakao));
    };
    s.onerror = () => reject(new Error("failed to load kakao sdk"));
    document.head.appendChild(s);
  });
}