import http from "k6/http";
import { check } from "k6";

export let options = {
  vus: 10,
  duration: "10s",
};

export default function () {
  const payload = JSON.stringify({
    username: "t1003",
    password: "wrong",
  });

  let res = http.post("http://localhost:8081/auth/login", payload, {
    headers: { "Content-Type": "application/json" },
  });

  check(res, {
    "login failed / locked": (r) =>
      r.status === 401 || r.status === 423 || r.status === 429,
  });
}
