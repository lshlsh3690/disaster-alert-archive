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
    if (document.getElementById(scriptId)) {
      // 이미 추가됨
      window.kakao.maps.load(() => resolve(window.kakao));
      return;
    }
    const s = document.createElement("script");
    s.id = scriptId;
    s.async = true;
    s.defer = true;
    s.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${key}&autoload=false`;
    s.onload = () => {
      if (!window.kakao) return reject(new Error("kakao not found"));
      window.kakao.maps.load(() => resolve(window.kakao));
    };
    s.onerror = () => reject(new Error("failed to load kakao sdk"));
    document.head.appendChild(s);
  });
}