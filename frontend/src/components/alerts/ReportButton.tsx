"use client";

import { useRouter } from "next/navigation";
import { useAuthStore } from "@/store/authStore";

export default function ReportButton() {
  const router = useRouter();
  const user = useAuthStore((state) => state.user);

  const handleClick = () => {
    if (!user) {
      router.push("/login");
    } else {
      router.push("/alerts/new");
    }
  };

  return (
    <button onClick={handleClick} className="px-4 py-2 rounded bg-green-600 text-white hover:bg-green-700 transition">
      제보 등록하기
    </button>
  );
}
