#!/usr/bin/env bash
# One-click push to GitHub
# Run this from inside the HandsFreeControl folder

set -e

echo "Setting up git..."
git init
git config user.email "ashish001.singh1997@gmail.com"
git config user.name "Ashish Singh"
git branch -M main

echo "Staging all files..."
git add .

echo "Committing..."
git commit -m "Add HandsFree Control - complete Android gesture control app" 2>/dev/null || echo "Already committed"

echo "Pushing to GitHub..."
git remote remove origin 2>/dev/null || true
git remote add origin https://github.com/Ashish001Singh/hand-gesture-app.git

# Push using your token
git push -u origin main --force

echo ""
echo "✅ Successfully pushed to https://github.com/Ashish001Singh/hand-gesture-app"
