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
import Button from "@/components/ui/Button";
import NicknameInput from "@/components/form/NicknameInput";
import { zodResolver } from "@hookform/resolvers/zod";
import { SignupFormData, SignupFormValues, signup } from "@/types/signup";
import { useI18n } from "@/hooks/useI18n";

export default function SignupForm() {
  const t = useI18n();
  const router = useRouter();
  const formMethods = useForm<SignupFormValues>({
    resolver: zodResolver(signup),
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
      alert(t.signup.completed);
      router.push("/login");
    },
    onErrorCallback: (errorMessage) => {
      alert(errorMessage);
    },
  });

  const onSubmit = async (values: SignupFormValues) => {
    const { isCodeSended } = useSignupStore.getState();

    if (isCodeSended && !values.verificationCode) {
      setError("verificationCode", { message: t.signup.verificationCodeRequired });
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
      <Button type="submit" isLoading={isSignupPending} fullWidth disabled={isSignupPending}>
        {t.signup.title}
      </Button>
      <Button type="button" variant="secondary" fullWidth onClick={() => router.back()}>
        {t.signup.cancel}
      </Button>
    </form>
  );
}
