import { FieldValues, Path, UseFormReturn } from "react-hook-form";
import InputStatusMessage from "../InputStatusMessage";
import useSignupStore from "@/store/signupStore";

interface PasswordInput<T extends FieldValues> {
  formMethods: UseFormReturn<T>;
  name: Path<T>;
  showVerificationUI?: boolean;
}

export default function PasswordInput<T extends FieldValues>({
  formMethods,
  name,
  showVerificationUI,
}: PasswordInput<T>) {
  const { register, watch, formState } = formMethods;
  const value = watch(name);
  const isPasswordMatched = useSignupStore((s) => s.isPasswordMatched);
  const error = formState.errors[name];

  const labels: Record<string, string> = {
    password: "비밀번호",
    confirmPassword: "비밀번호 확인",
  };

  const message: Record<string, string> = {
    password: "비밀번호를 입력하세요.",
    confirmPassword: "비밀번호 확인을 입력하세요.",
  };

  const hasValue = value.trim().length > 0;

  return (
    <div className="space-y-2">
      <input
        type="password"
        placeholder={labels[name] ?? "입력"}
        {...register(name)}
        className="w-full border rounded px-3 py-2"
      />
      {showVerificationUI && (
        <InputStatusMessage
          name={name}
          formMethods={formMethods}
          isValid={hasValue && isPasswordMatched}
          message={
            hasValue && isPasswordMatched
              ? "비밀번호가 일치합니다."
              : hasValue && !isPasswordMatched && !error
              ? "유효한 비밀번호입니다."
              : message[name]
          }
        />
      )}
    </div>
  );
}
