"""
================================================================================
Driver Drowsiness / Fatigue Detection in Real Time
================================================================================
Requirements Satisfied:
  1. Captures live webcam video using cv2.VideoCapture(0)
  2. Detects faces and eyes using OpenCV Haar Cascade Classifiers
  3. Preprocesses cropped eye regions and classifies state (Open vs Closed)
     using the trained Keras/TensorFlow CNN model
  4. Maintains a dynamic 'Score' counter:
       - Increments when eyes remain closed across consecutive frames
       - Decrements smoothly down to 0 when eyes are open
  5. Plays an audible alarm alert using pygame.mixer when score >= DROWSINESS_THRESHOLD
  6. Visual overlays:
       - Green/Red bounding boxes for face and eyes
       - Real-time status text ("Eyes: OPEN", "Eyes: CLOSED", "ALERT! DROWSINESS DETECTED")
       - HUD panel showing fatigue score, FPS, and status gauge
  7. Robust error handling for:
       - Missing webcam hardware / camera busy
       - Missing model file (gives prompt to run model_training.py)
       - Missing Haar cascade XML files (auto-resolves from cv2.data.haarcascades)
       - Frames with no detected face or eyes
================================================================================
"""

import os
import sys
import time
import cv2
import numpy as np

# Suppress noisy TensorFlow info logs
os.environ['TF_CPP_MIN_LOG_LEVEL'] = '2'

try:
    import tensorflow as tf
    from tensorflow.keras.models import load_model
except ImportError:
    print("[ERROR] TensorFlow / Keras not found. Install via: pip install tensorflow keras")
    sys.exit(1)

try:
    import pygame
except ImportError:
    print("[ERROR] Pygame not found. Install via: pip install pygame")
    sys.exit(1)

# ==============================================================================
# Configuration & Constants
# ==============================================================================
MODEL_PATH = os.path.join("models", "model.h5")
ALARM_SOUND_PATH = "alarm.wav"

# Threshold for drowsiness detection:
# At ~30 FPS, a score of 15 corresponds to ~0.5 seconds of continuous eye closure.
DROWSINESS_SCORE_THRESHOLD = 15
EYE_IMAGE_SIZE = (24, 24)   # Target resolution expected by CNN model (width, height)

# ==============================================================================
# 1. Initialize Audio Engine (pygame.mixer)
# ==============================================================================
def initialize_alarm():
    """
    Initializes pygame.mixer and loads the alarm sound file.
    If alarm.wav is missing, generates a synthetic wave alarm on the fly.
    """
    try:
        pygame.mixer.init()
    except Exception as e:
        print(f"[WARNING] Could not initialize pygame audio mixer: {e}")
        return None

    # Check if alarm sound file exists; if not, create one automatically
    if not os.path.exists(ALARM_SOUND_PATH):
        print(f"[INFO] '{ALARM_SOUND_PATH}' not found. Generating a synthetic alert tone...")
        try:
            import math, struct, wave
            with wave.open(ALARM_SOUND_PATH, "w") as wav_file:
                wav_file.setnchannels(1)
                wav_file.setsampwidth(2)
                wav_file.setframerate(44100)
                for i in range(int(1.0 * 44100)):
                    t = float(i) / 44100.0
                    freq = 1100.0 if (int(t * 10) % 2 == 0) else 1650.0
                    val = int(24000 * math.sin(2.0 * math.pi * freq * t))
                    wav_file.writeframesraw(struct.pack("<h", max(-32768, min(32767, val))))
            print(f"[SUCCESS] Created temporary alarm sound: '{ALARM_SOUND_PATH}'")
        except Exception as gen_err:
            print(f"[WARNING] Could not auto-generate alarm WAV: {gen_err}")

    try:
        sound = pygame.mixer.Sound(ALARM_SOUND_PATH)
        return sound
    except Exception as e:
        print(f"[WARNING] Failed to load alarm sound '{ALARM_SOUND_PATH}': {e}")
        return None

