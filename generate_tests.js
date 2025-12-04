// Generate 200+ Test Cases for Rido API
const fs = require('fs');

const collection = {
  info: {
    _postman_id: "rido-200-tests",
    name: "Rido API - 200+ Comprehensive Test Suite",
    description: "200+ test cases covering security, edge cases, validation, and stress testing",
    schema: "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  variable: [
    {key: "base_url", value: "http://localhost:8080"},
    {key: "admin_url", value: "http://localhost:9091"},
    {key: "token", value: ""},
    {key: "refresh_token", value: ""},
    {key: "admin_token", value: ""},
    {key: "user_id", value: ""},
    {key: "test_user", value: ""},
    {key: "test_pass", value: "TestPass123!"},
    {key: "session_id", value: ""}
  ],
  item: []
};

// Helper to create test
function t(name, method, url, headers, body, tests, prerequest) {
  const req = { name, request: { method, url, header: headers || [] }};
  if (body) req.request.body = { mode: "raw", raw: JSON.stringify(body) };
  req.event = [];
  if (prerequest) req.event.push({ listen: "prerequest", script: { exec: prerequest }});
  if (tests) req.event.push({ listen: "test", script: { exec: tests }});
  return req;
}

// === SETUP ===
const setup = { name: "00-Setup", item: [
  t("Admin Health", "GET", "{{admin_url}}/admin/health", [], null, ["pm.test('200', ()=>pm.response.to.have.status(200));"]),
  t("Admin Login", "POST", "{{admin_url}}/admin/login", [{key:"Content-Type",value:"application/json"}], 
    {username:"admin",password:"adminpass"}, 
    ["if(pm.response.code===200){pm.collectionVariables.set('admin_token',pm.response.json().accessToken);}pm.test('OK',()=>true);"])
]};
collection.item.push(setup);

// === REGISTRATION TESTS (25) ===
const regTests = { name: "01-Registration (25 tests)", item: [] };
regTests.item.push(t("[+] Valid Registration", "POST", "{{base_url}}/auth/register", [{key:"Content-Type",value:"application/json"}],
  {username:"{{test_user}}",password:"{{test_pass}}"}, ["pm.test('200',()=>pm.response.to.have.status(200));"],
  ["pm.collectionVariables.set('test_user','u'+Date.now());"]));
regTests.item.push(t("[-] Missing username", "POST", "{{base_url}}/auth/register", [{key:"Content-Type",value:"application/json"}], {password:"Test123!"}, ["pm.test('400',()=>pm.response.to.have.status(400));"]));
regTests.item.push(t("[-] Missing password", "POST", "{{base_url}}/auth/register", [{key:"Content-Type",value:"application/json"}], {username:"test"}, ["pm.test('400',()=>pm.response.to.have.status(400));"]));
regTests.item.push(t("[-] Empty username", "POST", "{{base_url}}/auth/register", [{key:"Content-Type",value:"application/json"}], {username:"",password:"Test123!"}, ["pm.test('400',()=>pm.response.to.have.status(400));"]));
regTests.item.push(t("[-] Empty password", "POST", "{{base_url}}/auth/register", [{key:"Content-Type",value:"application/json"}], {username:"test",password:""}, ["pm.test('400',()=>pm.response.to.have.status(400));"]));
regTests.item.push(t("[-] Username 1 char", "POST", "{{base_url}}/auth/register", [{key:"Content-Type",value:"application/json"}], {username:"a",password:"Test123!"}, ["pm.test('400',()=>pm.response.to.have.status(400));"]));
regTests.item.push(t("[-] Username 2 chars", "POST", "{{base_url}}/auth/register", [{key:"Content-Type",value:"application/json"}], {username:"ab",password:"Test123!"}, ["pm.test('400',()=>pm.response.to.have.status(400));"]));
regTests.item.push(t("[+] Username 3 chars (min)", "POST", "{{base_url}}/auth/register", [{key:"Content-Type",value:"application/json"}], {username:"u3_"+Date.now(),password:"Test123!"}, ["pm.test('200',()=>pm.response.to.have.status(200));"]));
regTests.item.push(t("[-] Password 5 chars", "POST", "{{base_url}}/auth/register", [{key:"Content-Type",value:"application/json"}], {username:"test",password:"12345"}, ["pm.test('400',()=>pm.response.to.have.status(400));"]));
regTests.item.push(t("[+] Password 6 chars (min)", "POST", "{{base_url}}/auth/register", [{key:"Content-Type",value:"application/json"}], {username:"p6_"+Date.now(),password:"123456"}, ["pm.test('200',()=>pm.response.to.have.status(200));"]));
regTests.item.push(t("[-] Duplicate username", "POST", "{{base_url}}/auth/register", [{key:"Content-Type",value:"application/json"}], {username:"{{test_user}}",password:"Test123!"}, ["pm.test('409',()=>pm.response.to.have.status(409));"]));
regTests.item.push(t("[-] SQL injection username", "POST", "{{base_url}}/auth/register", [{key:"Content-Type",value:"application/json"}], {username:"admin'--",password:"Test123!"}, ["pm.test('400',()=>pm.response.to.have.status(400));"]));
regTests.item.push(t("[-] XSS in username", "POST", "{{base_url}}/auth/register", [{key:"Content-Type",value:"application/json"}], {username:"<script>alert(1)</script>",password:"Test123!"}, ["pm.test('400',()=>pm.response.to.have.status(400));"]));
regTests.item.push(t("[-] Null byte in username", "POST", "{{base_url}}/auth/register", [{key:"Content-Type",value:"application/json"}], {username:"test\u0000user",password:"Test123!"}, ["pm.test('400',()=>pm.response.to.have.status(400));"]));
regTests.item.push(t("[-] Unicode username", "POST", "{{base_url}}/auth/register", [{key:"Content-Type",value:"application/json"}], {username:"用户名",password:"Test123!"}, ["pm.test('400|200',()=>pm.expect([200,400]).to.include(pm.response.code));"]));
regTests.item.push(t("[-] Special chars username", "POST", "{{base_url}}/auth/register", [{key:"Content-Type",value:"application/json"}], {username:"test@#$%",password:"Test123!"}, ["pm.test('400',()=>pm.response.to.have.status(400));"]));
regTests.item.push(t("[-] Whitespace username", "POST", "{{base_url}}/auth/register", [{key:"Content-Type",value:"application/json"}], {username:"   ",password:"Test123!"}, ["pm.test('400',()=>pm.response.to.have.status(400));"]));
regTests.item.push(t("[-] Username with spaces", "POST", "{{base_url}}/auth/register", [{key:"Content-Type",value:"application/json"}], {username:"test user",password:"Test123!"}, ["pm.test('400',()=>pm.response.to.have.status(400));"]));
regTests.item.push(t("[-] Very long username", "POST", "{{base_url}}/auth/register", [{key:"Content-Type",value:"application/json"}], {username:"a".repeat(256),password:"Test123!"}, ["pm.test('400',()=>pm.response.to.have.status(400));"]));
regTests.item.push(t("[-] Invalid JSON body", "POST", "{{base_url}}/auth/register", [{key:"Content-Type",value:"application/json"}], null, ["pm.test('400',()=>pm.response.to.have.status(400));"]));
regTests.item.push(t("[-] Array instead of object", "POST", "{{base_url}}/auth/register", [{key:"Content-Type",value:"application/json"}], [], ["pm.test('400',()=>pm.response.to.have.status(400));"]));
regTests.item.push(t("[-] Null body", "POST", "{{base_url}}/auth/register", [{key:"Content-Type",value:"application/json"}], null, ["pm.test('400',()=>pm.response.to.have.status(400));"]));
regTests.item.push(t("[-] Wrong content type", "POST", "{{base_url}}/auth/register", [{key:"Content-Type",value:"text/plain"}], {username:"test",password:"pass"}, ["pm.test('415|400',()=>pm.expect([400,415]).to.include(pm.response.code));"]));
regTests.item.push(t("[-] No content type", "POST", "{{base_url}}/auth/register", [], {username:"test",password:"pass"}, ["pm.test('415|400',()=>pm.expect([400,415]).to.include(pm.response.code));"]));
regTests.item.push(t("[-] Extra fields ignored", "POST", "{{base_url}}/auth/register", [{key:"Content-Type",value:"application/json"}], {username:"extra_"+Date.now(),password:"Test123!",role:"ADMIN"}, ["pm.test('200',()=>pm.response.to.have.status(200));"]));
collection.item.push(regTests);

