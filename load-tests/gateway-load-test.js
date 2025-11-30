import http from "k6/http";
import { check, sleep } from "k6";

export const options = {
  vus: 100,
  duration: "30s",
};

const AUTH = "http://localhost:8081";
const GW = "http://localhost:8080";

export default function () {
  // Login just once and reuse token
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

  // Gateway protected route
  const gw = http.get(`${GW}/auth/keys/jwks.json`, {
    headers: { Authorization: `Bearer ${access}` },
  });

  check(gw, {
    "gateway ok": (r) => r.status === 200,
  });

  sleep(1);
}
