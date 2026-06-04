import { ButtonHTMLAttributes } from "react";
import clsx from "clsx";

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  children: React.ReactNode;
  isLoading?: boolean;
  fullWidth?: boolean;
}

export default function Button({ children, isLoading, fullWidth, className, disabled, ...props }: ButtonProps) {
  return (
    <button
      {...props}
      className={
        className
          ? className
          : clsx(
              "px-3 py-2 border rounded bg-gray-100 hover:bg-gray-200 text-sm whitespace-nowrap",
              fullWidth && "w-full"
            )
      }
      disabled={isLoading || disabled}
    >
      {isLoading ? "전송 중" : children}
    </button>
  );
}
