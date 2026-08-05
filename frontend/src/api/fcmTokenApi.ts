export async function deleteFcmToken(token: string) {
  try {
    await fetch(`/api/v1/fcm-token?token=${encodeURIComponent(token)}`, {
      method: "DELETE",
      credentials: "include",
    });
  } catch (error) {
    console.error("FCM 토큰 삭제 실패:", error);
  }
}
