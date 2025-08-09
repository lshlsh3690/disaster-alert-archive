import { FieldValues, Path, UseFormReturn, useWatch } from "react-hook-form";
import PasswordInput from "./PasswordInput";
import { useEffect } from "react";
import useSignupStore from "@/store/signupStore";
import { useDebounce } from "@/hooks/useDebounce";

interface PasswordInputGroupProps<T extends FieldValues> {
  formMethods: UseFormReturn<T>;
}

export function PasswordInputGroup<T extends FieldValues>({ formMethods }: PasswordInputGroupProps<T>) {
  const { control, formState } = formMethods;
  const setIsPasswordMatched = useSignupStore((s) => s.setIsPasswordMatched);

  const password = useWatch({ control, name: "password" as Path<T> });
  const confirmPassword = useWatch({ control, name: "confirmPassword" as Path<T> });

  const dPwd = useDebounce((password as Path<T>) ?? "", 1000);
  const dCpw = useDebounce((confirmPassword as Path<T>) ?? "", 1000);

  useEffect(() => {
    // 길이/형식 등 스키마 에러가 있는 동안은 매칭 플래그 false
    const hasSchemaError = !!formState.errors.password || !!formState.errors.confirmPassword;

    // 둘 다 비어있거나 확인값이 아직 없으면 false로
    if (!dPwd && !dCpw) {
      setIsPasswordMatched(false);
      return;
    }
    if (!dPwd || !dCpw || hasSchemaError) {
      setIsPasswordMatched(false);
      return;
    }
    setIsPasswordMatched(dPwd === dCpw);
  }, [dPwd, dCpw, formState.errors.password, formState.errors.confirmPassword, setIsPasswordMatched]);

  return (
    <div className="space-y-4">
      <PasswordInput<T> formMethods={formMethods} name={"password" as Path<T>} />
      <PasswordInput<T> formMethods={formMethods} name={"confirmPassword" as Path<T>} />
    </div>
  );
}
