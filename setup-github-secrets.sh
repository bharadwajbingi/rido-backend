#!/bin/bash

# GitHub Secrets Setup Helper
# This script helps you set up all required secrets for Auth service deployment

echo "╔═══════════════════════════════════════════════════════════╗"
echo "║  GitHub Secrets Setup for Auth Service Deployment        ║"
echo "╚═══════════════════════════════════════════════════════════╝"
echo ""
echo "📋 You need to add 12 secrets to your GitHub repository:"
echo ""
echo "Go to: https://github.com/YOUR_USERNAME/rido-backend/settings/secrets/actions"
echo ""
echo "─────────────────────────────────────────────────────────────"
echo "AWS & EC2 Configuration"
echo "─────────────────────────────────────────────────────────────"
echo ""
read -p "AWS_ACCESS_KEY_ID (from AWS IAM): " AWS_ACCESS_KEY_ID
read -p "AWS_SECRET_ACCESS_KEY (from AWS IAM): " AWS_SECRET_ACCESS_KEY
read -p "EC2_HOST (EC2 public IP, e.g., 13.232.45.67): " EC2_HOST
echo ""
echo "EC2_SSH_KEY: Paste the ENTIRE content of your EC2 SSH key file"
echo "(including -----BEGIN RSA PRIVATE KEY----- and -----END RSA PRIVATE KEY-----)"
echo ""

echo "─────────────────────────────────────────────────────────────"
echo "Tailscale Configuration (Infrastructure Endpoints)"
echo "─────────────────────────────────────────────────────────────"
echo ""
echo "First, get your laptop's Tailscale IP:"
echo "  Windows: Open PowerShell and run: tailscale ip -4"
echo ""
read -p "POSTGRES_HOST (your laptop Tailscale IP): " POSTGRES_HOST
REDIS_HOST="$POSTGRES_HOST"
VAULT_HOST="$POSTGRES_HOST"
echo "✓ Using same IP for Redis and Vault: $POSTGRES_HOST"
echo ""

echo "─────────────────────────────────────────────────────────────"
echo "Database Configuration"
echo "─────────────────────────────────────────────────────────────"
echo ""
read -p "DB_USERNAME (default: rh_user): " DB_USERNAME
DB_USERNAME=${DB_USERNAME:-rh_user}
read -sp "DB_PASSWORD (default: rh_pass): " DB_PASSWORD
DB_PASSWORD=${DB_PASSWORD:-rh_pass}
echo ""
echo ""

echo "─────────────────────────────────────────────────────────────"
echo "Vault Configuration"
echo "─────────────────────────────────────────────────────────────"
echo ""
read -p "VAULT_TOKEN (default: root): " VAULT_TOKEN
VAULT_TOKEN=${VAULT_TOKEN:-root}
echo ""

echo "─────────────────────────────────────────────────────────────"
echo "Auth Service Admin Configuration"
echo "─────────────────────────────────────────────────────────────"
echo ""
read -p "ADMIN_USERNAME (default: admin): " ADMIN_USERNAME
ADMIN_USERNAME=${ADMIN_USERNAME:-admin}
read -sp "ADMIN_PASSWORD: " ADMIN_PASSWORD
echo ""
echo ""

echo "═══════════════════════════════════════════════════════════"
echo "📝 SUMMARY - Add these secrets to GitHub:"
echo "═══════════════════════════════════════════════════════════"
echo ""
echo "Name: AWS_ACCESS_KEY_ID"
echo "Value: $AWS_ACCESS_KEY_ID"
echo ""
echo "Name: AWS_SECRET_ACCESS_KEY"
echo "Value: $AWS_SECRET_ACCESS_KEY"
echo ""
echo "Name: EC2_HOST"
echo "Value: $EC2_HOST"
echo ""
echo "Name: EC2_SSH_KEY"
echo "Value: [paste your entire SSH key file]"
echo ""
echo "Name: POSTGRES_HOST"
echo "Value: $POSTGRES_HOST"
echo ""
echo "Name: REDIS_HOST"
echo "Value: $REDIS_HOST"
echo ""
echo "Name: VAULT_HOST"
echo "Value: $VAULT_HOST"
echo ""
echo "Name: DB_USERNAME"
echo "Value: $DB_USERNAME"
echo ""
echo "Name: DB_PASSWORD"
echo "Value: $DB_PASSWORD"
echo ""
echo "Name: VAULT_TOKEN"
echo "Value: $VAULT_TOKEN"
echo ""
echo "Name: ADMIN_USERNAME"
echo "Value: $ADMIN_USERNAME"
echo ""
echo "Name: ADMIN_PASSWORD"
echo "Value: $ADMIN_PASSWORD"
echo ""
echo "═══════════════════════════════════════════════════════════"
echo ""
echo "✅ Copy these values and add them manually to GitHub:"
echo "   https://github.com/YOUR_USERNAME/rido-backend/settings/secrets/actions"
echo ""
echo "Or save to file and reference:"
read -p "Save to secrets.txt? (y/n): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    cat > secrets.txt << EOF
# GitHub Secrets for Auth Service Deployment
# DO NOT COMMIT THIS FILE!

AWS_ACCESS_KEY_ID=$AWS_ACCESS_KEY_ID
AWS_SECRET_ACCESS_KEY=$AWS_SECRET_ACCESS_KEY
EC2_HOST=$EC2_HOST
POSTGRES_HOST=$POSTGRES_HOST
REDIS_HOST=$REDIS_HOST
VAULT_HOST=$VAULT_HOST
DB_USERNAME=$DB_USERNAME
DB_PASSWORD=$DB_PASSWORD
VAULT_TOKEN=$VAULT_TOKEN
ADMIN_USERNAME=$ADMIN_USERNAME
ADMIN_PASSWORD=$ADMIN_PASSWORD

# EC2_SSH_KEY: Add manually from your SSH key file
EOF
    echo "✅ Saved to secrets.txt"
    echo "⚠️  Remember: Add secrets.txt to .gitignore!"
fi
