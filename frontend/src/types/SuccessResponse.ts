export type SuccessResponse<T> = {
  success: true;
  code: number;
  message: string;
  data: T;
};