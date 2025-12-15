#!/bin/bash
# Fix AuditLogIT.java line 62 - add 3 null parameters to LoginRequest
sed -i '62s/new com\.rido\.auth\.dto\.LoginRequest("failaudit", "WrongPassword!")/new com.rido.auth.dto.LoginRequest("failaudit", "WrongPassword!", null, null, null)/' src/test/java/com/rido/auth/integration/AuditLogIT.java
echo "Fixed AuditLogIT.java line 62"
