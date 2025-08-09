export interface ApiResponse<T = unknown> {
  success: boolean;
  message: string;
  code: number;
  data: T;
}
