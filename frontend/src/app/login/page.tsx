"use client";

import Link from "next/link";
import OAuthButton from "@/components/ui/OAuthButton";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import useLogin from "@/lib/mutations/useLogin";
import EmailInput from "@/components/form/EmailInput";
import { useForm } from "react-hook-form";
import PasswordInput from "@/components/form/PasswordInput";
import { useAuthStore } from "@/store/authStore";
import Button from "@/components/ui/Button";

interface LoginFormData {
  email: string;
  password: string;
  isRememberMe?: boolean;
}

export default function LoginPage() {
  const router = useRouter();
  const user = useAuthStore((s) => s.user);
  const [error, setError] = useState("");

  useEffect(() => {
    if (user) router.replace("/");
  }, [user, router]);

  const formMethods = useForm<LoginFormData>({
    mode: "onChange",
    defaultValues: {
      email: "",
      password: "",
      isRememberMe: false,
    },
  });

  const { handleSubmit } = formMethods;

  const { mutate: login } = useLogin({
    onSuccessCallback: () => router.push("/"),
    onErrorCallback: (errorMessage) => setError(errorMessage),
  });

  const handleLogin = async (value: LoginFormData) => {
    login(value);
  };

  return (
    <main className="flex min-h-[calc(100vh-48px)] flex-col items-center justify-center bg-[var(--canvas)] p-4">
      <div className="w-full max-w-md space-y-4 rounded-[var(--radius-panel-card)] border border-[var(--line)] bg-[var(--surface)] p-6 shadow-[0_10px_30px_rgba(28,39,60,0.04)]">
        <h1 className="text-center text-2xl font-bold tracking-tight text-[var(--ink)]">로그인</h1>
        <form onSubmit={handleSubmit(handleLogin)} className="space-y-4">
          <EmailInput<LoginFormData> formMethods={formMethods} />
          <PasswordInput<LoginFormData> formMethods={formMethods} name="password" />
          {error && <p className="text-sm text-[var(--coral)]">{error}</p>}
          <Button type="submit" fullWidth className="h-11">
            로그인
          </Button>
          <Button type="button" variant="secondary" fullWidth className="h-11" onClick={() => router.back()}>
            취소
          </Button>
        </form>
        {/*체크 박스를 사용해서 로그인 저장하기 */}
        <div className="flex items-center">
          <input type="checkbox" id="rememberMe" className="mr-2 accent-[var(--blue)]" />
          <label htmlFor="rememberMe" className="text-[13px] text-[var(--text-muted)]">
            로그인 상태 유지
          </label>
        </div>

        <p className="text-center text-[13px] text-[var(--text-muted)]">
          계정이 없으신가요?{" "}
          <Link href="/signup" className="font-medium text-[var(--blue)] hover:underline">
            회원가입
          </Link>
        </p>
      </div>
      <div className="mt-4 w-full max-w-md rounded-[var(--radius-panel-card)] border border-[var(--line)] bg-[var(--surface)] p-6 shadow-[0_10px_30px_rgba(28,39,60,0.04)]">
        <div className="mb-3 flex items-center gap-3">
          <span className="h-px flex-1 bg-[var(--line)]"></span>
          <span className="text-[13px] text-[var(--text-subtle)]">간편 로그인</span>
          <span className="h-px flex-1 bg-[var(--line)]"></span>
        </div>
        <div className="space-y-2.5">
          <OAuthButton provider="google" />
          <OAuthButton provider="kakao" />
          <OAuthButton provider="naver" />
        </div>
      </div>
    </main>
  );
}
