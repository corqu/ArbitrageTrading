import axios from "axios";

let csrfRequest: Promise<void> | null = null;

const getCookie = (name: string) => {
  const cookies = document.cookie ? document.cookie.split("; ") : [];
  const prefix = `${name}=`;
  const match = cookies.find((cookie) => cookie.startsWith(prefix));
  return match ? decodeURIComponent(match.substring(prefix.length)) : null;
};

const isUnsafeMethod = (method?: string) => {
  if (!method) {
    return false;
  }

  const normalizedMethod = method.toUpperCase();
  return normalizedMethod === "POST" || normalizedMethod === "PUT" || normalizedMethod === "PATCH" || normalizedMethod === "DELETE";
};

export const ensureCsrfToken = async () => {
  if (!csrfRequest) {
    csrfRequest = axios.get("/api/auth/csrf").then(() => undefined).finally(() => {
      csrfRequest = null;
    });
  }

  await csrfRequest;
};

axios.defaults.withCredentials = true;
axios.defaults.xsrfCookieName = "XSRF-TOKEN";
axios.defaults.xsrfHeaderName = "X-XSRF-TOKEN";

axios.interceptors.request.use(async (config) => {
  config.withCredentials = true;

  if (config.url !== "/api/auth/csrf" && isUnsafeMethod(config.method)) {
    await ensureCsrfToken();
    const csrfToken = getCookie("XSRF-TOKEN");
    if (csrfToken) {
      config.headers.set("X-XSRF-TOKEN", csrfToken);
    }
  }

  return config;
});

export default axios;
