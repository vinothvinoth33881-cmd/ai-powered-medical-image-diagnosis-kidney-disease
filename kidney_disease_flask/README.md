# NephroScan AI - Kidney Disease Diagnosis Platform

AI-powered medical image diagnosis system using Machine Learning (SVM & Decision Tree) to classify renal CT and ultrasound scan images into four clinical categories:
- **Cyst**
- **Normal**
- **Stone**
- **Tumor**

---

## 🚀 Vercel Deployment Guide

### Why the error happened:
When Vercel deploys a Python Flask project, if `vercel.json` is missing or configured with static routes, Vercel looks for static `index.html` files and returns:
`"This page doesn't exist. Maybe you mistyped the URL or the server doesn't exist."`

### Fix Applied:
1. Created `vercel.json` routing all requests (`/(.*)`) to `app.py` via `@vercel/python`.
2. Initialized Flask with explicit `template_folder` and `static_folder` relative to `BASE_DIR`.
3. Saved uploads into `/tmp` (the only writable directory in Vercel Serverless Functions) and encoded analyzed scans as Base64 for instant rendering.
4. Resolved trained models using relative project directory paths via `os.path.dirname(os.path.abspath(__file__))`.
5. Created clean `requirements.txt` with compatible serverless dependencies.
