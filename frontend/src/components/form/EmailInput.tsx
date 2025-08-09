import useSignupStore from "@/store/signupStore";
import { useEffect } from "react";
import { FieldValues, Path, UseFormReturn, useWatch } from "react-hook-form";
import Button from "@components/Button";
import { useSendEmailVerificationCode } from "@/lib/mutations/useSendEmailVerificationCode";
import { useCountdownContext } from "@/context/useCountdownContext";
import InputStatusMessage from "../InputStatusMessage";

interface EmailInputProps<T extends FieldValues> {
  formMethods: UseFormReturn<T>;
  disabled?: boolean;
  defaultValue?: string;
  startCountdown?: () => void;
}

export default function EmailInput<T extends FieldValues>({ formMethods }: EmailInputProps<T>) {
  const { control, trigger, setError } = formMethods;
  const isEmailVerified = useSignupStore((state) => state.isEmailVerified);
  const isCodeSended = useSignupStore((state) => state.isCodeSended);
  const setIsEmailVerified = useSignupStore((state) => state.setIsEmailVerified);
  const setIsCodeSended = useSignupStore((state) => state.setIsCodeSended);

  const email = useWatch({
    control,
    name: "email" as Path<T>,
  });

  useEffect(() => {
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

  const { start } = useCountdownContext();

  const { mutate, isPending: isEmailCodeSending } = useSendEmailVerificationCode({
    onSuccessCallback: () => {
      start(180);
      setIsCodeSended(true);
      setIsEmailVerified(false);
    },
    onErrorCallback: (message) => {
      console.log(typeof message, message);

      setIsCodeSended(true);
      setIsEmailVerified(false);
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

  return (
    <div className="space-y-2">
      <div className="flex gap-2">
        <input
          type="email"
          {...formMethods.register("email" as Path<T>)}
          placeholder="이메일"
          className="flex-1 border rounded px-3 py-2"
        />
        <Button type="button" onClick={handleSendVerification} isLoading={isEmailCodeSending}>
          인증 코드 받기
        </Button>
      </div>
      {/* {errors["email" as Path<T>] && (
        <p className="text-red-500 text-sm">{String(errors["email" as Path<T>]?.message)}</p>
      )} */}
      <InputStatusMessage
        name={"email" as Path<T>}
        formMethods={formMethods}
        isPending={isEmailCodeSending}
        isValid={isEmailVerified || isCodeSended}
        message={
          isEmailVerified ? "" : isCodeSended ? `인증 코드를 전송했습니다. ` : "이메일을 입력하고 코드를 요청하세요."
        }
      />
    </div>
  );
}
