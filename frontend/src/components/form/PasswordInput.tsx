import { useState } from "react";
import { FieldValues, Path, UseFormReturn } from "react-hook-form";
import InputStatusMessage from "../ui/InputStatusMessage";
import useSignupStore from "@/store/signupStore";
import { useI18n } from "@/hooks/useI18n";

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
  const t = useI18n();
  const { register, watch, formState } = formMethods;
  const value = watch(name);
  const isPasswordMatched = useSignupStore((s) => s.isPasswordMatched);
  const error = formState.errors[name];
  const [showPassword, setShowPassword] = useState(false);

  const labels: Record<string, string> = {
    password: t("form.passwordLabel"),
    confirmPassword: t("form.confirmPasswordLabel"),
  };

  const message: Record<string, string> = {
    password: t("form.passwordRequired"),
    confirmPassword: t("form.confirmPasswordRequired"),
  };

  const hasValue = value.trim().length > 0;

  return (
    <div className="space-y-2">
      <div className="relative">
        <input
          type={showPassword ? "text" : "password"}
          placeholder={labels[name] ?? t("form.passwordPlaceholderDefault")}
          {...register(name)}
          className="input pr-10"
        />
        <button
          type="button"
          className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1 text-[var(--text-subtle)] transition-colors hover:text-[var(--text-body)] focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--blue-soft)]"
          aria-label={showPassword ? t("form.hidePassword") : t("form.showPassword")}
          onClick={() => setShowPassword((prev) => !prev)}
        >
          {showPassword ? (
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M9.88 9.88a3 3 0 0 0 4.24 4.24" />
              <path d="M10.73 5.08A10.43 10.43 0 0 1 12 5c7 0 10 7 10 7a13.16 13.16 0 0 1-1.67 2.68" />
              <path d="M6.61 6.61A13.5 13.5 0 0 0 2 12s3 7 10 7a9.7 9.7 0 0 0 5.39-1.61" />
              <line x1="2" y1="2" x2="22" y2="22" />
            </svg>
          ) : (
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7z" />
              <circle cx="12" cy="12" r="3" />
            </svg>
          )}
        </button>
      </div>
      {showVerificationUI && (
        <InputStatusMessage
          name={name}
          formMethods={formMethods}
          isValid={hasValue && isPasswordMatched}
          message={
            hasValue && isPasswordMatched
              ? t("form.passwordMatch")
              : hasValue && !isPasswordMatched && !error
              ? t("form.passwordValid")
              : message[name]
          }
        />
      )}
    </div>
  );
}
