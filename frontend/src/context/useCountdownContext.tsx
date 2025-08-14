import React, { createContext, useContext, useState, useEffect, useCallback } from "react";

interface CountdownContextType {
  secondsLeft: number;
  formatted: string;
  countdownStart: (duration: number) => void;
  countdownReset: () => void;
}

const CountdownContext = createContext<CountdownContextType | undefined>(undefined);

export const CountdownProvider = ({ children }: { children: React.ReactNode }) => {
  const [secondsLeft, setSecondsLeft] = useState<number>(0);
  const [formatted, setFormatted] = useState<string>("");
  const [timer, setTimer] = useState<NodeJS.Timeout | null>(null);

  useEffect(() => {
    const minutes = String(Math.floor(secondsLeft / 60)).padStart(2, "0");
    const secs = String(secondsLeft % 60).padStart(2, "0");
    setFormatted(`${minutes}:${secs}`);
  }, [secondsLeft]);

  useEffect(() => {
    return () => {
      countdownReset();
    };
  }, []);

  const countdownStart = useCallback(
    (duration: number) => {
      if (timer) {
        clearInterval(timer);
        setTimer(null);
      }
      setSecondsLeft(duration);
      const newTimer = setInterval(() => {
        setSecondsLeft((prev) => {
          if (prev <= 1) {
            setTimer(null);
            clearInterval(newTimer);
            return 0;
          }
          return prev - 1;
        });
      }, 1000);
      setTimer(newTimer);
    },
    [timer, secondsLeft, setSecondsLeft, setTimer]
  );

  const countdownReset = () => {
    clearInterval(timer!);
    setSecondsLeft(0);
    setTimer(null);
  };

  useEffect(() => {
    return () => {
      clearInterval(timer!);
    };
  }, [timer]);

  return (
    <CountdownContext.Provider value={{ secondsLeft, formatted, countdownStart, countdownReset }}>
      {children}
    </CountdownContext.Provider>
  );
};

export const useCountdownContext = () => {
  const context = useContext(CountdownContext);
  if (!context) {
    throw new Error("useCountdownContext must be used within a CountdownProvider");
  }
  return context;
};

export const useOptionalCountdownContext = () => {
  const ctx = useContext(CountdownContext);
  return (
    ctx ?? {
      secondsLeft: 0,
      formatted: "",
      countdownStart: () => {},
      countdownReset: () => {},
    }
  );
};