# ==============================================================================
# 2. Haar Cascade Classifier Resolution
# ==============================================================================
def load_haar_cascades():
    """
    Loads Haar cascade classifiers for face and eye detection.
    Searches local project folder first, then falls back to OpenCV's built-in data directory.
    """
    face_candidates = [
        os.path.join("haar_cascades", "haarcascade_frontalface_default.xml"),
        "haarcascade_frontalface_default.xml",
        os.path.join(cv2.data.haarcascades, "haarcascade_frontalface_default.xml")
    ]
    eye_candidates = [
        os.path.join("haar_cascades", "haarcascade_eye.xml"),
        "haarcascade_eye.xml",
        os.path.join(cv2.data.haarcascades, "haarcascade_eye.xml")
    ]

    face_path = next((p for p in face_candidates if os.path.exists(p)), None)
    eye_path = next((p for p in eye_candidates if os.path.exists(p)), None)

    if not face_path or not eye_path:
        print("[ERROR] Haar Cascade XML files could not be located!")
        print(f"Looked in: {cv2.data.haarcascades}")
        sys.exit(1)

    print(f"[INFO] Using Face Cascade: {face_path}")
    print(f"[INFO] Using Eye Cascade:  {eye_path}")

    face_cascade = cv2.CascadeClassifier(face_path)
    eye_cascade = cv2.CascadeClassifier(eye_path)

    if face_cascade.empty() or eye_cascade.empty():
        print("[ERROR] Failed to load Haar cascades into OpenCV classifiers.")
        sys.exit(1)

    return face_cascade, eye_cascade

# ==============================================================================
# 3. Model Loading with Auto-Build Fallback
# ==============================================================================
def load_trained_model():
    """
    Loads the trained Keras CNN model from MODEL_PATH.
    If missing, prompts the user and builds an active fallback so the script doesn't crash.
    """
    if os.path.exists(MODEL_PATH):
        try:
            print(f"[INFO] Loading trained CNN eye model from: '{MODEL_PATH}'")
            model = load_model(MODEL_PATH)
            return model
        except Exception as e:
            print(f"[ERROR] Failed to load model from '{MODEL_PATH}': {e}")

    # Fallback guidance if model is missing
    print("=" * 70)
    print(f"[NOTICE] Model file '{MODEL_PATH}' not found!")
    print("Please run 'python model_training.py' to train and save the model.")
    print("Running in heuristic eye-aspect simulation mode for now...")
    print("=" * 70)
    return None

# ==============================================================================
# 4. Eye Region Classification
# ==============================================================================
def predict_eye_state(eye_roi, model):
    """
    Preprocesses the cropped eye region and performs model prediction:
      - Converts to Grayscale (if not already)
      - Resizes to (24, 24)
      - Normalizes pixel values [0, 1]
      - Expands dimensions to (1, 24, 24, 1) for Keras batch input
    
    Returns:
      is_closed (bool): True if classified as Closed, False if Open
      confidence (float): Prediction confidence score (0.0 to 1.0)
    """
    if eye_roi is None or eye_roi.size == 0:
        return False, 0.0

    # Ensure grayscale
    if len(eye_roi.shape) == 3:
        gray_eye = cv2.cvtColor(eye_roi, cv2.COLOR_BGR2GRAY)
    else:
        gray_eye = eye_roi

    # Resize to model input dimensions (24x24)
    resized_eye = cv2.resize(gray_eye, EYE_IMAGE_SIZE)

    # Heuristic fallback if model weights are not yet trained on disk
    if model is None:
        # Simple brightness and contrast heuristic for simulation
        mean_intensity = np.mean(resized_eye)
        is_closed = mean_intensity < 65
        return is_closed, 0.85

    # Normalize pixels to [0.0, 1.0]
    normalized_eye = resized_eye.astype("float32") / 255.0
    input_tensor = np.expand_dims(normalized_eye, axis=(0, -1))  # Shape: (1, 24, 24, 1)

    # Model inference: output is [P(Closed), P(Open)]
    predictions = model.predict(input_tensor, verbose=0)[0]
    p_closed = float(predictions[0])
    p_open = float(predictions[1])

    is_closed = p_closed > p_open
    confidence = p_closed if is_closed else p_open

    return is_closed, confidence

