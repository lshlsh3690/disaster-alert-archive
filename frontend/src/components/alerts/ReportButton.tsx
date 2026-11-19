"use client";

import { useRouter } from "next/navigation";
import { useAuthStore } from "@/store/authStore";
import { useTranslation } from "react-i18next";

// className 을 주면 버튼 모양만 바꾼다(재난 문자 목록의 KRDS 주요 버튼). 로그인 여부에 따른 이동은 그대로.
export default function ReportButton({ className }: { className?: string } = {}) {
  const router = useRouter();
  const user = useAuthStore((state) => state.user);
  const { t } = useTranslation();


  const handleClick = () => {
    if (!user) {
      router.push("/login");
    } else {
      router.push("/alerts/new");
    }
  };

  return (
    <button type="button" onClick={handleClick} className={className ?? "px-4 py-2 rounded bg-green-600 text-white hover:bg-green-700 transition"}>
      {t("alertList.report")}
    </button>
  );
}
