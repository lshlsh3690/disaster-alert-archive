declare global {
  interface Window {
    kakao: any;
  }
}

// 모듈 레벨로 Promise를 캐시해 SDK 로드를 정확히 한 번만 시작한다. 이전엔 매 호출마다
// "이미 script 태그가 있으면 window.kakao.maps.load()를 바로 호출" 하는 식으로
// 짜여 있었는데, 그 태그가 아직 로드 중이면(window.kakao가 비어있는 채로 접근해)
// 동기 TypeError로 즉시 reject되거나(React StrictMode의 effect 이중 실행 등에서 재현),
// 반대로 그 태그가 이미 완전히 끝난(load/error 이벤트가 지나간) 상태라면 뒤늦게 붙인
// 리스너가 다시는 안 불려서 Promise가 영원히 pending에 머무는 문제가 있었다.
// 캐싱하면 실제로 script를 만들고 리스너를 붙이는 코드는 최초 호출 때 딱 한 번만
// 동기적으로(그래서 script가 로드를 끝내기 전에 반드시) 실행되므로 두 문제 모두 사라진다.
let sdkPromise: Promise<typeof window.kakao> | null = null;

export function loadKakaoMapSdk(appKey?: string): Promise<typeof window.kakao> {
  if (typeof window === "undefined") return Promise.reject(new Error("window unavailable"));
  if (sdkPromise) return sdkPromise;

  sdkPromise = new Promise((resolve, reject) => {
    if (window.kakao && window.kakao.maps) {
      window.kakao.maps.load(() => resolve(window.kakao));
      return;
    }
    const key = appKey ?? process.env.NEXT_PUBLIC_KAKAO_MAP_APP_KEY;
    if (!key) return reject(new Error("Kakao app key missing"));

    const scriptId = "kakao-maps-sdk";
    // 우리 로더를 거치지 않고 다른 경로로 같은 id의 태그가 이미 추가돼 있는 경우를 위한
    // 방어적 처리 — 정상적인 경우라면 캐시된 sdkPromise가 항상 앞서 처리하므로 이 분기는
    // 거의 타지 않는다.
    const existing = document.getElementById(scriptId) as HTMLScriptElement | null;
    if (existing) {
      existing.addEventListener("load", () => {
        if (!window.kakao || !window.kakao.maps) return reject(new Error("kakao not found"));
        window.kakao.maps.load(() => resolve(window.kakao));
      });
      existing.addEventListener("error", () => reject(new Error("failed to load kakao sdk")));
      return;
    }
    const s = document.createElement("script");
    s.id = scriptId;
    s.async = true;
    s.defer = true;
    s.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${key}&autoload=false&libraries=services`;
    s.onload = () => {
      if (!window.kakao || !window.kakao.maps) return reject(new Error("kakao not found"));
      window.kakao.maps.load(() => resolve(window.kakao));
    };
    s.onerror = () => reject(new Error("failed to load kakao sdk"));
    document.head.appendChild(s);
  });

  // 실패하면 다음 호출에서 재시도할 수 있도록 캐시를 비운다(성공한 결과만 영구 캐시).
  sdkPromise.catch(() => {
    sdkPromise = null;
  });

  return sdkPromise;
}
