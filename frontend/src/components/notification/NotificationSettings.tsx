// frontend/src/components/notification/NotificationSettings.tsx
"use client";

import { useState, useEffect } from "react";
import { useNotificationPermission } from "@/hooks/useNotificationPermission";

type NotificationType = "NONE" | "PUSH" | "ALARM";

const NOTIFICATION_OPTIONS: {
  type: NotificationType;
  label: string;
  description: string;
}[] = [
  {
    type: "NONE",
    label: "알림 없음",
    description: "재난문자를 받아도 알림이 표시되지 않습니다.",
  },
  {
    type: "PUSH",
    label: "푸시 알림",
    description: "상단 배너로 재난문자 알림이 표시됩니다.",
  },
  {
    type: "ALARM",
    label: "시스템 알람",
    description: "소리와 진동으로 즉시 알림이 표시됩니다.",
  },
];

function NotificationTypeIcon({ type }: { type: NotificationType }) {
  if (type === "NONE") {
    return (
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <path d="M8.7 3A6 6 0 0 1 18 8c0 3 .5 4.8 1.1 6" />
        <path d="M17 17H4s3-2 3-9a4.7 4.7 0 0 1 .3-1.7" />
        <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
        <line x1="3" y1="3" x2="21" y2="21" />
      </svg>
    );
  }
  if (type === "ALARM") {
    return (
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
        <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
        <path d="M3.5 3.5C2.6 4.9 2 6.4 2 8" />
        <path d="M22 8c0-1.6-.6-3.1-1.5-4.5" />
      </svg>
    );
  }
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
      <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
    </svg>
  );
}

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
    <div className="space-y-4">
      <h2 className="text-lg font-semibold text-[var(--ink)]">알림 설정</h2>

      {/* 권한 상태 배너 */}
      {permission === "denied" && (
        <p className="rounded-[var(--radius-compact)] border border-[#f3c7c1] bg-[var(--coral-soft)] px-3 py-2.5 text-[13px] text-[#b4453a]">
          알림 권한이 차단되어 있습니다. 브라우저 설정에서 알림을 허용해주세요.
        </p>
      )}

      {permission === "default" && (
        <p className="rounded-[var(--radius-compact)] border border-[#cfe0fb] bg-[var(--blue-soft)] px-3 py-2.5 text-[13px] text-[var(--blue)]">
          푸시 알림을 받으려면 알림 권한을 허용해주세요.
        </p>
      )}

      {/* 알림 타입 선택 */}
      <div className="space-y-2.5">
        {NOTIFICATION_OPTIONS.map((option) => {
          const isSelected = selectedType === option.type;
          return (
            <button
              key={option.type}
              type="button"
              onClick={() => handleSave(option.type)}
              disabled={isSaving || isLoading}
              aria-pressed={isSelected}
              className={`flex w-full items-start gap-3 rounded-[var(--radius-card)] border px-4 py-3.5 text-left transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--blue-soft)] ${
                isSelected
                  ? "border-[var(--blue)] bg-[var(--blue-soft)]"
                  : "border-[var(--line)] bg-[var(--surface)] hover:border-[#c8d0dc]"
              } ${isSaving || isLoading ? "opacity-60 cursor-not-allowed" : "cursor-pointer"}`}
            >
              <span
                className={`mt-0.5 shrink-0 ${isSelected ? "text-[var(--blue)]" : "text-[var(--text-subtle)]"}`}
                aria-hidden="true"
              >
                <NotificationTypeIcon type={option.type} />
              </span>
              <span className="flex-1 min-w-0">
                <span className="flex items-center gap-2">
                  <span className="font-medium text-[var(--ink)]">{option.label}</span>
                  {isSelected && (
                    <span className="rounded-[var(--radius-pill)] bg-[var(--blue)] px-2 py-0.5 text-[11px] font-semibold text-white">
                      현재 설정
                    </span>
                  )}
                </span>
                <span className="mt-0.5 block text-[13px] text-[var(--text-muted)]">{option.description}</span>
              </span>
            </button>
          );
        })}
      </div>

      {/* 저장 메시지 */}
      {saveMessage && (
        <p
          className={`text-center text-[13px] ${
            saveMessage.includes("실패") || saveMessage.includes("거부") ? "text-[var(--coral)]" : "text-[var(--success)]"
          }`}
        >
          {saveMessage}
        </p>
      )}
    </div>
  );
}
