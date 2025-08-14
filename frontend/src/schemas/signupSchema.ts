import { z } from "zod";

/**
z.email();
z.uuid();
z.url();
z.hostname();
z.emoji();         // validates a single emoji character
z.base64();
z.base64url();
z.jwt();
z.nanoid();
z.cuid();
z.cuid2();
z.ulid();
z.ipv4();
z.ipv6();
z.cidrv4();        // ipv4 CIDR block
z.cidrv6();        // ipv6 CIDR block
z.iso.date();
z.iso.time();
z.iso.datetime();
z.iso.duration();
 */
export const signupSchema = z
  .object({
    email: z.email("올바른 이메일 형식이 아닙니다.").nonempty("이메일을 입력하세요."),
    password: z
      .string()
      .nonempty("비밀번호를 입력하세요.")
      .trim()
      .regex(/^(?=.*[a-zA-Z])(?=.*\d)(?=.*[!@#$%^&*])[A-Za-z\d!@#$%^&*]{4,20}$/, {
        message: "비밀번호는 4자 이상, 20자 이하이며, 영문, 숫자, 특수문자를 포함해야 합니다.",
      }),
    confirmPassword: z
      .string()
      .nonempty("비밀번호 확인을 입력하세요.")
      .trim()
      .regex(/^(?=.*[a-zA-Z])(?=.*\d)(?=.*[!@#$%^&*])[A-Za-z\d!@#$%^&*]{4,20}$/, {
        message: "비밀번호 확인은 4자 이상, 20자 이하이며, 영문, 숫자, 특수문자를 포함해야 합니다.",
      }),
    nickname: z
      .string()
      .nonempty("닉네임을 입력하세요.")
      .trim()
      .regex(/^[a-zA-Z0-9가-힣]+$/, "닉네임은 영문, 숫자, 한글만 사용할 수 있습니다.")
      .min(2, "닉네임은 최소 2자 이상이어야 합니다.")
      .max(10, "닉네임은 최대 10자까지 입력할 수 있습니다."),
    verificationCode: z
      .string()
      .nonempty("인증 코드를 입력하세요.")
      .trim()
      .min(6, "인증 코드는 6자리입니다.")
      .max(6, "인증 코드는 6자리입니다.")
      .optional(),
  });

export type SignupFormData = z.infer<typeof signupSchema>;

// 폼 전용 값: UI에서만 쓰는 verificationCode 포함 (DTO 아님)
// 실제 API 요청 시에는 verificationCode를 제외하고 SignupFormData를 사용
// zod의 유효성 검증을 하기 위해서 임시적으로 지정
export type SignupFormValues = SignupFormData & {
  verificationCode?: string;
};
