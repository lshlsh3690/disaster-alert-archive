"use client";

import { useEffect } from "react";
import { useWatch, Path, FieldValues } from "react-hook-form";
import { UseFormReturn } from "react-hook-form";
import useSignupStore from "@/store/signupStore";
import Button from "../Button";
import { useCountdownContext } from "@/context/useCountdownContext";
import { useEmailCodeVerify } from "@/lib/mutations/useEmailCodeVerify";
import InputStatusMessage from "../InputStatusMessage";

interface CodeInputProps<T extends FieldValues> {
  formMethods: UseFormReturn<T>;
}

export default function CodeInput<T extends FieldValues>({ formMethods }: CodeInputProps<T>) {
  const { control, trigger, setError, clearErrors } = formMethods;
  const isCodeSended = useSignupStore((s) => s.isCodeSended);
  const isEmailVerified = useSignupStore((s) => s.isEmailVerified);
  const isEmailCodeTimeout = useSignupStore((s) => s.isEmailCodeTimeout);
  const setIsEmailVerified = useSignupStore((s) => s.setIsEmailVerified);
  const setIsEmailCodeTimeout = useSignupStore((state) => state.setIsEmailCodeTimeout);

  const { secondsLeft, formatted } = useCountdownContext();

  const email = useWatch({ control, name: "email" as Path<T> });
  const code = useWatch({ control, name: "verificationCode" as Path<T> });

  const { mutate, isPending: isCodeVerifying } = useEmailCodeVerify({
    onSuccessCallback: () => {
      setIsEmailVerified(true);
      alert("인증 코드가 확인되었습니다.");
      clearErrors("verificationCode" as Path<T>);
    },
    onErrorCallback: (errorMessage) => {
      setIsEmailVerified(false);
      setError("verificationCode" as Path<T>, { message: errorMessage });
    },
  });

  const handleVerifyCode = async (e: React.MouseEvent<HTMLButtonElement>) => {
    e.preventDefault();

    const isCodeValid = await trigger("verificationCode" as Path<T>, { shouldFocus: true });
    if (!isCodeValid) return;

    console.log("isCodeSended ", isCodeSended);
    if (!isCodeSended) {
      setError("verificationCode" as Path<T>, { message: "인증 코드를 먼저 요청하세요." });
      return;
    }

    if (isEmailCodeTimeout) {
      setError("verificationCode" as Path<T>, { message: "인증 시간이 만료되었습니다. 코드를 다시 요청하세요." });
      return;
    }
    mutate({
      email,
      code,
    });
  };

  useEffect(() => {
    if (isEmailVerified) return;

    if (isCodeSended && secondsLeft === 0) {
      setIsEmailCodeTimeout(true);
    }
    if (code && isCodeSended) {
      setIsEmailVerified(false);
    }
  }, [isCodeSended, secondsLeft, isEmailVerified, code, setIsEmailVerified, setIsEmailCodeTimeout]);

  return (
    <div className="space-y-2">
      <div className="flex gap-2">
        <input
          type="text"
          placeholder="인증 코드"
          {...formMethods.register("verificationCode" as Path<T>)}
          disabled={isEmailVerified}
          className={`w-full border rounded px-3 py-2 
    ${isEmailVerified ? "bg-gray-100 text-gray-500" : "bg-white"}`}
        />

        <Button type="button" onClick={handleVerifyCode} isLoading={isCodeVerifying} disabled={isEmailVerified}>
          인증 확인
        </Button>
      </div>

      <InputStatusMessage
        name={"verificationCode" as Path<T>}
        formMethods={formMethods}
        isValid={isEmailVerified && !isEmailCodeTimeout}
        isError={isEmailCodeTimeout}
        isPending={isCodeVerifying}
        message={
          isEmailVerified
            ? "이메일인증이 완료되었습니다."
            : isEmailCodeTimeout
            ? "인증 시간이 만료되었습니다. 코드를 다시 요청하세요."
            : secondsLeft > 0
            ? `인증 코드를 입력하세요. 남은 시간 : ${formatted}`
            : "이메일을 입력하고 코드를 요청하세요."
        }
      />
    </div>
  );
}
