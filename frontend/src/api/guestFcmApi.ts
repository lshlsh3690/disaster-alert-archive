const getDeviceType = () =>
  document.referrer.includes("android-app://") ? "ANDROID" : "WEB";

export async function registerGuestFcmToken(token: string, legalDistrictCodes: string[]) {
  if (legalDistrictCodes.length === 0) return;
  try {
    await fetch("/api/v1/fcm-token/guest", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ token, deviceType: getDeviceType(), legalDistrictCodes }),
    });
  } catch (error) {
    console.error("게스트 FCM 토큰 등록 실패:", error);
  }
}

export async function linkGuestFcmToken(token: string) {
  try {
    await fetch("/api/v1/fcm-token/guest/link", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify({ token }),
    });
  } catch (error) {
    console.error("게스트 FCM 토큰 연결 실패:", error);
  }
}

export async function deleteGuestFcmToken(token: string) {
  try {
    await fetch(`/api/v1/fcm-token/guest?token=${encodeURIComponent(token)}`, {
      method: "DELETE",
    });
  } catch (error) {
    console.error("게스트 FCM 토큰 삭제 실패:", error);
  }
}
