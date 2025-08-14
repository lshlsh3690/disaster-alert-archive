import { SuccessResponse } from "@/types/SuccessResponse";
import { z } from "zod";

export const SuccessResponseSchema = <T extends z.ZodTypeAny>(dataSchema: T) =>
  z.object({
    success: z.literal(true), // true로 고정
    code: z.number(),
    message: z.string(),
    data: dataSchema, // 제네릭 타입으로 데이터 스키마
  });

export function parseSuccessResponse<T>(raw: unknown, dataSchema: z.ZodType<T>): SuccessResponse<T> | null {
  const parsed = SuccessResponseSchema(dataSchema).safeParse(raw);
  return parsed.success ? (parsed.data as SuccessResponse<T>) : null;
}
