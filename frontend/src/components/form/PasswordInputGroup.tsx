import { FieldValues, Path, UseFormReturn, useWatch } from "react-hook-form";
import PasswordInput from "./PasswordInput";
import { useEffect } from "react";
import useSignupStore from "@/store/signupStore";
import { useDebounce } from "@/hooks/useDebounce";

interface PasswordInputGroupProps<T extends FieldValues> {
  formMethods: UseFormReturn<T>;
  showVerificationUI?: boolean;
}

export function PasswordInputGroup<T extends FieldValues>({
  formMethods,
  showVerificationUI,
}: PasswordInputGroupProps<T>) {
  const { control, formState } = formMethods;
  const setIsPasswordMatched = useSignupStore((s) => s.setIsPasswordMatched);

  const pwd = useWatch({ control, name: "password" as Path<T> }) as string;
  const cpw = useWatch({ control, name: "confirmPassword" as Path<T> }) as string;

  const dPwd = useDebounce(pwd, 300);
  const dCpw = useDebounce(cpw, 300);

  useEffect(() => {
    if (!showVerificationUI) return;
    const bothFilled = dPwd.length > 0 && dCpw.length > 0;
    const hasSchemaError = !!formState.errors.password || !!formState.errors.confirmPassword;

    setIsPasswordMatched(bothFilled && !hasSchemaError && dPwd === dCpw);
  }, [dPwd, dCpw, !!formState.errors.password, !!formState.errors.confirmPassword, setIsPasswordMatched]);

  return (
    <div className="space-y-2">
      <PasswordInput<T> formMethods={formMethods} name={"password" as Path<T>} showVerificationUI />
      <PasswordInput<T> formMethods={formMethods} name={"confirmPassword" as Path<T>} showVerificationUI />
    </div>
  );
}
