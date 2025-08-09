import { useEffect, useState } from "react";

export default function useCountdown(seconds: number) {
  const [timeLeft, setTimeLeft] = useState<number>(seconds);
  const [isRunning, setIsRunning] = useState<boolean>(false);

  useEffect(() => {
    if (!isRunning || timeLeft <= 0) return;

    const timer = setInterval(() => {
      setTimeLeft((prev) => prev - 1);
    }, 1000);

    return () => clearInterval(timer);
  }, [isRunning, timeLeft]);

  const start = () => {
    setTimeLeft(seconds);
    setIsRunning(true);
  };

  const reset = () => {
    setTimeLeft(0);
    setIsRunning(false);
  };

  const minutes = Math.floor(timeLeft / 60)
    .toString()
    .padStart(2, "0");
  const secs = (timeLeft % 60).toString().padStart(2, "0");

  return {
    timeLeft,
    isRunning,
    start,
    reset,
    formatted: `${minutes}:${secs}`,
  };
}