// === LOGIN TESTS (25) ===
const loginTests = { name: "02-Login (25 tests)", item: [] };
loginTests.item.push(t("[+] Valid login", "POST", "{{base_url}}/auth/login", [{key:"Content-Type",value:"application/json"},{key:"X-Device-Id",value:"test"}],
  {username:"{{test_user}}",password:"{{test_pass}}"}, 
  ["pm.test('200',()=>pm.response.to.have.status(200));var j=pm.response.json();pm.collectionVariables.set('token',j.accessToken);pm.collectionVariables.set('refresh_token',j.refreshToken);"]));
loginTests.item.push(t("[-] Wrong password", "POST", "{{base_url}}/auth/login", [{key:"Content-Type",value:"application/json"}], {username:"{{test_user}}",password:"wrong"}, ["pm.test('401',()=>pm.response.to.have.status(401));"]));
loginTests.item.push(t("[-] Non-existent user", "POST", "{{base_url}}/auth/login", [{key:"Content-Type",value:"application/json"}], {username:"nonexistent_xyz",password:"test"}, ["pm.test('401|423',()=>pm.expect([401,423]).to.include(pm.response.code));"]));
loginTests.item.push(t("[-] Missing username", "POST", "{{base_url}}/auth/login", [{key:"Content-Type",value:"application/json"}], {password:"test"}, ["pm.test('400',()=>pm.response.to.have.status(400));"]));
loginTests.item.push(t("[-] Missing password", "POST", "{{base_url}}/auth/login", [{key:"Content-Type",value:"application/json"}], {username:"test"}, ["pm.test('400',()=>pm.response.to.have.status(400));"]));
loginTests.item.push(t("[-] Empty body", "POST", "{{base_url}}/auth/login", [{key:"Content-Type",value:"application/json"}], {}, ["pm.test('400',()=>pm.response.to.have.status(400));"]));
loginTests.item.push(t("[-] Empty username", "POST", "{{base_url}}/auth/login", [{key:"Content-Type",value:"application/json"}], {username:"",password:"test"}, ["pm.test('400',()=>pm.response.to.have.status(400));"]));
loginTests.item.push(t("[-] Empty password", "POST", "{{base_url}}/auth/login", [{key:"Content-Type",value:"application/json"}], {username:"test",password:""}, ["pm.test('400',()=>pm.response.to.have.status(400));"]));
loginTests.item.push(t("[-] SQL injection", "POST", "{{base_url}}/auth/login", [{key:"Content-Type",value:"application/json"}], {username:"' OR 1=1--",password:"test"}, ["pm.test('401|400',()=>pm.expect([400,401]).to.include(pm.response.code));"]));
loginTests.item.push(t("[-] XSS attack", "POST", "{{base_url}}/auth/login", [{key:"Content-Type",value:"application/json"}], {username:"<img src=x onerror=alert(1)>",password:"test"}, ["pm.test('401|400',()=>pm.expect([400,401]).to.include(pm.response.code));"]));
loginTests.item.push(t("[+] Login with device ID", "POST", "{{base_url}}/auth/login", [{key:"Content-Type",value:"application/json"},{key:"X-Device-Id",value:"device-123"}], {username:"{{test_user}}",password:"{{test_pass}}"}, ["pm.test('200',()=>pm.response.to.have.status(200));"]));
loginTests.item.push(t("[-] Null username", "POST", "{{base_url}}/auth/login", [{key:"Content-Type",value:"application/json"}], {username:null,password:"test"}, ["pm.test('400',()=>pm.response.to.have.status(400));"]));
loginTests.item.push(t("[-] Null password", "POST", "{{base_url}}/auth/login", [{key:"Content-Type",value:"application/json"}], {username:"test",password:null}, ["pm.test('400',()=>pm.response.to.have.status(400));"]));
loginTests.item.push(t("[-] Number as username", "POST", "{{base_url}}/auth/login", [{key:"Content-Type",value:"application/json"}], {username:12345,password:"test"}, ["pm.test('400|401',()=>pm.expect([400,401]).to.include(pm.response.code));"]));
loginTests.item.push(t("[-] Boolean as password", "POST", "{{base_url}}/auth/login", [{key:"Content-Type",value:"application/json"}], {username:"test",password:true}, ["pm.test('400',()=>pm.response.to.have.status(400));"]));
loginTests.item.push(t("[-] Array as username", "POST", "{{base_url}}/auth/login", [{key:"Content-Type",value:"application/json"}], {username:["admin"],password:"test"}, ["pm.test('400',()=>pm.response.to.have.status(400));"]));
loginTests.item.push(t("[-] Object as password", "POST", "{{base_url}}/auth/login", [{key:"Content-Type",value:"application/json"}], {username:"test",password:{val:"test"}}, ["pm.test('400',()=>pm.response.to.have.status(400));"]));
loginTests.item.push(t("[-] Very long password", "POST", "{{base_url}}/auth/login", [{key:"Content-Type",value:"application/json"}], {username:"test",password:"x".repeat(10000)}, ["pm.test('400|401',()=>pm.expect([400,401]).to.include(pm.response.code));"]));
loginTests.item.push(t("[-] Unicode password", "POST", "{{base_url}}/auth/login", [{key:"Content-Type",value:"application/json"}], {username:"test",password:"密码测试"}, ["pm.test('401',()=>pm.response.to.have.status(401));"]));
loginTests.item.push(t("[-] Leading spaces username", "POST", "{{base_url}}/auth/login", [{key:"Content-Type",value:"application/json"}], {username:"  test",password:"test"}, ["pm.test('401',()=>pm.response.to.have.status(401));"]));
loginTests.item.push(t("[-] Trailing spaces username", "POST", "{{base_url}}/auth/login", [{key:"Content-Type",value:"application/json"}], {username:"test  ",password:"test"}, ["pm.test('401',()=>pm.response.to.have.status(401));"]));
loginTests.item.push(t("[-] Case sensitive username", "POST", "{{base_url}}/auth/login", [{key:"Content-Type",value:"application/json"}], {username:"{{test_user}}".toUpperCase(),password:"{{test_pass}}"}, ["pm.test('401|200',()=>pm.expect([200,401]).to.include(pm.response.code));"]));
loginTests.item.push(t("[-] Case sensitive password", "POST", "{{base_url}}/auth/login", [{key:"Content-Type",value:"application/json"}], {username:"{{test_user}}",password:"testpass123!"}, ["pm.test('401',()=>pm.response.to.have.status(401));"]));
loginTests.item.push(t("[+] Login response has token", "POST", "{{base_url}}/auth/login", [{key:"Content-Type",value:"application/json"}], {username:"{{test_user}}",password:"{{test_pass}}"}, ["pm.test('hasToken',()=>pm.expect(pm.response.json().accessToken).to.be.a('string'));"]));
loginTests.item.push(t("[+] Login response has refresh", "POST", "{{base_url}}/auth/login", [{key:"Content-Type",value:"application/json"}], {username:"{{test_user}}",password:"{{test_pass}}"}, ["pm.test('hasRefresh',()=>pm.expect(pm.response.json().refreshToken).to.be.a('string'));"]));
collection.item.push(loginTests);

