import instance from "@/api/axios";
import { SuccessResponse } from "@/types/SuccessResponse";

const MEMBER_API_BASE = "/api/v1/members";

export type MemberInfo = {
  memberId: number;
  id?: number; // 백엔드 응답 필드명이 다를 수 있는 경우 대비
  nickname: string;
  email: string;
  role: "USER" | "ADMIN";
};

export const sendNicknameDuplicationCheck = async (nickname: string) => {
  const res = await instance.get(`${MEMBER_API_BASE}/check-nickname`, { params: { nickname } });
  return res.data;
};

/**
 * 현재 로그인된 사용자 정보를 가져온다.
 * 반환값: SuccessResponse<MemberInfo> 형태
 * { success, code, message, data: { memberId, nickname, email, role } }
 *
 * 호출부에서 .data 로 실제 멤버 정보를 꺼내야 한다.
 */
export const getMyInfo = async (): Promise<SuccessResponse<MemberInfo>> => {
  const res = await instance.get<SuccessResponse<MemberInfo>>(`${MEMBER_API_BASE}/me`, {
    headers: { "X-Auth-Required": "true" },
  });
  return res.data;
};
