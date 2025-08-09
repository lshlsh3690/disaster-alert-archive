import instance from "@/api/axios";

const MEMBER_API_BASE = "/api/v1/members";

export const sendNicknameDuplicationCheck = async (nickname: string) => {
  const res = await instance.get(`${MEMBER_API_BASE}/check-nickname`, { params: { nickname } });
  return res.data;
};
