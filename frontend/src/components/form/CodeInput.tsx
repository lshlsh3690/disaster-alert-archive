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
  const {
    control,
    trigger,
    setError,
    clearErrors,
    formState: { errors },
  } = formMethods;
  const isCodeSended = useSignupStore((s) => s.isCodeSended);
  const isEmailVerified = useSignupStore((s) => s.isEmailVerified);
  const { secondsLeft, formatted, reset } = useCountdownContext();

  const email = useWatch({ control, name: "email" as Path<T> });
  const code = useWatch({ control, name: "verificationCode" as Path<T> });

  const setIsEmailVerified = useSignupStore((s) => s.setIsEmailVerified);

  const { mutate, isPending: isCodeVerifying } = useEmailCodeVerify({
    onSuccessCallback: () => {
      setIsEmailVerified(true);
      alert("인증 코드가 확인되었습니다.");
      clearErrors("verificationCode" as Path<T>);
      reset();
    },
    onErrorCallback: (errorMessage) => {
      setIsEmailVerified(false);
      setError("verificationCode" as Path<T>, { message: errorMessage });
      console.error("인증 코드 오류:", errorMessage);
    },
  });
  const handleVerifyCode = async (e: React.MouseEvent<HTMLButtonElement>) => {
    e.preventDefault();

    const isCodeValid = await trigger("verificationCode" as Path<T>, { shouldFocus: true });
    if (!isCodeValid) return;
    mutate({
      email,
      code,
    });
  };

  useEffect(() => {
    if (isEmailVerified) return;

    if (isCodeSended && secondsLeft === 0) {
      setIsEmailVerified(false);
      setError("verificationCode" as Path<T>, { message: "인증 시간이 만료되었습니다. 코드를 다시 요청하세요." });
    }
    if (
      isCodeSended &&
      secondsLeft > 0 &&
      errors["verificationCode" as Path<T>]?.message === "인증 시간이 만료되었습니다. 코드를 다시 요청하세요."
    ) {
      clearErrors("verificationCode" as Path<T>);
    }
  }, [isCodeSended, secondsLeft, setError, clearErrors, setIsEmailVerified, errors]);

  useEffect(() => {
    return () => setIsEmailVerified(false);
  }, [setIsEmailVerified]);

  return (
    <div className="space-y-2">
      <div className="flex gap-2">
        <input
          type="text"
          placeholder="인증 코드"
          {...formMethods.register("verificationCode" as Path<T>)}
          className="w-full border rounded px-3 py-2"
        />

        <Button type="button" onClick={handleVerifyCode} isLoading={isCodeVerifying}>
          인증 확인
        </Button>
      </div>

      {/* {isCodeSended && secondsLeft > 0 && (
      
        <p className="text-sm text-blue-600">남은 시간: {formatted} 내에 인증을 완료하세요.</p>
      )}

      {errors["verificationCode" as Path<T>] && (
        <p className="text-red-500 text-sm">{String(errors["verificationCode" as Path<T>]?.message)}</p>
      )} */}

      <InputStatusMessage
        name={"verificationCode" as Path<T>}
        formMethods={formMethods}
        isValid={isEmailVerified}
        isPending={isCodeVerifying}
        message={
          isEmailVerified
            ? "이메일 인증이 완료되었습니다."
            : isCodeSended && secondsLeft > 0
            ? `남은 시간: ${formatted} 내에 인증을 완료하세요.`
            : "인증 코드를 입력하세요."
        }
      />
    </div>
  );
}
