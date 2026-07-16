"use client";

import { useRouter } from "next/navigation";
import { useAuthStore } from "@/store/authStore";
import { useTranslation } from "react-i18next";

export default function ReportButton() {
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
    <button onClick={handleClick} className="px-4 py-2 rounded bg-green-600 text-white hover:bg-green-700 transition">
      {t("alertList.report")}
    </button>
  );
}
