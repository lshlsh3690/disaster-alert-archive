import { useMutation, useQuery, useQueryClient, useInfiniteQuery } from "@tanstack/react-query";
import { Comment, CommentCreateRequest, createComment, deleteComment, fetchComments, updateComment, fetchLatestComments } from "@/api/commentApi";

export function useComments(params: { source: "OFFICIAL" | "USER"; targetId: number; page?: number; size?: number }) {
  return useQuery({
    queryKey: ["comments", params],
    queryFn: () => fetchComments(params),
  });
}

export function useCreateComment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CommentCreateRequest) => createComment(req),
    onSuccess: (_data, variables) => {
      qc.invalidateQueries({ queryKey: ["comments", { source: variables.source, targetId: variables.targetId }] });
      qc.invalidateQueries({ queryKey: ["comments-infinite", { source: variables.source, targetId: variables.targetId }] });
    },
  });
}

export function useDeleteComment(source: "OFFICIAL" | "USER", targetId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => deleteComment(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["comments", { source, targetId }] });
      qc.invalidateQueries({ queryKey: ["comments-infinite", { source, targetId }] });
    },
  });
}

export function useInfiniteComments(params: { source: "OFFICIAL" | "USER"; targetId: number; size?: number }) {
  const size = params.size ?? 10;
  return useInfiniteQuery({
    queryKey: ["comments-infinite", { source: params.source, targetId: params.targetId, size }],
    queryFn: ({ pageParam }) => {
      const page = typeof pageParam === "number" ? pageParam : 0;
      return fetchComments({ source: params.source, targetId: params.targetId, page, size });
    },
    initialPageParam: 0,
    getNextPageParam: (lastPage) => {
      const next = lastPage.number + 1;
      return next < lastPage.totalPages ? next : undefined;
    },
  });
}

export function useUpdateComment(source: "OFFICIAL" | "USER", targetId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { id: number; content: string }) => updateComment(vars.id, vars.content),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["comments", { source, targetId }] });
      qc.invalidateQueries({ queryKey: ["comments-infinite", { source, targetId }] });
    },
  });
}

export function useLatestComments(size = 5) {
  return useQuery({
    queryKey: ["latest-comments", size],
    queryFn: () => fetchLatestComments(size),
    staleTime: 30_000,
  });
}


