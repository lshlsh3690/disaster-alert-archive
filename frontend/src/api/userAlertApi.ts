import { parseSuccessResponse } from "@/types/SuccessResponse";
import instance from "./axios";
import { z } from "zod";
import { ZPageMeta, ZAlert } from "@/types/alerts";


export type UserAlertCreateRequest = {
  title: string;
  message: string;
  disasterType?: string | null;
  disasterLevel?: string | null; // e.g. LEVEL_1 | LEVEL_2 | LEVEL_3
  occurredAt: string; // LocalDateTime string (e.g. 2025-10-01T10:00)
  regionCodes: string[];
  regionNames: string[];
};

export type UserAlertUpdateRequest = Partial<{
  title: string;
  message: string;
  disasterType: string | null;
  disasterLevel: string | null; // enum name
  occurredAt: string; // LocalDateTime
  regionCodes: string[];
  regionNames: string[];
}>;

export const ZUserAlertResponse = z.object({
  id: z.number(),
  createdById: z.number(),
  title: z.string(),
  message: z.string(),
  disasterType: z.string().nullable().optional(),
  emergencyLevelText: z.string().nullable().optional(),
  emergencyLevel: z.string().nullable().optional(),
  occurredAt: z.string(),
  createdAt: z.string(),
  modifiedAt: z.string().nullable().optional(),
  regionCodes: z.array(z.string()).default([]),
  regionNames: z.array(z.string()).default([]),
});

export type UserAlertResponse = z.infer<typeof ZUserAlertResponse>;

export async function createUserAlert(req: UserAlertCreateRequest): Promise<UserAlertResponse> {
  const res = await instance.post("/api/v1/user-alerts", req);
  const parsed = parseSuccessResponse(res.data, ZUserAlertResponse);
  if (!parsed) throw new Error("Unexpected response format");
  return parsed.data;
}

export async function fetchUserAlerts(params: { page?: number; size?: number; mine?: boolean }) {
  const res = await instance.get("/api/v1/user-alerts", { params, headers: { "X-Auth-Required": params.mine ? "true" : "false" } });
  // 서버는 ApiResponse 래핑 없이 Page<UserAlertDtos.Response> 바로 반환
  const page = ZPageMeta.parse(res.data);
  // content는 UserAlertDtos.Response 구조지만, ZAlert와 공통 필드를 사용하므로 재검증
  const content = z.array(ZAlert).parse(page.content);
  return { ...page, content };
}

export async function fetchUserAlert(id: number) {
  const res = await instance.get(`/api/v1/user-alerts/${id}`, { headers: { "X-Auth-Required": "false" } });
  // ApiResponse 래핑: { success, data }
  const body = res.data?.data ?? res.data;
  // UserAlertDtos.Response -> 공통 Alert로 매핑 검증 없이 사용 가능한 필드만 사용
  const mapped = {
    id: body.id,
    createdById: body.createdById,
    title: body.title,
    message: body.message,
    createdAt: body.createdAt,
    disasterType: body.disasterType ?? null,
    emergencyLevel: body.emergencyLevel ?? null,
    emergencyLevelText: body.emergencyLevelText ?? null,
    regionNames: body.regionNames ?? [],
    occurredAt: body.occurredAt ?? null,
  } as const;
  return mapped;
}

export async function updateUserAlert(id: number, req: UserAlertUpdateRequest): Promise<UserAlertResponse> {
  const res = await instance.patch(`/api/v1/user-alerts/${id}`, req, { headers: { "X-Auth-Required": "true" } });
  const parsed = parseSuccessResponse(res.data, ZUserAlertResponse);
  if (!parsed) throw new Error("Unexpected response format");
  return parsed.data;
}

export async function deleteUserAlert(id: number): Promise<void> {
  await instance.delete(`/api/v1/user-alerts/${id}`, { headers: { "X-Auth-Required": "true" } });
}