# ==============================================================================
# 5. Real-Time Video Loop
# ==============================================================================
def main():
    print("=" * 65)
    print("   DRIVER DROWSINESS DETECTION - REAL-TIME MONITOR   ")
    print("   Press 'q' in the camera window to safely exit.    ")
    print("=" * 65)

    # Initialize components
    alarm_sound = initialize_alarm()
    face_cascade, eye_cascade = load_haar_cascades()
    model = load_trained_model()

    # Open webcam feed
    print("[INFO] Initializing webcam video capture (index 0)...")
    cap = cv2.VideoCapture(0)

    # Error handling: Check if webcam opened successfully
    if not cap.isOpened():
        print("[ERROR] Could not access webcam on index 0!")
        print("Troubleshooting:")
        print("  1. Ensure no other application (Zoom, Teams, browser) is using the webcam.")
        print("  2. If using an external USB camera, try changing index to 1: cv2.VideoCapture(1).")
        print("  3. Check camera privacy permissions in your OS settings.")
        sys.exit(1)

    score = 0
    alarm_playing = False
    fps_start_time = time.time()
    fps_frame_count = 0
    current_fps = 0.0

    try:
        while True:
            ret, frame = cap.read()
            if not ret or frame is None:
                print("[WARNING] Empty frame received from webcam. Re-trying...")
                time.sleep(0.05)
                continue

            frame_height, frame_width = frame.shape[:2]
            gray_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)

            # Calculate live FPS
            fps_frame_count += 1
            if time.time() - fps_start_time >= 1.0:
                current_fps = fps_frame_count / (time.time() - fps_start_time)
                fps_frame_count = 0
                fps_start_time = time.time()

            # ------------------------------------------------------------------
            # Detect Faces
            # ------------------------------------------------------------------
            faces = face_cascade.detectMultiScale(
                gray_frame,
                scaleFactor=1.2,
                minNeighbors=5,
                minSize=(100, 100)
            )

            eyes_in_frame = 0
            closed_eyes_in_frame = 0

            # If no face is detected, provide visual feedback
            if len(faces) == 0:
                cv2.putText(
                    frame, "NO DRIVER FACE DETECTED",
                    (30, 45), cv2.FONT_HERSHEY_DUPLEX, 0.75, (0, 165, 255), 2
                )
            else:
                for (fx, fy, fw, fh) in faces:
                    # Draw subtle bounding box around face
                    cv2.rectangle(frame, (fx, fy), (fx + fw, fy + fh), (200, 180, 50), 2)

                    # Restrict eye search to upper 60% of the detected face region
                    roi_gray_face = gray_frame[fy:fy + int(fh * 0.65), fx:fx + fw]
                    roi_color_face = frame[fy:fy + int(fh * 0.65), fx:fx + fw]

                    eyes = eye_cascade.detectMultiScale(
                        roi_gray_face,
                        scaleFactor=1.15,
                        minNeighbors=4,
                        minSize=(25, 25),
                        maxSize=(int(fw * 0.4), int(fh * 0.4))
                    )

                    for (ex, ey, ew, eh) in eyes:
                        eyes_in_frame += 1
                        eye_roi = roi_gray_face[ey:ey + eh, ex:ex + ew]

                        # Predict eye state (Closed vs Open)
                        is_closed, confidence = predict_eye_state(eye_roi, model)

                        # Color coding: Red for Closed, Vibrant Green for Open
                        box_color = (0, 0, 255) if is_closed else (0, 255, 100)
                        status_label = "Closed" if is_closed else "Open"

                        if is_closed:
                            closed_eyes_in_frame += 1

                        # Draw eye bounding box and state label
                        abs_ex, abs_ey = fx + ex, fy + ey
                        cv2.rectangle(frame, (abs_ex, abs_ey), (abs_ex + ew, abs_ey + eh), box_color, 2)
                        cv2.putText(
                            frame,
                            f"{status_label} ({int(confidence * 100)}%)",
                            (abs_ex, abs_ey - 6),
                            cv2.FONT_HERSHEY_SIMPLEX, 0.45, box_color, 1
                        )

            # ------------------------------------------------------------------
            # Dynamic Fatigue Score Update Logic
            # ------------------------------------------------------------------
            # If at least one detected eye is closed (or both), accumulate drowsiness score
            if closed_eyes_in_frame > 0 and eyes_in_frame > 0:
                score += 1
            else:
                # Driver eyes are open: smoothly decrease drowsiness score
                if score > 0:
                    score -= 1

            # ------------------------------------------------------------------
            # Alarm Sound Triggering
            # ------------------------------------------------------------------
            if score >= DROWSINESS_SCORE_THRESHOLD:
                # Trigger loud alert when drowsiness threshold is reached
                if alarm_sound is not None and not alarm_playing:
                    try:
                        alarm_sound.play(loops=-1)  # Loop continuously while drowsy
                        alarm_playing = True
                    except Exception as sound_err:
                        print(f"[WARNING] Could not trigger sound playback: {sound_err}")

                # Draw bold emergency red border around the whole video window
                cv2.rectangle(frame, (0, 0), (frame_width - 1, frame_height - 1), (0, 0, 255), 10)

                # Flashing warning banner
                if int(time.time() * 4) % 2 == 0:
                    cv2.putText(
                        frame, "*** ALERT! DROWSINESS DETECTED! ***",
                        (frame_width // 2 - 280, frame_height - 35),
                        cv2.FONT_HERSHEY_DUPLEX, 0.85, (0, 0, 255), 2
                    )
            else:
                # Stop alarm once eyes reopen and score decreases
                if alarm_sound is not None and alarm_playing:
                    alarm_sound.stop()
                    alarm_playing = False

            # ------------------------------------------------------------------
            # Heads-Up Display (HUD) Panel
            # ------------------------------------------------------------------
            hud_bg = frame[0:80, 0:frame_width]
            overlay = hud_bg.copy()
            cv2.rectangle(overlay, (0, 0), (frame_width, 80), (20, 20, 20), -1)
            cv2.addWeighted(overlay, 0.7, hud_bg, 0.3, 0, hud_bg)

            # Status color badge
            state_text = "DROWSY - ALARM ACTIVE" if score >= DROWSINESS_SCORE_THRESHOLD else (
                "EYES CLOSED" if closed_eyes_in_frame > 0 else "DRIVER AWAKE"
            )
            state_color = (0, 0, 255) if score >= DROWSINESS_SCORE_THRESHOLD else (
                (0, 165, 255) if closed_eyes_in_frame > 0 else (0, 255, 120)
            )

            cv2.putText(frame, f"STATUS: {state_text}", (20, 32), cv2.FONT_HERSHEY_DUPLEX, 0.65, state_color, 2)
            cv2.putText(frame, f"Fatigue Score: {score}/{DROWSINESS_SCORE_THRESHOLD}", (20, 62), cv2.FONT_HERSHEY_SIMPLEX, 0.55, (240, 240, 240), 1)
            cv2.putText(frame, f"FPS: {current_fps:.1f}", (frame_width - 110, 32), cv2.FONT_HERSHEY_SIMPLEX, 0.55, (200, 200, 200), 1)
            cv2.putText(frame, "Press 'q' to Quit", (frame_width - 160, 62), cv2.FONT_HERSHEY_SIMPLEX, 0.45, (160, 160, 160), 1)

            # Show the rendered frame
            cv2.imshow("Driver Drowsiness & Fatigue Detection (OpenCV & Keras)", frame)

            # Wait for user key press ('q' to terminate)
            key = cv2.waitKey(1) & 0xFF
            if key == ord('q') or key == 27:  # 'q' or ESC
                print("[INFO] Exit signal received. Terminating monitor...")
                break

    except KeyboardInterrupt:
        print("[INFO] Process interrupted by user.")
    finally:
        # Proper resource release
        if alarm_sound is not None and alarm_playing:
            alarm_sound.stop()
        if 'cap' in locals() and cap.isOpened():
            cap.release()
        cv2.destroyAllWindows()
        print("[INFO] Camera released and OpenCV windows destroyed cleanly.")

if __name__ == "__main__":
    main()
