"use client";

import dynamic from "next/dynamic";

// layout.tsx는 Server Component라 그 안에서 직접 dynamic(ssr:false)를 쓸 수 없다.
// 정적 import로 SSR되면 서버는 실제 권한을 알 수 없어 항상 "default"로 배너 HTML을
// 내려보내고, granted/denied 사용자는 하이드레이션 전까지 그 배너를 보게 된다
// (원래 고치려던 깜빡임이 하이드레이션 시점으로 옮겨 재현됨). 클라이언트 전용 로더로
// 분리해 서버 렌더 자체를 건너뛴다.
const NotificationPermissionBanner = dynamic(
  () => import("./NotificationPermissionBanner"),
  { ssr: false }
);

export default NotificationPermissionBanner;
