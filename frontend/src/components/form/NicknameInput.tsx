import { useDebounce } from "@/hooks/useDebounce";
import useNicknameDuplicationCheck from "@/lib/mutations/useNicknameDuplicationCheck";
import { useEffect } from "react";
import { FieldValues, Path, UseFormReturn, useWatch } from "react-hook-form";
import useSignupStore from "@/store/signupStore";
import InputStatusMessage from "../ui/InputStatusMessage";
import { useI18n } from "@/hooks/useI18n";

interface NicknameInputProps<T extends FieldValues> {
  formMethods: UseFormReturn<T>;
  disabled?: boolean;
  defaultValue?: string;
}

export default function NicknameInput<T extends FieldValues>({ formMethods }: NicknameInputProps<T>) {
  const t = useI18n();
  const {
    control,
    setError,
    clearErrors,
    trigger,
    formState,
  } = formMethods;

  const setIsNicknameValid = useSignupStore((s) => s.setIsNicknameValid);
  const isNicknameValid = useSignupStore((s) => s.isNicknameValid);

  const nickname = useWatch({
    control,
    name: "nickname" as Path<T>,
  });

  const debouncedNickname = useDebounce(nickname, 600);

  const { mutate: checkNicknameDuplication, isPending } = useNicknameDuplicationCheck({
    onSuccessCallback: () => {
      clearErrors("nickname" as Path<T>);
      setIsNicknameValid(true);
    },
    onErrorCallback: (message) => {
      setError("nickname" as Path<T>, { message });
      setIsNicknameValid(false);
    },
  });

  useEffect(() => {
    if (debouncedNickname && debouncedNickname.length >= 2 && debouncedNickname.length <= 10) {
      trigger("nickname" as Path<T>, { shouldFocus: true });
      clearErrors("nickname" as Path<T>);
      setIsNicknameValid(false);
      checkNicknameDuplication(debouncedNickname);
    }
  }, [debouncedNickname]);

  const showSuccess = !!nickname && !formState.errors.nickname && isNicknameValid;

  return (
    <div className="space-y-2">
      <input
        type="text"
        {...formMethods.register("nickname" as Path<T>)}
        placeholder={t.form.nicknamePlaceholder}
        className="input"
      />

      <InputStatusMessage
        name={"nickname" as Path<T>}
        formMethods={formMethods}
        isPending={isPending}
        isValid={showSuccess}
        message={showSuccess ? t.form.nicknameValid : t.form.nicknamePrompt}
      />
    </div>
  );
}
