import { UseFormSetError } from "react-hook-form";
import { SignupFormData } from "@/types/signup";
import useSignupStore from "@/store/signupStore";

export function validateSignupStateForForm(setError: UseFormSetError<SignupFormData>): boolean {
  const { isCodeSended, isEmailVerified, isNicknameValid } = useSignupStore.getState();

  let isValid = true;

  if (!isCodeSended) {
    setError("email", { message: "이메일 인증 코드를 먼저 전송해주세요." });
    isValid = false;
  }

  if (!isEmailVerified) {
    setError("email", { message: "이메일 인증을 완료해주세요." });
    isValid = false;
  }

  if (!isNicknameValid) {
    setError("nickname", { message: "닉네임 중복 확인을 완료해주세요." });
    isValid = false;
  }

  return isValid;
}
