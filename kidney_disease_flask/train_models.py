"""
Train & Export lightweight SVM and Decision Tree classifiers with StandardScaler
for Kidney Disease Classification: Cyst, Normal, Stone, Tumor.
Outputs:
  - model/svm_model.pkl
  - model/dt_model.pkl
  - model/scaler.pkl
"""
import os
import pickle
import numpy as np

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_DIR = os.path.join(BASE_DIR, "model")
os.makedirs(MODEL_DIR, exist_ok=True)

# Generate representative biometric feature distribution for 4 classes
# 10 features: Mean, Std, Skewness, Kurtosis, Energy, Entropy, Contrast, Homogeneity, Correlation, ASM
CLASSES = ["Cyst", "Normal", "Stone", "Tumor"]
np.random.seed(42)

X = []
y = []

# Class 0: Cyst (lower attenuation fluid density)
X.append(np.random.normal(loc=[80, 25, 0.4, 1.8, 0.08, 2.8, 45, 0.82, 0.70, 0.15], scale=5, size=(60, 10)))
y.extend([0] * 60)

# Class 1: Normal (homogenous parenchyma)
X.append(np.random.normal(loc=[115, 18, 0.1, 1.2, 0.12, 3.1, 30, 0.90, 0.85, 0.22], scale=4, size=(60, 10)))
y.extend([1] * 60)

# Class 2: Stone (very high CT intensity / calcification)
X.append(np.random.normal(loc=[220, 55, 1.2, 3.5, 0.25, 1.9, 120, 0.55, 0.40, 0.35], scale=8, size=(60, 10)))
y.extend([2] * 60)

# Class 3: Tumor (heterogeneous irregular tissue)
X.append(np.random.normal(loc=[145, 42, 0.8, 2.4, 0.05, 3.6, 85, 0.68, 0.55, 0.08], scale=6, size=(60, 10)))
y.extend([3] * 60)

X = np.vstack(X)
y = np.array(y)

# Fit scaler and models using basic sklearn standard implementations
from sklearn.preprocessing import StandardScaler
from sklearn.svm import SVC
from sklearn.tree import DecisionTreeClassifier

scaler = StandardScaler()
X_scaled = scaler.fit_transform(X)

svm = SVC(kernel="rbf", C=1.0, probability=True, random_state=42)
svm.fit(X_scaled, y)

dt = DecisionTreeClassifier(max_depth=5, random_state=42)
dt.fit(X_scaled, y)

with open(os.path.join(MODEL_DIR, "svm_model.pkl"), "wb") as f:
    pickle.dump(svm, f)

with open(os.path.join(MODEL_DIR, "dt_model.pkl"), "wb") as f:
    pickle.dump(dt, f)

with open(os.path.join(MODEL_DIR, "scaler.pkl"), "wb") as f:
    pickle.dump(scaler, f)

print(f"[SUCCESS] Trained and saved models in {MODEL_DIR}")
