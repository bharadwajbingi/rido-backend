import http from "k6/http";
import { check, sleep } from "k6";

export const options = {
  vus: 20,
  duration: "20s",
};

const AUTH = "http://localhost:8081";
const GW = "http://localhost:8080";

export default function () {
  // 1️⃣ Login to get tokens
  const loginRes = http.post(
    `${AUTH}/auth/login`,
    JSON.stringify({
      username: "t1003",
      password: "pass123",
    }),
    {
      headers: { "Content-Type": "application/json", "X-Device-Id": "k6dev" },
    }
  );

  const access = loginRes.json("accessToken");

  check(loginRes, {
    "login ok": (r) => r.status === 200,
    "access exists": (r) => access !== undefined,
  });

  // 2️⃣ Call gateway protected endpoint
  const gwRes = http.get(`${GW}/auth/keys/jwks.json`, {
    headers: { Authorization: `Bearer ${access}` },
  });

  check(gwRes, {
    "gateway accepted JWT": (r) => r.status === 200,
  });

  sleep(1);
}