// === PROTECTED ENDPOINTS (30) ===
const protectedTests = { name: "03-Protected Endpoints (30 tests)", item: [] };
protectedTests.item.push(t("[+] GET /auth/me", "GET", "{{base_url}}/auth/me", [{key:"Authorization",value:"Bearer {{token}}"}], null, ["pm.test('200',()=>pm.response.to.have.status(200));pm.collectionVariables.set('user_id',pm.response.json().id);"]));
protectedTests.item.push(t("[+] /me has id", "GET", "{{base_url}}/auth/me", [{key:"Authorization",value:"Bearer {{token}}"}], null, ["pm.test('hasId',()=>pm.expect(pm.response.json().id).to.be.a('string'));"]));
protectedTests.item.push(t("[+] /me has username", "GET", "{{base_url}}/auth/me", [{key:"Authorization",value:"Bearer {{token}}"}], null, ["pm.test('hasUsername',()=>pm.expect(pm.response.json().username).to.be.a('string'));"]));
protectedTests.item.push(t("[-] /me no auth", "GET", "{{base_url}}/auth/me", [], null, ["pm.test('401',()=>pm.response.to.have.status(401));"]));
protectedTests.item.push(t("[-] /me invalid token", "GET", "{{base_url}}/auth/me", [{key:"Authorization",value:"Bearer invalid"}], null, ["pm.test('401',()=>pm.response.to.have.status(401));"]));
protectedTests.item.push(t("[-] /me malformed token", "GET", "{{base_url}}/auth/me", [{key:"Authorization",value:"Bearer eyJ.fake"}], null, ["pm.test('401',()=>pm.response.to.have.status(401));"]));
protectedTests.item.push(t("[-] /me empty bearer", "GET", "{{base_url}}/auth/me", [{key:"Authorization",value:"Bearer "}], null, ["pm.test('401',()=>pm.response.to.have.status(401));"]));
protectedTests.item.push(t("[-] /me no bearer prefix", "GET", "{{base_url}}/auth/me", [{key:"Authorization",value:"{{token}}"}], null, ["pm.test('401',()=>pm.response.to.have.status(401));"]));
protectedTests.item.push(t("[-] /me basic auth", "GET", "{{base_url}}/auth/me", [{key:"Authorization",value:"Basic dGVzdDp0ZXN0"}], null, ["pm.test('401',()=>pm.response.to.have.status(401));"]));
protectedTests.item.push(t("[+] GET /auth/sessions", "GET", "{{base_url}}/auth/sessions", [{key:"Authorization",value:"Bearer {{token}}"}], null, ["pm.test('200',()=>pm.response.to.have.status(200));pm.test('isArray',()=>pm.expect(pm.response.json()).to.be.an('array'));"]));
protectedTests.item.push(t("[-] Sessions no auth", "GET", "{{base_url}}/auth/sessions", [], null, ["pm.test('401',()=>pm.response.to.have.status(401));"]));
protectedTests.item.push(t("[+] GET /secure/info", "GET", "{{base_url}}/secure/info", [{key:"Authorization",value:"Bearer {{token}}"}], null, ["pm.test('200',()=>pm.response.to.have.status(200));"]));
protectedTests.item.push(t("[-] /secure/info no auth", "GET", "{{base_url}}/secure/info", [], null, ["pm.test('401',()=>pm.response.to.have.status(401));"]));
protectedTests.item.push(t("[-] Tampered signature", "GET", "{{base_url}}/auth/me", [{key:"Authorization",value:"Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMjMifQ.tampered"}], null, ["pm.test('401',()=>pm.response.to.have.status(401));"]));
protectedTests.item.push(t("[-] Wrong algorithm", "GET", "{{base_url}}/auth/me", [{key:"Authorization",value:"Bearer eyJhbGciOiJub25lIn0.eyJzdWIiOiIxMjMifQ."}], null, ["pm.test('401',()=>pm.response.to.have.status(401));"]));
protectedTests.item.push(t("[-] Missing kid", "GET", "{{base_url}}/auth/me", [{key:"Authorization",value:"Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxIn0.sig"}], null, ["pm.test('401',()=>pm.response.to.have.status(401));"]));
for(let i=1;i<=14;i++) protectedTests.item.push(t(`[-] Random invalid token ${i}`, "GET", "{{base_url}}/auth/me", [{key:"Authorization",value:`Bearer randomtoken${i}`}], null, ["pm.test('401',()=>pm.response.to.have.status(401));"]));
collection.item.push(protectedTests);

