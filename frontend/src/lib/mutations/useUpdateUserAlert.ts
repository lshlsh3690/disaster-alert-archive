"use client";

import { useMutation } from "@tanstack/react-query";
import { updateUserAlert, type UserAlertUpdateRequest, type UserAlertResponse } from "@/api/userAlertApi";

export function useUpdateUserAlert() {
  return useMutation<UserAlertResponse, Error, { id: number; payload: UserAlertUpdateRequest }>({
    mutationFn: ({ id, payload }) => updateUserAlert(id, payload),
  });
}


