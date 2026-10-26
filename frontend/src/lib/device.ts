// frontend/src/lib/device.ts
// 푸시 알림은 모바일 사용자 전용이다(2026-10) — 데스크톱 브라우저에서는 권한 요청·토큰 등록을 하지 않는다.

/**
 * 휴대폰·태블릿이면 true. SSR 에서는 false.
 *
 * - Android 태블릿은 Chromium 의 userAgentData.mobile 이 false 라 UA 의 "Android" 로 잡는다.
 * - iPadOS 13+ 는 기본이 데스크톱 모드라 UA 가 Macintosh 로 오므로 터치 포인트로 구분한다
 *   (Mac 은 터치 포인트가 0 이다).
 */
export function isMobileDevice(): boolean {
  if (typeof navigator === "undefined") return false;
  const uaData = (navigator as Navigator & { userAgentData?: { mobile?: boolean } }).userAgentData;
  if (uaData?.mobile) return true;
  const ua = navigator.userAgent;
  if (/Android|iPhone|iPad|iPod|Mobile/i.test(ua)) return true;
  return /Macintosh/.test(ua) && navigator.maxTouchPoints > 1;
}

/**
 * 서버에 보내는 FCM 토큰의 기기 구분. TWA(안드로이드 앱 래퍼)는 ANDROID, 그 밖의 모바일 브라우저는 MOBILE_WEB.
 * 예전 값 WEB 은 데스크톱이 섞여 있어 V134 마이그레이션으로 일괄 삭제했다.
 */
export function pushDeviceType(): "ANDROID" | "MOBILE_WEB" {
  return document.referrer.includes("android-app://") ? "ANDROID" : "MOBILE_WEB";
}
