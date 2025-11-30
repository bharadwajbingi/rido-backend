import http from "k6/http";
import { check, sleep } from "k6";

export let options = {
  vus: 50,
  duration: "20s",
};

export default function () {
  // STEP 1: Login to get tokens
  let loginRes = http.post(
    "http://localhost:8081/auth/login",
    JSON.stringify({ username: "t1003", password: "pass123" }),
    { headers: { "Content-Type": "application/json", "X-Device-Id": "k6dev" } }
  );

  if (loginRes.status !== 200) return;

  let refreshToken = loginRes.json("refreshToken");

  // STEP 2: Refresh
  let refreshRes = http.post(
    "http://localhost:8081/auth/refresh",
    JSON.stringify({ refreshToken }),
    { headers: { "Content-Type": "application/json", "X-Device-Id": "k6dev" } }
  );

  check(refreshRes, {
    "refresh worked or replay handled": (r) =>
      r.status === 200 || r.status === 401,
  });

  sleep(1);
}