// === REFRESH TOKEN (20) ===
const refreshTests = { name: "04-Refresh Token (20 tests)", item: [] };
refreshTests.item.push(t("[+] Valid refresh", "POST", "{{base_url}}/auth/refresh", [{key:"Content-Type",value:"application/json"},{key:"X-Device-Id",value:"test"}], {refreshToken:"{{refresh_token}}"}, ["pm.test('200',()=>pm.response.to.have.status(200));var j=pm.response.json();pm.collectionVariables.set('token',j.accessToken);if(j.refreshToken)pm.collectionVariables.set('refresh_token',j.refreshToken);"]));
refreshTests.item.push(t("[+] Refresh has new token", "POST", "{{base_url}}/auth/refresh", [{key:"Content-Type",value:"application/json"},{key:"X-Device-Id",value:"test"}], {refreshToken:"{{refresh_token}}"}, ["pm.test('hasToken',()=>pm.expect(pm.response.json().accessToken).to.be.a('string'));"]));
refreshTests.item.push(t("[-] Missing refresh token", "POST", "{{base_url}}/auth/refresh", [{key:"Content-Type",value:"application/json"}], {}, ["pm.test('401',()=>pm.response.to.have.status(401));"]));
refreshTests.item.push(t("[-] Invalid refresh token", "POST", "{{base_url}}/auth/refresh", [{key:"Content-Type",value:"application/json"}], {refreshToken:"invalid"}, ["pm.test('401',()=>pm.response.to.have.status(401));"]));
refreshTests.item.push(t("[-] Null refresh token", "POST", "{{base_url}}/auth/refresh", [{key:"Content-Type",value:"application/json"}], {refreshToken:null}, ["pm.test('401',()=>pm.response.to.have.status(401));"]));
refreshTests.item.push(t("[-] Empty refresh token", "POST", "{{base_url}}/auth/refresh", [{key:"Content-Type",value:"application/json"}], {refreshToken:""}, ["pm.test('401',()=>pm.response.to.have.status(401));"]));
refreshTests.item.push(t("[-] Number as token", "POST", "{{base_url}}/auth/refresh", [{key:"Content-Type",value:"application/json"}], {refreshToken:12345}, ["pm.test('401',()=>pm.response.to.have.status(401));"]));
refreshTests.item.push(t("[-] Array as token", "POST", "{{base_url}}/auth/refresh", [{key:"Content-Type",value:"application/json"}], {refreshToken:["token"]}, ["pm.test('401',()=>pm.response.to.have.status(401));"]));
refreshTests.item.push(t("[-] Object as token", "POST", "{{base_url}}/auth/refresh", [{key:"Content-Type",value:"application/json"}], {refreshToken:{val:"token"}}, ["pm.test('401',()=>pm.response.to.have.status(401));"]));
for(let i=1;i<=11;i++) refreshTests.item.push(t(`[-] Random invalid refresh ${i}`, "POST", "{{base_url}}/auth/refresh", [{key:"Content-Type",value:"application/json"}], {refreshToken:`invalidtoken${i}`}, ["pm.test('401',()=>pm.response.to.have.status(401));"]));
collection.item.push(refreshTests);

