"use client";

import Link from "next/link";
import OAuthButton from "@/components/OAuthButton";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import useLogin from "@/lib/mutations/useLogin";
import EmailInput from "@/components/form/EmailInput";
import { useForm } from "react-hook-form";
import PasswordInput from "@/components/form/PasswordInput";
import { useAuthStore } from "@/store/authStore";

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
    onSuccessCallback: () => router.push("/dashboard"),
    onErrorCallback: (errorMessage) => setError(errorMessage),
  });

  const handleLogin = async (value: LoginFormData) => {
    login(value);
  };

  return (
    <main className="min-h-screen flex flex-col items-center justify-center p-4 bg-gray-50">
      <div className="w-full max-w-md bg-white rounded-xl shadow p-6 space-y-4">
        <h1 className="text-2xl font-bold text-center">🔐 로그인</h1>
        <form onSubmit={handleSubmit(handleLogin)} className="space-y-4">
          <EmailInput<LoginFormData> formMethods={formMethods} />
          <PasswordInput<LoginFormData> formMethods={formMethods} name="password" />
          {error && <p className="text-red-500 text-sm">{error}</p>}
          <button type="submit" className="w-full bg-blue-600 text-white py-2 rounded hover:bg-blue-700 transition">
            로그인
          </button>
          <button
            type="button"
            onClick={() => router.back()}
            className="w-full bg-gray-100 text-gray-700 py-2 rounded hover:bg-gray-200 transition"
          >
            취소
          </button>
        </form>
        {/*체크 박스를 사용해서 로그인 저장하기 */}
        <div className="flex items-center">
          <input type="checkbox" id="rememberMe" className="mr-2" />
          <label htmlFor="rememberMe" className="text-sm text-gray-600">
            로그인 상태 유지
          </label>
        </div>

        <p className="text-sm text-center text-gray-600">
          계정이 없으신가요?{" "}
          <Link href="/signup" className="text-blue-600 hover:underline font-medium">
            회원가입
          </Link>
        </p>
      </div>
      <div className="w-full max-w-md mt-4 bg-white rounded-xl shadow p-6 space-y-3">
        <p className="text-sm text-center text-gray-500">간편 로그인</p>
        <OAuthButton provider="google" />
        <OAuthButton provider="kakao" />
        <OAuthButton provider="naver" />
      </div>
    </main>
  );
}
