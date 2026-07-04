import { ButtonHTMLAttributes } from "react";
import clsx from "clsx";

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  children: React.ReactNode;
  isLoading?: boolean;
  fullWidth?: boolean;
  variant?: "primary" | "secondary" | "danger";
}

export default function Button({
  children,
  isLoading,
  fullWidth,
  variant = "primary",
  className,
  disabled,
  ...props
}: ButtonProps) {
  return (
    <button
      {...props}
      disabled={disabled || isLoading}
      className={clsx(
        "inline-flex items-center justify-center gap-1.5 rounded-[var(--radius-control)] px-4 py-2 text-sm font-medium transition focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-[var(--blue)]",
        fullWidth && "w-full",
        (disabled || isLoading) && "opacity-60 cursor-not-allowed",
        variant === "primary" && "bg-[var(--blue)] text-white hover:brightness-95",
        variant === "secondary" &&
          "border border-[var(--line)] bg-[var(--surface)] text-[var(--text-body)] hover:bg-[var(--blue-soft)]",
        variant === "danger" && "bg-[var(--coral)] text-white hover:brightness-95",
        className
      )}
    >
      {isLoading && (
        <svg className="animate-spin" width="15" height="15" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="3" strokeOpacity="0.25" />
          <path d="M21 12a9 9 0 0 0-9-9" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
        </svg>
      )}
      {children}
    </button>
  );
}
