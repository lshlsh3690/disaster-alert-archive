export interface SignupFormData {
  email: string;
  password: string;
  confirmPassword: string;
  nickname: string;
}

// 폼 전용 값: UI에서만 쓰는 verificationCode 포함 (DTO 아님)
export type SignupFormValues = SignupFormData & {
  verificationCode?: string;
};