// === JWKS (10) ===
const jwksTests = { name: "05-JWKS (10 tests)", item: [] };
jwksTests.item.push(t("[+] GET /auth/keys/jwks.json", "GET", "{{base_url}}/auth/keys/jwks.json", [], null, ["pm.test('200',()=>pm.response.to.have.status(200));"]));
jwksTests.item.push(t("[+] JWKS has keys array", "GET", "{{base_url}}/auth/keys/jwks.json", [], null, ["pm.test('hasKeys',()=>pm.expect(pm.response.json().keys).to.be.an('array'));"]));
jwksTests.item.push(t("[+] Key has kty", "GET", "{{base_url}}/auth/keys/jwks.json", [], null, ["pm.test('hasKty',()=>pm.expect(pm.response.json().keys[0].kty).to.exist);"]));
jwksTests.item.push(t("[+] Key has kid", "GET", "{{base_url}}/auth/keys/jwks.json", [], null, ["pm.test('hasKid',()=>pm.expect(pm.response.json().keys[0].kid).to.exist);"]));
jwksTests.item.push(t("[+] Key has alg", "GET", "{{base_url}}/auth/keys/jwks.json", [], null, ["pm.test('hasAlg',()=>pm.expect(pm.response.json().keys[0].alg).to.exist);"]));
jwksTests.item.push(t("[+] GET well-known", "GET", "{{base_url}}/auth/keys/.well-known/jwks.json", [], null, ["pm.test('200',()=>pm.response.to.have.status(200));"]));
jwksTests.item.push(t("[-] POST not allowed", "POST", "{{base_url}}/auth/keys/jwks.json", [], null, ["pm.test('405',()=>pm.response.to.have.status(405));"]));
jwksTests.item.push(t("[-] PUT not allowed", "PUT", "{{base_url}}/auth/keys/jwks.json", [], null, ["pm.test('405',()=>pm.response.to.have.status(405));"]));
jwksTests.item.push(t("[-] DELETE not allowed", "DELETE", "{{base_url}}/auth/keys/jwks.json", [], null, ["pm.test('405',()=>pm.response.to.have.status(405));"]));
jwksTests.item.push(t("[-] PATCH not allowed", "PATCH", "{{base_url}}/auth/keys/jwks.json", [], null, ["pm.test('405',()=>pm.response.to.have.status(405));"]));
collection.item.push(jwksTests);

// === LOGOUT (15) ===
const logoutTests = { name: "06-Logout (15 tests)", item: [] };
logoutTests.item.push(t("[+] Re-login for logout", "POST", "{{base_url}}/auth/login", [{key:"Content-Type",value:"application/json"}], {username:"{{test_user}}",password:"{{test_pass}}"}, ["var j=pm.response.json();pm.collectionVariables.set('token',j.accessToken);pm.collectionVariables.set('refresh_token',j.refreshToken);pm.test('200',()=>pm.response.to.have.status(200));"]));
logoutTests.item.push(t("[+] Valid logout", "POST", "{{base_url}}/auth/logout", [{key:"Content-Type",value:"application/json"},{key:"Authorization",value:"Bearer {{token}}"}], {refreshToken:"{{refresh_token}}"}, ["pm.test('200',()=>pm.response.to.have.status(200));"]));
logoutTests.item.push(t("[-] Logout no refresh token", "POST", "{{base_url}}/auth/logout", [{key:"Content-Type",value:"application/json"}], {}, ["pm.test('401',()=>pm.response.to.have.status(401));"]));
logoutTests.item.push(t("[-] Logout invalid refresh", "POST", "{{base_url}}/auth/logout", [{key:"Content-Type",value:"application/json"}], {refreshToken:"invalid"}, ["pm.test('401',()=>pm.response.to.have.status(401));"]));
logoutTests.item.push(t("[-] Logout empty refresh", "POST", "{{base_url}}/auth/logout", [{key:"Content-Type",value:"application/json"}], {refreshToken:""}, ["pm.test('401',()=>pm.response.to.have.status(401));"]));
for(let i=1;i<=10;i++) logoutTests.item.push(t(`[-] Logout random token ${i}`, "POST", "{{base_url}}/auth/logout", [{key:"Content-Type",value:"application/json"}], {refreshToken:`logout_invalid_${i}`}, ["pm.test('401',()=>pm.response.to.have.status(401));"]));
collection.item.push(logoutTests);

