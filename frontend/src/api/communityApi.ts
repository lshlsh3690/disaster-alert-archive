import instance from "./axios";
import { z } from "zod";
import { parseSuccessResponse } from "@/types/SuccessResponse";

export const ZCommunityPost = z.object({
  id: z.number(),
  category: z.enum(["NOTICE", "FREE"]),
  authorId: z.number().nullable().optional(),
  authorNickname: z.string().nullable().optional(),
  title: z.string(),
  content: z.string(),
  createdAt: z.string(),
  updatedAt: z.string().nullable().optional(),
});
export type CommunityPost = z.infer<typeof ZCommunityPost>;

export async function fetchCommunityPosts(category: "NOTICE" | "FREE", params?: { page?: number; size?: number; }) {
  const res = await instance.get(`/api/v1/community`, { params: { category, ...params }, headers: { "X-Auth-Required": "false" } });
  const PageSchema = z.object({ content: z.array(ZCommunityPost), totalElements: z.number(), totalPages: z.number(), number: z.number(), size: z.number() });
  return PageSchema.parse(res.data);
}

export async function createCommunityPost(body: { category: "NOTICE" | "FREE"; title: string; content: string; }) {
  const res = await instance.post(`/api/v1/community`, body, { headers: { "X-Auth-Required": "true" } });
  const parsed = parseSuccessResponse(res.data, ZCommunityPost);
  if (!parsed) throw new Error("Unexpected response");
  return parsed.data;
}


