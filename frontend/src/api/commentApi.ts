import instance from "./axios";
import { z } from "zod";
import { parseSuccessResponse } from "@/types/SuccessResponse";

export const ZComment = z.object({
  id: z.number(),
  content: z.string(),
  authorId: z.number().nullable().optional(),
  authorNickname: z.string().nullable().optional(),
  createdAt: z.string(),
  updatedAt: z.string().nullable().optional(),
  edited: z.boolean().optional(),
});
export type Comment = z.infer<typeof ZComment>;

export const ZCommentPage = z.object({
  content: z.array(ZComment),
  totalElements: z.number(),
  totalPages: z.number(),
  number: z.number(),
  size: z.number(),
});

export type CommentCreateRequest = {
  source: "OFFICIAL" | "USER";
  targetId: number;
  content: string;
};

export async function fetchComments(params: { source: "OFFICIAL" | "USER"; targetId: number; page?: number; size?: number; }) {
  const res = await instance.get("/api/v1/comments", { params, headers: { "X-Auth-Required": "false" } });
  return ZCommentPage.parse(res.data);
}

export async function createComment(req: CommentCreateRequest) {
  const res = await instance.post("/api/v1/comments", req, { headers: { "X-Auth-Required": "true" } });
  const parsed = parseSuccessResponse(res.data, ZComment);
  if (!parsed) throw new Error("Unexpected response");
  return parsed.data;
}

export async function deleteComment(id: number) {
  await instance.delete(`/api/v1/comments/${id}`, { headers: { "X-Auth-Required": "true" } });
}

export async function updateComment(id: number, content: string) {
  const res = await instance.patch(`/api/v1/comments/${id}`, { content }, { headers: { "X-Auth-Required": "true" } });
  const parsed = parseSuccessResponse(res.data, ZComment);
  if (!parsed) throw new Error("Unexpected response");
  return parsed.data;
}

export async function fetchLatestComments(size = 5) {
  const res = await instance.get(`/api/v1/comments/latest`, { params: { size }, headers: { "X-Auth-Required": "false" } });
  // 서버는 Page<CommentDtos.Response> 반환
  return z.object({
    content: z.array(ZComment),
    totalElements: z.number(),
    totalPages: z.number(),
    number: z.number(),
    size: z.number(),
  }).parse(res.data).content;
}


