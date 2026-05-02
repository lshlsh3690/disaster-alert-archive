import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createCommunityPost, fetchCommunityPosts } from "@/api/communityApi";

export function useCommunityPosts(category: "NOTICE" | "FREE", params?: { page?: number; size?: number }) {
  const key = ["community", category, params];
  return useQuery({ queryKey: key, queryFn: () => fetchCommunityPosts(category, params) });
}

export function useCreateCommunityPost(category: "NOTICE" | "FREE") {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: { title: string; content: string }) => createCommunityPost({ category, ...body }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["community", category] });
    },
  });
}


