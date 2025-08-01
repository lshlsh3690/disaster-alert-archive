import instance from "./axios";

export const login = async (email: string, password: string) => {
  try {
    const response = await instance.post(
      "/api/v1/auth/login",
      { email, password },
      { withCredentials: true }
    );
    console.log("로그인 요청:", { email, password });
    return response.data;
  } catch (error) {
    console.error("로그인 실패:", error);
    throw error;
  }
};

export const logout = async () => {
  try {
    await instance.post("/api/v1/auth/logout");
  } catch (error) {
    console.error("로그아웃 실패:", error);
    throw error;
  }
};
