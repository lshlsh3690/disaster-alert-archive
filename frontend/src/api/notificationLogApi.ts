import instance from "@/api/axios";
import { SuccessResponse } from "@/types/SuccessResponse";

const BASE = "/api/v1/notification-logs";

export type NotificationLogItem = {
  id: number;
  disasterAlertId: number;
  message: string | null;
  disasterType: string | null;
  originalRegion: string | null;
  emergencyLevel: string | null;
  isRead: boolean;
  sentAt: string;
};

export type NotificationLogPage = {
  content: NotificationLogItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  unreadCount: number;
};

export const getMyNotificationLogs = async (
  page = 0,
  size = 20
): Promise<SuccessResponse<NotificationLogPage>> => {
  const res = await instance.get<SuccessResponse<NotificationLogPage>>(BASE, {
    params: { page, size },
  });
  return res.data;
};

export const markNotificationAsRead = async (id: number): Promise<void> => {
  await instance.patch(`${BASE}/${id}/read`);
};
