"use client";

import { useEffect } from "react";
import { useWatch, Path, FieldValues } from "react-hook-form";
import { UseFormReturn } from "react-hook-form";
import useSignupStore from "@/store/signupStore";
import Button from "../ui/Button";
import { useCountdownContext } from "@/context/useCountdownContext";
import { useEmailCodeVerify } from "@/lib/mutations/useEmailCodeVerify";
import InputStatusMessage from "../ui/InputStatusMessage";
import { useI18n } from "@/hooks/useI18n";
import { formatMessage } from "@/utils/formatMessage";

interface CodeInputProps<T extends FieldValues> {
  formMethods: UseFormReturn<T>;
}

export default function CodeInput<T extends FieldValues>({ formMethods }: CodeInputProps<T>) {
  const t = useI18n();
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
      alert(t.form.codeVerified);
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
      setError("verificationCode" as Path<T>, { message: t.form.codeRequestFirst });
      return;
    }

    if (isEmailCodeTimeout) {
      setError("verificationCode" as Path<T>, { message: t.form.codeExpired });
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
          placeholder={t.form.codePlaceholder}
          {...formMethods.register("verificationCode" as Path<T>)}
          disabled={isEmailVerified}
          className={`input ${isEmailVerified ? "bg-[#f1f3f6] text-[var(--text-subtle)]" : ""}`}
        />

        <Button type="button" onClick={handleVerifyCode} isLoading={isCodeVerifying} disabled={isEmailVerified}>
          {t.form.codeVerify}
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
            ? t.form.codeEmailVerified
            : isEmailCodeTimeout
            ? t.form.codeExpired
            : secondsLeft > 0
            ? formatMessage(t.form.codeTimeRemaining, { time: formatted })
            : t.form.emailPrompt
        }
      />
    </div>
  );
}
