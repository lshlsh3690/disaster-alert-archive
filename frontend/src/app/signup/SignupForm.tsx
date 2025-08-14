"use client";

import { useForm } from "react-hook-form";
import { useRouter } from "next/navigation";
import EmailInput from "@components/form/EmailInput";
import useSignupStore from "@/store/signupStore";
import CodeInput from "@/components/form/CodeInput";
import { CountdownProvider } from "@/context/useCountdownContext";
import { PasswordInputGroup } from "@/components/form/PasswordInputGroup";
import { useEffect } from "react";
import useSignup from "@/lib/mutations/useSignup";
import Button from "@/components/Button";
import NicknameInput from "@/components/form/NicknameInput";
import { zodResolver } from "@hookform/resolvers/zod";
import { SignupFormData, SignupFormValues, signupSchema } from "@/schemas/signupSchema";

export default function SignupForm() {
  const router = useRouter();
  const formMethods = useForm<SignupFormValues>({
    resolver: zodResolver(signupSchema),
    mode: "onChange",
    reValidateMode: "onChange",
    defaultValues: {
      email: "",
      password: "",
      confirmPassword: "",
      nickname: "",
      verificationCode: "",
    },
  });

  useEffect(() => {
    useSignupStore.getState().resetSignupState();
  }, []);

  const { handleSubmit, setError, clearErrors } = formMethods;

  const { mutate, isPending: isSignupPending } = useSignup({
    setError,
    onSuccessCallback: () => {
      alert("회원가입이 완료되었습니다.");
      router.push("/login");
    },
    onErrorCallback: (errorMessage) => {
      alert(errorMessage);
    },
  });

  const onSubmit = async (values: SignupFormValues) => {
    const { isCodeSended } = useSignupStore.getState();

    if (isCodeSended && !values.verificationCode) {
      setError("verificationCode", { message: "인증 코드를 입력하세요." });
      return;
    }

    const dto: SignupFormData = {
      email: values.email,
      password: values.password,
      confirmPassword: values.confirmPassword,
      nickname: values.nickname,
    };
    console.log("회원가입 데이터:", dto);
    mutate(dto);
  };

  useEffect(() => {
    return () => {
      useSignupStore.getState().resetSignupState();
      clearErrors();
    };
  }, []);

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
      <CountdownProvider>
        <EmailInput<SignupFormValues> formMethods={formMethods} showVerificationUI />
        <CodeInput<SignupFormValues> formMethods={formMethods} />
      </CountdownProvider>
      <PasswordInputGroup<SignupFormValues> formMethods={formMethods} showVerificationUI />
      <NicknameInput<SignupFormValues> formMethods={formMethods} />
      <Button
        className="w-full bg-blue-600 text-white py-2 rounded hover:bg-blue-700 transition"
        type="submit"
        isLoading={isSignupPending}
        fullWidth
        disabled={isSignupPending}
      >
        {isSignupPending ? "회원가입 중..." : "회원가입"}
      </Button>
    </form>
  );
}
