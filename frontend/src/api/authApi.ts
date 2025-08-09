import instance from "@/api/axios";
import { ApiResponse } from "@/types/ApiResponse";
import { SignupFormData } from "@/types/signup";

const AUTH_API_BASE = "/api/v1/auth";

/**
 * Sends a login request to the server with the provided email and password.
 * Stores authentication cookies if login is successful.
 *
 * @param email - The user's email address.
 * @param password - The user's password.
 * @returns The response data from the login API.
 */
export const loginApi = async (email: string, password: string) => {
  const res = await instance.post(`${AUTH_API_BASE}/login`, { email, password }, { withCredentials: true });
  return res.data;
};

/**
 * Sends a logout request to the server to terminate the user's session.
 *
 * @returns The response data from the logout API.
 */
export const logoutApi = async () => {
  const res = await instance.post(`${AUTH_API_BASE}/logout`);
  return res.data;
};

/**
 * Sends a request to the server to send an email verification code to the specified email address.
 *
 * @param email - The email address to which the verification code will be sent.
 * @returns The response data from the email verification API.
 */
export const sendEmailVerificationCode = async (email: string) => {
  const res = await instance.post(`${AUTH_API_BASE}/email/verify`, {
    email: email,
  });
  return res.data;
};

/**
 * Verifies the email verification code sent to the user's email address.
 *
 * @param email - The email address to verify.
 * @param code - The verification code received by the user.
 * @returns The response data from the verification code API.
 */
export const verifyEmailCode = async (email: string, code: string) => {
  const res = await instance.post(`${AUTH_API_BASE}/email/verify/code`, {
    email: email,
    code: code,
  });
  return res.data;
};

/**
 * Sends a signup request to the server with the provided user data.
 *
 * @param data - The signup form data containing user registration information.
 * @returns The response data from the signup API.
 */
export const signupApi = async (data: SignupFormData): Promise<ApiResponse<null>> => {
  const res = await instance.post(`${AUTH_API_BASE}/signup`, data);
  return res.data;
};


