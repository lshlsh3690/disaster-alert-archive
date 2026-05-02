"use client";

import { useMutation } from "@tanstack/react-query";
import { deleteUserAlert } from "@/api/userAlertApi";

export function useDeleteUserAlert() {
  return useMutation<void, Error, number>({
    mutationFn: (id: number) => deleteUserAlert(id),
  });
}


