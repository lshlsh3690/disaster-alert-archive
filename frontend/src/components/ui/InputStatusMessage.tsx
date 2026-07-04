import { FieldValues, Path, UseFormReturn } from "react-hook-form";

interface InputStatusMessageProps<T extends FieldValues> {
  name: Path<T>;
  formMethods: UseFormReturn<T>;
  isValid?: boolean;
  isPending?: boolean;
  isError?: boolean;
  message?: string;
}

export default function InputStatusMessage<T extends FieldValues>({
  name,
  formMethods,
  isValid,
  isPending,
  isError,
  message,
}: InputStatusMessageProps<T>) {
  const { formState } = formMethods;
  const error = formState.errors[name];

  // 우선순위: 에러 > 성공 > 전송 중 > 일반 메시지
  return (
    <p
      className={`text-sm mt-1 ${
        error || isError
          ? "text-[var(--coral)]"
          : isValid
          ? "text-[var(--success)]"
          : isPending
          ? "text-[var(--blue)]"
          : "text-[var(--text-muted)]"
      }`}
    >
      {error?.message?.toString() ?? message}
    </p>
  );
}