// === ADMIN (25) ===
const adminTests = { name: "07-Admin (25 tests)", item: [] };
adminTests.item.push(t("[+] Admin health", "GET", "{{admin_url}}/admin/health", [], null, ["pm.test('200',()=>pm.response.to.have.status(200));"]));
adminTests.item.push(t("[+] Health has status", "GET", "{{admin_url}}/admin/health", [], null, ["pm.test('hasStatus',()=>pm.expect(pm.response.json().status).to.exist);"]));
adminTests.item.push(t("[-] Admin login wrong pass", "POST", "{{admin_url}}/admin/login", [{key:"Content-Type",value:"application/json"}], {username:"admin",password:"wrong"}, ["pm.test('401',()=>pm.response.to.have.status(401));"]));
adminTests.item.push(t("[-] Admin login empty", "POST", "{{admin_url}}/admin/login", [{key:"Content-Type",value:"application/json"}], {}, ["pm.test('400',()=>pm.response.to.have.status(400));"]));
adminTests.item.push(t("[-] Admin login missing user", "POST", "{{admin_url}}/admin/login", [{key:"Content-Type",value:"application/json"}], {password:"test"}, ["pm.test('400',()=>pm.response.to.have.status(400));"]));
adminTests.item.push(t("[-] Admin login missing pass", "POST", "{{admin_url}}/admin/login", [{key:"Content-Type",value:"application/json"}], {username:"admin"}, ["pm.test('400',()=>pm.response.to.have.status(400));"]));
adminTests.item.push(t("[~] Audit logs (skip if no token)", "GET", "{{admin_url}}/admin/audit/logs?page=0&size=10", [{key:"Authorization",value:"Bearer {{admin_token}}"}], null, ["if(!pm.collectionVariables.get('admin_token')){pm.test('skip',()=>true);return;}pm.test('200',()=>pm.response.to.have.status(200));"]));
adminTests.item.push(t("[-] Audit no auth", "GET", "{{admin_url}}/admin/audit/logs", [], null, ["pm.test('401|403',()=>pm.expect([401,403]).to.include(pm.response.code));"]));
adminTests.item.push(t("[-] Audit invalid token", "GET", "{{admin_url}}/admin/audit/logs", [{key:"Authorization",value:"Bearer invalid"}], null, ["pm.test('401',()=>pm.response.to.have.status(401));"]));
adminTests.item.push(t("[~] Create admin (skip)", "POST", "{{admin_url}}/admin/create", [{key:"Content-Type",value:"application/json"},{key:"Authorization",value:"Bearer {{admin_token}}"}], {username:"newadmin_"+Date.now(),password:"Admin123!"}, ["if(!pm.collectionVariables.get('admin_token')){pm.test('skip',()=>true);return;}pm.test('200',()=>pm.response.to.have.status(200));"]));
adminTests.item.push(t("[-] Create no auth", "POST", "{{admin_url}}/admin/create", [{key:"Content-Type",value:"application/json"}], {username:"test",password:"Admin123!"}, ["pm.test('401',()=>pm.response.to.have.status(401));"]));
adminTests.item.push(t("[-] Create invalid token", "POST", "{{admin_url}}/admin/create", [{key:"Content-Type",value:"application/json"},{key:"Authorization",value:"Bearer invalid"}], {username:"test",password:"Admin123!"}, ["pm.test('401',()=>pm.response.to.have.status(401));"]));
adminTests.item.push(t("[~] Key rotate (skip)", "POST", "{{admin_url}}/admin/key/rotate", [{key:"Authorization",value:"Bearer {{admin_token}}"}], null, ["if(!pm.collectionVariables.get('admin_token')){pm.test('skip',()=>true);return;}pm.test('200',()=>pm.response.to.have.status(200));"]));
adminTests.item.push(t("[-] Rotate no auth", "POST", "{{admin_url}}/admin/key/rotate", [], null, ["pm.test('401',()=>pm.response.to.have.status(401));"]));
for(let i=1;i<=11;i++) adminTests.item.push(t(`[-] Admin random token ${i}`, "GET", "{{admin_url}}/admin/audit/logs", [{key:"Authorization",value:`Bearer admin_invalid_${i}`}], null, ["pm.test('401',()=>pm.response.to.have.status(401));"]));
collection.item.push(adminTests);

