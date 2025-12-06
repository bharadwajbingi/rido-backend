# ==============================================================================
# Run All Smoke Tests - PowerShell Version
# Executes all smoke tests in sequence and reports results
# ==============================================================================

param(
    [string]$AuthUrl = "http://localhost:8081",
    [string]$AdminUrl = "http://localhost:9091"
)

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "Running All Smoke Tests (PowerShell)" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$TotalTests = 0
$PassedTests = 0
$FailedTests = 0

# ==============================================================================
# WAIT FOR SERVICE READINESS
# ==============================================================================
function Wait-ForReadiness {
    Write-Host "Waiting for Auth service to be ready..."
    $maxAttempts = 30
    $attempt = 0
    
    while ($attempt -lt $maxAttempts) {
        try {
            $response = Invoke-RestMethod -Uri "$AuthUrl/actuator/health" -Method GET -TimeoutSec 5 -ErrorAction SilentlyContinue
            if ($response.status -eq "UP") {
                Write-Host "✅ Auth service is ready!" -ForegroundColor Green
                return $true
            }
        } catch {
            # Ignore errors, keep waiting
        }
        
        $attempt++
        Write-Host "  Waiting for readiness... ($attempt/$maxAttempts)"
        Start-Sleep -Seconds 2
    }
    
    Write-Host "❌ FAIL: Auth service not ready after $maxAttempts attempts" -ForegroundColor Red
    return $false
}

if (-not (Wait-ForReadiness)) {
    exit 1
}

Write-Host ""

# ==============================================================================
# TEST FUNCTIONS
# ==============================================================================
function Run-Test {
    param(
        [string]$TestName,
        [string]$TestFile
    )
    
    $script:TotalTests++
    
    Write-Host "----------------------------------------"
    Write-Host "Running: $TestName"
    Write-Host "----------------------------------------"
    
    # Run bash script via WSL if available, otherwise skip
    $bashPath = "$ScriptDir\$TestFile"
    
    try {
        # Try to run via WSL
        $wslPath = $bashPath -replace '\\', '/' -replace 'C:', '/mnt/c'
        $result = wsl bash $wslPath 2>&1
        $exitCode = $LASTEXITCODE
        
        if ($exitCode -eq 0) {
            Write-Host "✅ PASSED: $TestName" -ForegroundColor Green
            $script:PassedTests++
        } else {
            Write-Host "❌ FAILED: $TestName" -ForegroundColor Red
            $script:FailedTests++
        }
        
        Write-Host $result
    } catch {
        Write-Host "⚠️ Could not run test (WSL not available): $TestName" -ForegroundColor Yellow
        $script:FailedTests++
    }
    
    Write-Host ""
}

