import { parseSuccessResponse, SuccessResponse } from "@/types/successResponse";
import { z } from "zod";

export function makeMutationFn<Vars, T>(
  apiCall: (vars: Vars) => Promise<unknown>,// API 호출 함수, Vars는 요청 파라미터 타입
  dataSchema: z.ZodType<T>//reponse Body
): (vars: Vars) => Promise<SuccessResponse<T>> {
  return async (vars: Vars) => {
    const raw = await apiCall(vars); // axios라면 raw = (await axios(...)).data 형태여야 함
    const ok = parseSuccessResponse<T>(raw, dataSchema);
    if (!ok) {
      throw new Error("Invalid success response shape");
    }
    return ok;
  };
}