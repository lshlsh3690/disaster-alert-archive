"use client";

import { QueryClientProvider } from "@tanstack/react-query";
import { queryClient } from "./queryClient";
import { ReactNode } from "react";

interface Props {
  children: ReactNode;
}

export default function ReactQueryProvider({ children }: Props) {
  return (
    <QueryClientProvider client={queryClient}>
      {children}
    </QueryClientProvider>
  );
}