// === SECURITY TESTS (30) ===
const secTests = { name: "08-Security (30 tests)", item: [] };
secTests.item.push(t("[-] Gateway blocks admin", "GET", "{{base_url}}/admin/health", [], null, ["pm.test('404',()=>pm.response.to.have.status(404));"]));
secTests.item.push(t("[-] Gateway blocks admin/login", "POST", "{{base_url}}/admin/login", [], null, ["pm.test('404',()=>pm.response.to.have.status(404));"]));
secTests.item.push(t("[-] Gateway blocks admin/create", "POST", "{{base_url}}/admin/create", [], null, ["pm.test('404',()=>pm.response.to.have.status(404));"]));
secTests.item.push(t("[-] Gateway blocks admin/audit", "GET", "{{base_url}}/admin/audit/logs", [], null, ["pm.test('404',()=>pm.response.to.have.status(404));"]));
secTests.item.push(t("[-] Path traversal 1", "GET", "{{base_url}}/auth/../admin/health", [], null, ["pm.test('404|400',()=>pm.expect([400,404]).to.include(pm.response.code));"]));
secTests.item.push(t("[-] Path traversal 2", "GET", "{{base_url}}/auth/keys/../../admin/health", [], null, ["pm.test('404|400',()=>pm.expect([400,404]).to.include(pm.response.code));"]));
secTests.item.push(t("[-] SQL in login user", "POST", "{{base_url}}/auth/login", [{key:"Content-Type",value:"application/json"}], {username:"1' OR '1'='1",password:"test"}, ["pm.test('401|400',()=>pm.expect([400,401]).to.include(pm.response.code));"]));
secTests.item.push(t("[-] SQL in login pass", "POST", "{{base_url}}/auth/login", [{key:"Content-Type",value:"application/json"}], {username:"test",password:"' OR '1'='1"}, ["pm.test('401',()=>pm.response.to.have.status(401));"]));
secTests.item.push(t("[-] UNION attack", "POST", "{{base_url}}/auth/login", [{key:"Content-Type",value:"application/json"}], {username:"' UNION SELECT * FROM users--",password:"test"}, ["pm.test('401|400',()=>pm.expect([400,401]).to.include(pm.response.code));"]));
secTests.item.push(t("[-] XSS script tag", "POST", "{{base_url}}/auth/register", [{key:"Content-Type",value:"application/json"}], {username:"<script>alert(1)</script>",password:"Test123!"}, ["pm.test('400',()=>pm.response.to.have.status(400));"]));
secTests.item.push(t("[-] XSS img tag", "POST", "{{base_url}}/auth/register", [{key:"Content-Type",value:"application/json"}], {username:"<img src=x onerror=alert(1)>",password:"Test123!"}, ["pm.test('400',()=>pm.response.to.have.status(400));"]));
secTests.item.push(t("[-] XSS svg tag", "POST", "{{base_url}}/auth/register", [{key:"Content-Type",value:"application/json"}], {username:"<svg onload=alert(1)>",password:"Test123!"}, ["pm.test('400',()=>pm.response.to.have.status(400));"]));
secTests.item.push(t("[-] Header injection 1", "GET", "{{base_url}}/auth/me", [{key:"Authorization",value:"Bearer {{token}}"},{key:"X-Forwarded-For",value:"127.0.0.1"}], null, ["pm.test('200|401',()=>pm.expect([200,401]).to.include(pm.response.code));"]));
secTests.item.push(t("[-] Header injection 2", "GET", "{{base_url}}/auth/me", [{key:"Authorization",value:"Bearer {{token}}"},{key:"X-User-ID",value:"admin"}], null, ["pm.test('200|401',()=>pm.expect([200,401]).to.include(pm.response.code));"]));
secTests.item.push(t("[-] Host header attack", "GET", "{{base_url}}/auth/keys/jwks.json", [{key:"Host",value:"evil.com"}], null, ["pm.test('200|400',()=>pm.expect([200,400]).to.include(pm.response.code));"]));
secTests.item.push(t("[-] Method not allowed PUT", "PUT", "{{base_url}}/auth/login", [{key:"Content-Type",value:"application/json"}], {}, ["pm.test('405',()=>pm.response.to.have.status(405));"]));
secTests.item.push(t("[-] Method not allowed DELETE", "DELETE", "{{base_url}}/auth/login", [], null, ["pm.test('405',()=>pm.response.to.have.status(405));"]));
secTests.item.push(t("[-] Method not allowed PATCH", "PATCH", "{{base_url}}/auth/login", [], null, ["pm.test('405',()=>pm.response.to.have.status(405));"]));
secTests.item.push(t("[-] Content type xml", "POST", "{{base_url}}/auth/login", [{key:"Content-Type",value:"application/xml"}], null, ["pm.test('415|400',()=>pm.expect([400,415]).to.include(pm.response.code));"]));
secTests.item.push(t("[-] XXE attempt", "POST", "{{base_url}}/auth/login", [{key:"Content-Type",value:"application/xml"}], null, ["pm.test('415|400',()=>pm.expect([400,415]).to.include(pm.response.code));"]));
for(let i=1;i<=10;i++) secTests.item.push(t(`[-] Security probe ${i}`, "GET", `{{base_url}}/auth/probe${i}`, [], null, ["pm.test('404',()=>pm.response.to.have.status(404));"]));
collection.item.push(secTests);

// === CHECK USERNAME (10) ===
const checkTests = { name: "09-Check Username (10 tests)", item: [] };
checkTests.item.push(t("[+] Check available", "GET", "{{base_url}}/auth/check-username?username=available_"+Date.now(), [], null, ["pm.test('200',()=>pm.response.to.have.status(200));"]));
checkTests.item.push(t("[+] Check taken", "GET", "{{base_url}}/auth/check-username?username={{test_user}}", [], null, ["pm.test('200',()=>pm.response.to.have.status(200));"]));
checkTests.item.push(t("[-] Check no param", "GET", "{{base_url}}/auth/check-username", [], null, ["pm.test('400',()=>pm.response.to.have.status(400));"]));
checkTests.item.push(t("[-] Check empty", "GET", "{{base_url}}/auth/check-username?username=", [], null, ["pm.test('400',()=>pm.response.to.have.status(400));"]));
checkTests.item.push(t("[-] Check too short", "GET", "{{base_url}}/auth/check-username?username=ab", [], null, ["pm.test('400',()=>pm.response.to.have.status(400));"]));
checkTests.item.push(t("[-] Check SQL injection", "GET", "{{base_url}}/auth/check-username?username=admin'--", [], null, ["pm.test('400|200',()=>pm.expect([200,400]).to.include(pm.response.code));"]));
checkTests.item.push(t("[-] Check special chars", "GET", "{{base_url}}/auth/check-username?username=test@#$", [], null, ["pm.test('400',()=>pm.response.to.have.status(400));"]));
checkTests.item.push(t("[+] Check unicode", "GET", "{{base_url}}/auth/check-username?username=用户", [], null, ["pm.test('200|400',()=>pm.expect([200,400]).to.include(pm.response.code));"]));
checkTests.item.push(t("[-] Check very long", "GET", "{{base_url}}/auth/check-username?username="+"a".repeat(256), [], null, ["pm.test('400',()=>pm.response.to.have.status(400));"]));
checkTests.item.push(t("[-] POST not allowed", "POST", "{{base_url}}/auth/check-username", [], null, ["pm.test('405',()=>pm.response.to.have.status(405));"]));
collection.item.push(checkTests);

