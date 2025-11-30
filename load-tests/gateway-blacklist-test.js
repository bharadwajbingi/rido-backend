import http from "k6/http";
import { check, sleep } from "k6";

export const options = {
  vus: 10,
  iterations: 10,
};

const AUTH = "http://localhost:8081";
const GW = "http://localhost:8080";

export default function () {
  // 1️⃣ Login
  const login = http.post(
    `${AUTH}/auth/login`,
    JSON.stringify({
      username: "t1003",
      password: "pass123",
    }),
    {
      headers: { "Content-Type": "application/json", "X-Device-Id": "k6dev" },
    }
  );

  const access = login.json("accessToken");
  const refresh = login.json("refreshToken");

  check(login, { "login ok": (r) => r.status === 200 });

  // 2️⃣ Logout → blacklist access
  const logout = http.post(
    `${AUTH}/auth/logout`,
    JSON.stringify({
      refreshToken: refresh,
    }),
    {
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${access}`,
      },
    }
  );

  check(logout, { "logout ok": (r) => r.status === 200 });

  sleep(0.5);

  // 3️⃣ Try Gateway with blacklisted token
  const gwRes = http.get(`${GW}/auth/sessions`, {
    headers: { Authorization: `Bearer ${access}` },
  });

  check(gwRes, {
    "gateway blocks blacklisted token": (r) => r.status === 401,
  });
}
