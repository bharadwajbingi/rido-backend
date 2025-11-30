import http from "k6/http";
import { check, sleep } from "k6";

export let options = {
  vus: 100, // ✔ 100 virtual users
  duration: "30s",
};

export default function () {
  const payload = JSON.stringify({
    username: "t1003",
    password: "pass123",
  });

  const headers = { "Content-Type": "application/json" };

  let res = http.post("http://localhost:8081/auth/login", payload, { headers });

  check(res, {
    "login success OR handled error": (r) =>
      r.status === 200 ||
      r.status === 401 ||
      r.status === 423 ||
      r.status === 429,
  });

  sleep(1);
}
