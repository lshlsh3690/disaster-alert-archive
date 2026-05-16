// frontend/src/components/notification/NotificationSettings.tsx
"use client";

import { useState, useEffect } from "react";
import { useNotificationPermission } from "@/hooks/useNotificationPermission";

type NotificationType = "NONE" | "PUSH" | "ALARM";

const NOTIFICATION_OPTIONS: {
  type: NotificationType;
  label: string;
  description: string;
  icon: string;
}[] = [
  {
    type: "NONE",
    label: "알림 없음",
    description: "재난문자를 받아도 알림이 표시되지 않습니다.",
    icon: "🔕",
  },
  {
    type: "PUSH",
    label: "푸시 알림",
    description: "상단 배너로 재난문자 알림이 표시됩니다.",
    icon: "🔔",
  },
  {
    type: "ALARM",
    label: "시스템 알람",
    description: "소리와 진동으로 즉시 알림이 표시됩니다.",
    icon: "🚨",
  },
];

export default function NotificationSettings() {
  const { permission, isLoading, requestPermission } = useNotificationPermission();
  const [selectedType, setSelectedType] = useState<NotificationType>("PUSH");
  const [isSaving, setIsSaving] = useState(false);
  const [saveMessage, setSaveMessage] = useState("");

  // 현재 설정 불러오기
  useEffect(() => {
    const fetchPreference = async () => {
      try {
        const res = await fetch("/api/v1/notification-preference", {
          credentials: "include",
        });
        if (res.ok) {
          const data = await res.json();
          setSelectedType(data.data?.notificationType ?? "PUSH");
        }
      } catch (error) {
        console.error("알림 설정 불러오기 실패:", error);
      }
    };
    fetchPreference();
  }, []);

  // 알림 타입 변경 저장
  const handleSave = async (type: NotificationType) => {
    // PUSH나 ALARM 선택 시 권한 확인
    if (type !== "NONE" && permission !== "granted") {
      const granted = await requestPermission();
      if (!granted) {
        setSaveMessage("알림 권한이 거부되었습니다. 브라우저 설정에서 허용해주세요.");
        return;
      }
    }

    setIsSaving(true);
    try {
      const res = await fetch("/api/v1/notification-preference", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ notificationType: type }),
      });

      if (res.ok) {
        setSelectedType(type);
        setSaveMessage("알림 설정이 저장되었습니다.");
        setTimeout(() => setSaveMessage(""), 3000);
      }
    } catch (error) {
      console.error("알림 설정 저장 실패:", error);
      setSaveMessage("저장에 실패했습니다. 다시 시도해주세요.");
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div className="p-4 space-y-4">
      <h2 className="text-lg font-semibold">알림 설정</h2>

      {/* 권한 상태 배너 */}
      {permission === "denied" && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-3 text-sm text-red-700">
          ⚠️ 알림 권한이 차단되어 있습니다. 브라우저 설정에서 알림을 허용해주세요.
        </div>
      )}

      {permission === "default" && (
        <div className="bg-blue-50 border border-blue-200 rounded-lg p-3 text-sm text-blue-700">
          💡 푸시 알림을 받으려면 알림 권한을 허용해주세요.
        </div>
      )}

      {/* 알림 타입 선택 */}
      <div className="space-y-3">
        {NOTIFICATION_OPTIONS.map((option) => (
          <button
            key={option.type}
            onClick={() => handleSave(option.type)}
            disabled={isSaving || isLoading}
            className={`w-full flex items-start gap-3 p-4 rounded-xl border-2 text-left transition-all
              ${
                selectedType === option.type
                  ? "border-blue-500 bg-blue-50"
                  : "border-gray-200 bg-white hover:border-gray-300"
              }
              ${isSaving || isLoading ? "opacity-50 cursor-not-allowed" : "cursor-pointer"}
            `}
          >
            <span className="text-2xl">{option.icon}</span>
            <div className="flex-1">
              <div className="flex items-center gap-2">
                <span className="font-medium text-gray-900">{option.label}</span>
                {selectedType === option.type && (
                  <span className="text-xs bg-blue-500 text-white px-2 py-0.5 rounded-full">현재 설정</span>
                )}
              </div>
              <p className="text-sm text-gray-500 mt-0.5">{option.description}</p>
            </div>
          </button>
        ))}
      </div>

      {/* 저장 메시지 */}
      {saveMessage && (
        <p
          className={`text-sm text-center ${
            saveMessage.includes("실패") || saveMessage.includes("거부") ? "text-red-600" : "text-green-600"
          }`}
        >
          {saveMessage}
        </p>
      )}
    </div>
  );
}
