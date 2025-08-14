import { SignupFormData } from "@/schemas/signupSchema";
import { UseFormSetError } from "react-hook-form";
import { create } from "zustand";
import { devtools } from "zustand/middleware";

interface SignupState {
  isEmailVerified: boolean;
  isCodeSended: boolean;
  isNicknameValid: boolean;
  isPasswordMatched: boolean;
  isEmailCodeTimeout: boolean;
  setIsEmailVerified: (value: boolean) => void;
  setIsCodeSended: (value: boolean) => void;
  setIsNicknameValid: (value: boolean) => void;
  setIsPasswordMatched: (value: boolean) => void;
  setIsEmailCodeTimeout: (value: boolean) => void;
  validateSignupStateForForm: (setError: UseFormSetError<SignupFormData>) => boolean;
  resetSignupState: () => void;
}

const useSignupStore = create<SignupState>()(
  devtools((set) => ({
    isEmailVerified: false,
    isCodeSended: false,
    isNicknameValid: false,
    isPasswordMatched: false,
    isEmailCodeTimeout: false,
    setIsEmailVerified: (value: boolean) => set({ isEmailVerified: value }),
    setIsCodeSended: (value: boolean) => set({ isCodeSended: value }),
    setIsNicknameValid: (value: boolean) => set({ isNicknameValid: value }),
    setIsPasswordMatched: (value: boolean) => set({ isPasswordMatched: value }),
    setIsEmailCodeTimeout: (value: boolean) => set({ isEmailCodeTimeout: value }),
    resetSignupState: () =>
      set({
        isEmailVerified: false,
        isCodeSended: false,
        isNicknameValid: false,
        isPasswordMatched: false,
        isEmailCodeTimeout: false,
      }),
    validateSignupStateForForm: (setError) => {
      const { isCodeSended, isEmailVerified, isNicknameValid, isPasswordMatched } = useSignupStore.getState();

      let isValid = true;

      if (!isCodeSended) {
        console.error("이메일 인증 코드가 전송되지 않았습니다.");
        setError("email", { message: "이메일 인증 코드를 먼저 전송해주세요." });
        isValid = false;
      }

      if (!isEmailVerified) {
        console.error("이메일 인증이 완료되지 않았습니다.");
        setError("email", { message: "이메일 인증을 완료해주세요." });
        isValid = false;
      }

      if (!isNicknameValid) {
        console.error("닉네임 중복 확인이 완료되지 않았습니다.");
        setError("nickname", { message: "닉네임 중복 확인을 완료해주세요." });
        isValid = false;
      }
      if (!isPasswordMatched) {
        console.error("비밀번호가 일치하지 않습니다.");
        setError("password", { message: "비밀번호가 일치하지 않습니다." });
        setError("confirmPassword", { message: "비밀번호가 일치하지 않습니다." });
        isValid = false;
      }

      return isValid;
    },
  }))
);

export default useSignupStore;
