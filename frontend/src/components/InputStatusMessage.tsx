import { FieldValues, Path, UseFormReturn } from "react-hook-form";

interface InputStatusMessageProps<T extends FieldValues> {
  name: Path<T>;
  formMethods: UseFormReturn<T>;
  isValid?: boolean;
  isPending?: boolean;
  message?: string;
}

export default function InputStatusMessage<T extends FieldValues>({
  name,
  formMethods,
  isValid,
  isPending,
  message,
}: InputStatusMessageProps<T>) {
  const { formState } = formMethods;
  const error = formState.errors[name];

  // 우선순위: 에러 > 성공 > 전송 중 > 일반 메시지
  return (
    <p
      className={`text-sm mt-1 ${
        error
          ? "text-red-500"
          : isValid
          ? "text-green-600"
          : isPending
          ? "text-blue-500"
          : "text-gray-500"
      }`}
    >
      {error?.message?.toString() ?? message}
    </p>
  );
}
