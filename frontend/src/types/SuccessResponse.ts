import { z } from "zod";


export type SuccessResponse<T> = {
  success: true;
  code: number;
  message: string;
  data: T;
};

export const SuccessResponse = <T extends z.ZodTypeAny>(dataSchema: T) =>
  z.object({
    success: z.literal(true), // true로 고정
    code: z.number(),
    message: z.string(),
    data: dataSchema, // 제네릭 타입으로 데이터 스키마
  });

export function parseSuccessResponse<T>(raw: unknown, dataSchema: z.ZodType<T>): SuccessResponse<T> | null {
  const parsed = SuccessResponse(dataSchema).safeParse(raw);
  return parsed.success ? (parsed.data as SuccessResponse<T>) : null;
}


