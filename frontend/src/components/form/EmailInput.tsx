import useSignupStore from "@/store/signupStore";
import { useEffect } from "react";
import { FieldValues, Path, UseFormReturn, useWatch } from "react-hook-form";
import Button from "@components/Button";
import { useSendEmailVerificationCode } from "@/lib/mutations/useSendEmailVerificationCode";
import InputStatusMessage from "../InputStatusMessage";
import { useOptionalCountdownContext } from "@/context/useCountdownContext";

interface EmailInputProps<T extends FieldValues> {
  formMethods: UseFormReturn<T>;
  disabled?: boolean;
  showVerificationUI?: boolean;
  defaultValue?: string;
}

export default function EmailInput<T extends FieldValues>({ formMethods, showVerificationUI }: EmailInputProps<T>) {
  const { control, trigger, setError, clearErrors } = formMethods;
  const isEmailVerified = useSignupStore((state) => state.isEmailVerified);
  const isCodeSended = useSignupStore((state) => state.isCodeSended);
  const setIsEmailVerified = useSignupStore((state) => state.setIsEmailVerified);
  const setIsCodeSended = useSignupStore((state) => state.setIsCodeSended);
  const setIsEmailCodeTimeout = useSignupStore((state) => state.setIsEmailCodeTimeout);

  const { countdownStart, countdownReset } = useOptionalCountdownContext();
  const email = useWatch({
    control,
    name: "email" as Path<T>,
  });

  useEffect(() => {
    if (isEmailVerified) {
      setIsEmailVerified(false);
      setIsCodeSended(false);
      setIsEmailCodeTimeout(false);
      countdownReset();
    }
    if (email && isCodeSended) {
      setIsEmailVerified(false);
      setIsCodeSended(false);
    }

    return () => {
      if (isCodeSended || isEmailVerified) {
        setIsEmailVerified(false);
        setIsCodeSended(false);
      }
    };
  }, [email]);

  const {
    mutate,
    isPending: isEmailCodeSending,
    data: emailCodeData,
    isSuccess,
  } = useSendEmailVerificationCode<T>({
    setError,
    onSuccessCallback: () => {
      if (!showVerificationUI) return;
      setIsCodeSended(true);
      console.log("after set:", useSignupStore.getState().isCodeSended); // ✅ true
      setIsEmailVerified(false);
      setIsEmailCodeTimeout(false);
      clearErrors("email" as Path<T>);
      clearErrors("verificationCode" as Path<T>);
      countdownStart(180);
    },
    onErrorCallback: (message) => {
      if (!showVerificationUI) return;
      setIsCodeSended(false);
      setIsEmailVerified(false);
      setIsEmailCodeTimeout(false);
      setError("email" as Path<T>, { message });
    },
  });

  const handleSendVerification = async (e: React.MouseEvent<HTMLButtonElement>) => {
    e.preventDefault();
    //shouldFocus: true 옵션을 사용하여 유효성 검사 실패 시 포커스 이동
    const valid = await trigger("email" as Path<T>, { shouldFocus: true });
    if (!valid) return;

    mutate(email);
  };

  const statusMessage =
    emailCodeData?.message ??
    (isEmailVerified ? "" : isCodeSended ? "인증 코드를 전송했습니다." : "이메일을 입력하고 코드를 요청하세요.");

  return (
    <div className="space-y-2">
      <div className="flex gap-2">
        <input
          type="email"
          {...formMethods.register("email" as Path<T>)}
          placeholder="이메일"
          className="flex-1 border rounded px-3 py-2"
        />
        {showVerificationUI && (
          <Button type="button" onClick={handleSendVerification} isLoading={isEmailCodeSending}>
            인증 코드 받기
          </Button>
        )}
      </div>

      {showVerificationUI && (
        <InputStatusMessage
          name={"email" as Path<T>}
          formMethods={formMethods}
          isPending={isEmailCodeSending}
          isValid={isEmailVerified || isCodeSended || isSuccess}
          message={statusMessage}
        />
      )}
    </div>
  );
}
