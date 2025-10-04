"use client";

import { useMutation } from "@tanstack/react-query";
import { createUserAlert, type UserAlertCreateRequest, type UserAlertResponse } from "@/api/userAlertApi";

export function useCreateUserAlert() {
  return useMutation<UserAlertResponse, Error, UserAlertCreateRequest>({
    mutationFn: createUserAlert,
  });
}


