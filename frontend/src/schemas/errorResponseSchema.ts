import { z } from "zod";

export const errorResponseSchema = z.object({
  code: z.string(), // "A102", "M409" ...
  key: z.string().optional(), // "EMAIL_NOT_VERIFIED", "DUPLICATE_NICKNAME" ...
  message: z.string(),
  status: z.number(), // 400, 401, 404 ...
  path: z.string(),
  timestamp: z.string(), // ISO-8601
  field: z.string().nullable().optional()
});

export function parseErrorResponse(raw: unknown) {
  const parsed = errorResponseSchema.safeParse(raw);
  return parsed.success ? parsed.data : null;
}