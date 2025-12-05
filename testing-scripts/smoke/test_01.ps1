$GATEWAY_URL = "http://localhost:8080"
Write-Host "=== Session Limit Test (PowerShell) ==="

# Register
$User = "testps$(Get-Date -Format 'yyyyMMddHHmmss')"
Write-Host "Registering user: $User"
$Body = @{
    username = $User
    password = "Pass123!"
} | ConvertTo-Json

try {
    $Reg = Invoke-RestMethod -Uri "$GATEWAY_URL/auth/register" -Method Post -Body $Body -ContentType "application/json"
    Write-Host "Registered"
} catch {
    Write-Host "Registration failed: $_"
    exit 1
}

# Login 6 times
Write-Host "Creating 6 sessions..."
$Token = ""
for ($i = 1; $i -le 6; $i++) {
    $Body = @{
        username = $User
        password = "Pass123!"
        deviceId = "dev$i"
    } | ConvertTo-Json

    try {
        $Resp = Invoke-RestMethod -Uri "$GATEWAY_URL/auth/login" -Method Post -Body $Body -ContentType "application/json"
        if ($i -eq 6) {
            $Token = $Resp.accessToken
        }
        Write-Host "Session $i created"
    } catch {
        Write-Host "Login failed: $_"
        exit 1
    }
}

# Check sessions
Write-Host "Checking active sessions..."
$Headers = @{
    Authorization = "Bearer $Token"
}

try {
    $Sessions = Invoke-RestMethod -Uri "$GATEWAY_URL/auth/sessions" -Method Get -Headers $Headers
    $Count = $Sessions.Count
    Write-Host "Active sessions: $Count"

    if ($Count -eq 5) {
        Write-Host "✅ PASS: Exactly 5 sessions active"
    } else {
        Write-Host "❌ FAIL: Expected 5 sessions, found $Count"
        Write-Host "Sessions: $($Sessions | ConvertTo-Json)"
        exit 1
    }
} catch {
    Write-Host "Get sessions failed: $_"
    exit 1
}