# ==============================================================================
# RUN NATIVE POWERSHELL TESTS
# ==============================================================================
function Run-StandaloneTests {
    $script:TotalTests++
    
    Write-Host "----------------------------------------"
    Write-Host "Running: Standalone Auth Tests (Native PowerShell)"
    Write-Host "----------------------------------------"
    
    $timestamp = Get-Date -Format "yyyyMMddHHmmss"
    $username = "ps_test_$timestamp"
    $password = "SecurePass123!"
    $passed = 0
    $failed = 0
    
    # Test 1: JWKS Endpoint
    Write-Host "Test 1: JWKS Endpoint"
    try {
        $jwks = Invoke-RestMethod -Uri "$AuthUrl/auth/keys/jwks.json" -Method GET -TimeoutSec 10
        if ($jwks.keys) {
            Write-Host "  ✅ PASS: JWKS endpoint accessible" -ForegroundColor Green
            $passed++
        } else {
            Write-Host "  ❌ FAIL: JWKS endpoint returned unexpected data" -ForegroundColor Red
            $failed++
        }
    } catch {
        Write-Host "  ❌ FAIL: JWKS endpoint error: $_" -ForegroundColor Red
        $failed++
    }
    
    # Test 2: User Registration
    Write-Host "Test 2: User Registration"
    try {
        $regBody = @{ username = $username; password = $password } | ConvertTo-Json
        $regResponse = Invoke-WebRequest -Uri "$AuthUrl/auth/register" `
            -Method POST `
            -Body $regBody `
            -ContentType "application/json" `
            -UseBasicParsing `
            -TimeoutSec 10

        if ($regResponse.StatusCode -eq 200) {
            Write-Host "  ✅ PASS: User registered successfully" -ForegroundColor Green
            $passed++
        } else {
            Write-Host "  ❌ FAIL: Registration returned $($regResponse.StatusCode)" -ForegroundColor Red
            $failed++
        }
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        Write-Host "  ❌ FAIL: Registration failed (HTTP $statusCode)" -ForegroundColor Red
        $failed++
    }
    
    # Test 3: Login
    Write-Host "Test 3: Login with Valid Credentials"
    $accessToken = $null
    $refreshToken = $null
    try {
        $loginBody = @{ username = $username; password = $password } | ConvertTo-Json
        $loginResponse = Invoke-RestMethod -Uri "$AuthUrl/auth/login" `
            -Method POST `
            -Body $loginBody `
            -ContentType "application/json" `
            -Headers @{ "User-Agent" = "PowerShellTest/1.0"; "X-Device-Id" = "ps-device-001" } `
            -TimeoutSec 10
        
        if ($loginResponse.accessToken) {
            $accessToken = $loginResponse.accessToken
            $refreshToken = $loginResponse.refreshToken
            Write-Host "  ✅ PASS: Login successful" -ForegroundColor Green
            $passed++
        } else {
            Write-Host "  ❌ FAIL: Login response missing accessToken" -ForegroundColor Red
            $failed++
        }
    } catch {
        Write-Host "  ❌ FAIL: Login error: $_" -ForegroundColor Red
        $failed++
    }
    
    # Test 4: Invalid Password
    Write-Host "Test 4: Login with Invalid Password"
    try {
        $invalidBody = @{ username = $username; password = "WrongPass123!" } | ConvertTo-Json
        $invalidResponse = Invoke-WebRequest -Uri "$AuthUrl/auth/login" `
            -Method POST `
            -Body $invalidBody `
            -ContentType "application/json" `
            -UseBasicParsing `
            -TimeoutSec 10 `
            -ErrorAction SilentlyContinue
        
        Write-Host "  ❌ FAIL: Should have returned 401" -ForegroundColor Red
        $failed++
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        if ($statusCode -eq 401) {
            Write-Host "  ✅ PASS: Invalid password correctly rejected (401)" -ForegroundColor Green
            $passed++
        } else {
            Write-Host "  ❌ FAIL: Expected 401, got $statusCode" -ForegroundColor Red
            $failed++
        }
    }
    
    # Test 5: Token Refresh
    Write-Host "Test 5: Token Refresh"
    $newRefreshToken = $null
    if ($refreshToken) {
        try {
            $refreshBody = @{ refreshToken = $refreshToken } | ConvertTo-Json
            $refreshResponse = Invoke-RestMethod -Uri "$AuthUrl/auth/refresh" `
                -Method POST `
                -Body $refreshBody `
                -ContentType "application/json" `
                -Headers @{ "User-Agent" = "PowerShellTest/1.0"; "X-Device-Id" = "ps-device-001" } `
                -TimeoutSec 10
            
            if ($refreshResponse.accessToken) {
                $newRefreshToken = $refreshResponse.refreshToken
                Write-Host "  ✅ PASS: Token refresh successful" -ForegroundColor Green
                $passed++
            } else {
                Write-Host "  ❌ FAIL: Refresh response missing accessToken" -ForegroundColor Red
                $failed++
            }
        } catch {
            Write-Host "  ❌ FAIL: Token refresh error: $_" -ForegroundColor Red
            $failed++
        }
    } else {
        Write-Host "  ⚠️ SKIP: No refresh token available" -ForegroundColor Yellow
    }
    
    # Test 6: Logout
    Write-Host "Test 6: Logout"
    if ($newRefreshToken) {
        try {
            $logoutBody = @{ refreshToken = $newRefreshToken } | ConvertTo-Json
            $logoutResponse = Invoke-WebRequest -Uri "$AuthUrl/auth/logout" `
                -Method POST `
                -Body $logoutBody `
                -ContentType "application/json" `
                -UseBasicParsing `
                -TimeoutSec 10
            
            if ($logoutResponse.StatusCode -eq 200) {
                Write-Host "  ✅ PASS: Logout successful" -ForegroundColor Green
                $passed++
            } else {
                Write-Host "  ❌ FAIL: Logout returned $($logoutResponse.StatusCode)" -ForegroundColor Red
                $failed++
            }
        } catch {
            Write-Host "  ❌ FAIL: Logout error: $_" -ForegroundColor Red
            $failed++
        }
    } else {
        Write-Host "  ⚠️ SKIP: No refresh token available" -ForegroundColor Yellow
    }
    
    # Summary for this test block
    if ($failed -eq 0) {
        Write-Host "✅ PASSED: Standalone Auth Tests" -ForegroundColor Green
        $script:PassedTests++
    } else {
        Write-Host "❌ FAILED: Standalone Auth Tests ($failed failures)" -ForegroundColor Red
        $script:FailedTests++
    }
    
    Write-Host ""
}

# ==============================================================================
# RUN ALL TESTS
# ==============================================================================
Run-StandaloneTests

# ==============================================================================
# SUMMARY
# ==============================================================================
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "Smoke Test Summary" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "Total tests:  $TotalTests"
Write-Host "Passed:       $PassedTests" -ForegroundColor Green
Write-Host "Failed:       $FailedTests" -ForegroundColor Red
Write-Host ""

if ($FailedTests -eq 0) {
    Write-Host "✅ ALL SMOKE TESTS PASSED!" -ForegroundColor Green
    exit 0
} else {
    Write-Host "❌ SOME TESTS FAILED" -ForegroundColor Red
    exit 1
}
