# Driver Drowsiness and Fatigue Detection in Real Time
### Using OpenCV Haar Cascades and Keras / TensorFlow Deep Learning

A production-ready computer vision and deep learning project that tracks driver face and eye states in real time using a standard webcam, calculates a continuous drowsiness score, and triggers an emergency audible alarm to prevent accidents caused by fatigue.

---

## 📁 Project Directory Structure

```text
driver_drowsiness_project/
│
├── requirements.txt            # Compatible Python package dependencies
├── generate_alarm.py           # Generates the synthetic emergency buzzer alarm.wav
├── model_training.py           # Complete CNN model training & export script
├── drowsiness_detection.py      # Real-time OpenCV webcam monitoring script
├── alarm.wav                   # Emergency sound alert file
│
├── models/
│   └── model.h5                # Trained CNN Keras model weights
│
├── haar_cascades/              # (Optional - OpenCV provides built-in fallback)
│   ├── haarcascade_frontalface_default.xml
│   └── haarcascade_eye.xml
│
└── dataset/                    # Training and Validation Eye Dataset
    ├── train/
    │   ├── closed/             # Cropped closed eye images (~24x24 px)
    │   └── open/               # Cropped open eye images (~24x24 px)
    └── val/
        ├── closed/
        └── open/
```

---

## ⚡ Quick Start Instructions

### 1. Set Up Python Virtual Environment
We recommend Python **3.9, 3.10, or 3.11**.

```bash
# Open terminal in project directory
cd driver_drowsiness_project

# Create a virtual environment
python -m venv venv

# Activate virtual environment
# Windows (Command Prompt):
venv\Scripts\activate
# Windows (PowerShell):
venv\Scripts\Activate.ps1
# macOS / Linux:
source venv/bin/activate
```

### 2. Install Required Dependencies
```bash
pip install --upgrade pip
pip install -r requirements.txt
```

### 3. Generate Alarm Sound
```bash
python generate_alarm.py
```
This produces `alarm.wav` directly in the project folder without needing external audio downloads.

### 4. Train the CNN Eye Classifier
```bash
python model_training.py
```
- If you have downloaded an eye dataset (such as MRL Eye or CEW), place it into `dataset/train` and `dataset/val`.
- If the dataset is not yet downloaded, the script automatically trains on benchmark geometric eye patterns so `models/model.h5` is created immediately for quick end-to-end testing.

### 5. Launch Real-Time Drowsiness Detection
```bash
python drowsiness_detection.py
```
- Position your webcam facing your face in normal lighting.
- Keep your eyes open: The box turns **GREEN** ("Open").
- Close your eyes continuously: The box turns **RED** ("Closed"), the **Fatigue Score** increases, and when it reaches `15` (~0.5 - 1.0 second), the **Audible Alarm** rings and a flashing red alert banner appears!
- Reopen your eyes: The alarm immediately stops and the fatigue score cools down to 0.
- Press **'q'** or **ESC** in the video window to quit.

---

## 📓 Running in VS Code or Jupyter Notebook

### VS Code:
1. Open the project folder in VS Code (`File -> Open Folder...`).
2. Press `Ctrl+Shift+P` (or `Cmd+Shift+P` on macOS) and select **Python: Select Interpreter**.
3. Choose the virtual environment created in step 1 (`./venv` or Python 3.10/3.11).
4. Open `drowsiness_detection.py` and click the **Run Python File** play button (top right).

### Jupyter Notebook:
To run inside a Jupyter notebook:
1. Install ipykernel:
   ```bash
   pip install ipykernel ipywidgets
   ```
2. Start Jupyter:
   ```bash
   jupyter notebook
   ```
3. In your notebook cell, you can import and run the detection loop or inspect model accuracy metrics and confusion matrices.

---

## 🧠 CNN Model Architecture Details

The eye classification model is a deep Convolutional Neural Network designed for low latency and high accuracy:
- **Input Layer**: `(24, 24, 1)` Grayscale image patch.
- **Conv2D Block 1**: 32 filters (3x3), Batch Normalization, ReLU, MaxPool2D(2x2).
- **Conv2D Block 2**: 64 filters (3x3), Batch Normalization, ReLU, MaxPool2D(2x2).
- **Conv2D Block 3**: 128 filters (3x3), Batch Normalization, ReLU, MaxPool2D(2x2).
- **Flatten Layer**: Flattens high-level feature maps to 1152-dimensional vector.
- **Dense Layer**: 128 units with ReLU activation.
- **Dropout Layer**: Rate 0.5 to prevent overfitting to specific facial structures.
- **Output Layer**: 2 units with Softmax activation `[P(Closed), P(Open)]`.

---

## 🛠️ Troubleshooting & FAQ

1. **"Webcam not detected / could not open video stream"**:
   - Check if another application (Teams, Zoom, browser) is locking the webcam.
   - If using a secondary webcam, change `cv2.VideoCapture(0)` to `cv2.VideoCapture(1)`.
2. **"Haar cascade XML not found"**:
   - The script automatically checks `cv2.data.haarcascades` which ships directly with `opencv-python`.
3. **"Audio error in Pygame"**:
   - Ensure your default sound output device is enabled.
4. **"High False Positives due to glasses"**:
   - Train on the MRL dataset subset containing subjects with glasses, or adjust `DROWSINESS_SCORE_THRESHOLD` in `drowsiness_detection.py` from 15 to 20.
