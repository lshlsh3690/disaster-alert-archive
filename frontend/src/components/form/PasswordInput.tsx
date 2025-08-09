import { FieldValues, Path, UseFormReturn } from "react-hook-form";
import InputStatusMessage from "../InputStatusMessage";
import useSignupStore from "@/store/signupStore";

interface PasswordInput<T extends FieldValues> {
  formMethods: UseFormReturn<T>;
  name: Path<T>;
}

export default function PasswordInput<T extends FieldValues>({ formMethods, name }: PasswordInput<T>) {
  const {
    register,
    watch,
  } = formMethods;

  const value = watch(name);
  const isPasswordMatched = useSignupStore((s) => s.isPasswordMatched);

  const placeholders: Record<string, string> = {
    password: "비밀번호",
    confirmPassword: "비밀번호 확인",
  };

  const placeholder = placeholders[name] || "입력";

  return (
    <div className="space-y-4">
      <input
        type="password"
        placeholder={placeholder}
        {...register(name)}
        className="w-full border rounded px-3 py-2"
      />
      <InputStatusMessage
        name={name}
        formMethods={formMethods}
        isValid={!!value && isPasswordMatched}
        message={value && isPasswordMatched ? "유효한 비밀번호입니다." : ""}
      />
    </div>
  );
}