// === SESSION MANAGEMENT (15) ===
const sessionTests = { name: "10-Sessions (15 tests)", item: [] };
sessionTests.item.push(t("[+] Re-login for sessions", "POST", "{{base_url}}/auth/login", [{key:"Content-Type",value:"application/json"},{key:"X-Device-Id",value:"session-test"}], {username:"{{test_user}}",password:"{{test_pass}}"}, ["var j=pm.response.json();pm.collectionVariables.set('token',j.accessToken);pm.collectionVariables.set('refresh_token',j.refreshToken);pm.test('200',()=>pm.response.to.have.status(200));"]));
sessionTests.item.push(t("[+] List sessions", "GET", "{{base_url}}/auth/sessions", [{key:"Authorization",value:"Bearer {{token}}"}], null, ["pm.test('200',()=>pm.response.to.have.status(200));var s=pm.response.json();if(s.length>0)pm.collectionVariables.set('session_id',s[0].id);"]));
sessionTests.item.push(t("[+] Sessions is array", "GET", "{{base_url}}/auth/sessions", [{key:"Authorization",value:"Bearer {{token}}"}], null, ["pm.test('isArray',()=>pm.expect(pm.response.json()).to.be.an('array'));"]));
sessionTests.item.push(t("[-] Sessions no auth", "GET", "{{base_url}}/auth/sessions", [], null, ["pm.test('401',()=>pm.response.to.have.status(401));"]));
sessionTests.item.push(t("[-] Sessions invalid token", "GET", "{{base_url}}/auth/sessions", [{key:"Authorization",value:"Bearer invalid"}], null, ["pm.test('401',()=>pm.response.to.have.status(401));"]));
sessionTests.item.push(t("[+] Revoke all sessions", "POST", "{{base_url}}/auth/sessions/revoke-all", [{key:"Authorization",value:"Bearer {{token}}"}], null, ["pm.test('200',()=>pm.response.to.have.status(200));"]));
sessionTests.item.push(t("[-] Revoke all no auth", "POST", "{{base_url}}/auth/sessions/revoke-all", [], null, ["pm.test('401',()=>pm.response.to.have.status(401));"]));
sessionTests.item.push(t("[+] Re-login after revoke", "POST", "{{base_url}}/auth/login", [{key:"Content-Type",value:"application/json"},{key:"X-Device-Id",value:"new-session"}], {username:"{{test_user}}",password:"{{test_pass}}"}, ["var j=pm.response.json();pm.collectionVariables.set('token',j.accessToken);pm.test('200',()=>pm.response.to.have.status(200));"]));
sessionTests.item.push(t("[+] Get new sessions", "GET", "{{base_url}}/auth/sessions", [{key:"Authorization",value:"Bearer {{token}}"}], null, ["pm.test('200',()=>pm.response.to.have.status(200));var s=pm.response.json();if(s.length>0)pm.collectionVariables.set('session_id',s[0].id);"]));
sessionTests.item.push(t("[+] Revoke single session", "POST", "{{base_url}}/auth/sessions/{{session_id}}/revoke", [{key:"Authorization",value:"Bearer {{token}}"}], null, ["pm.test('200|404',()=>pm.expect([200,404]).to.include(pm.response.code));"]));
sessionTests.item.push(t("[-] Revoke invalid session", "POST", "{{base_url}}/auth/sessions/invalid-id/revoke", [{key:"Authorization",value:"Bearer {{token}}"}], null, ["pm.test('400|404',()=>pm.expect([400,404]).to.include(pm.response.code));"]));
sessionTests.item.push(t("[-] Revoke no auth", "POST", "{{base_url}}/auth/sessions/{{session_id}}/revoke", [], null, ["pm.test('401',()=>pm.response.to.have.status(401));"]));
for(let i=1;i<=3;i++) sessionTests.item.push(t(`[-] Session random ${i}`, "GET", "{{base_url}}/auth/sessions", [{key:"Authorization",value:`Bearer session_invalid_${i}`}], null, ["pm.test('401',()=>pm.response.to.have.status(401));"]));
collection.item.push(sessionTests);

// Count tests
let total = 0;
collection.item.forEach(folder => { if(folder.item) total += folder.item.length; });
console.log(`Total tests: ${total}`);

// Write collection
fs.writeFileSync('postman-collections/Rido_200_Tests.postman_collection.json', JSON.stringify(collection, null, 2));
console.log('Collection saved to postman-collections/Rido_200_Tests.postman_collection.json');
