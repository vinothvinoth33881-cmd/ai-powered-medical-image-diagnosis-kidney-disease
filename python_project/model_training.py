"""
================================================================================
Driver Drowsiness Detection System - CNN Model Training Script
================================================================================
Purpose:
    Defines, compiles, trains, and saves a Convolutional Neural Network (CNN)
    using Keras / TensorFlow to classify cropped eye images into two states:
        - Class 0: Closed Eyes
        - Class 1: Open Eyes
    The trained model weights are saved as 'models/model.h5' for real-time inference.

Compatible Datasets:
    1. MRL Eye Dataset (https://mrl.cs.vsb.cz/eyedataset)
    2. Closed Eyes in the Wild (CEW) Dataset
================================================================================
"""

import os
import sys
import numpy as np
import tensorflow as tf
from tensorflow.keras.models import Sequential
from tensorflow.keras.layers import Conv2D, MaxPooling2D, Flatten, Dense, Dropout, BatchNormalization
from tensorflow.keras.preprocessing.image import ImageDataGenerator
from tensorflow.keras.callbacks import ModelCheckpoint, EarlyStopping, ReduceLROnPlateau

# Configure GPU memory growth if GPU is available to prevent memory allocation crashes
gpus = tf.config.list_physical_devices('GPU')
if gpus:
    try:
        for gpu in gpus:
            tf.config.experimental.set_memory_growth(gpu, True)
        print(f"[INFO] Detected {len(gpus)} GPU(s). Memory growth enabled.")
    except RuntimeError as e:
        print(f"[WARNING] Could not set GPU memory growth: {e}")
else:
    print("[INFO] No GPU found. Training will proceed on CPU.")

# ==============================================================================
# 1. Hyperparameters and Configuration
# ==============================================================================
IMAGE_WIDTH = 24
IMAGE_HEIGHT = 24
IMAGE_CHANNELS = 1          # 1 for Grayscale, 3 for RGB
BATCH_SIZE = 32
EPOCHS = 15
LEARNING_RATE = 0.001
MODEL_DIR = "models"
MODEL_PATH = os.path.join(MODEL_DIR, "model.h5")
DATASET_DIR = "dataset"     # Expected structure: dataset/train/{open, closed}, dataset/val/{open, closed}

os.makedirs(MODEL_DIR, exist_ok=True)

# ==============================================================================
# 2. Build the CNN Model Architecture
# ==============================================================================
def build_eye_cnn_model(input_shape=(IMAGE_HEIGHT, IMAGE_WIDTH, IMAGE_CHANNELS)):
    """
    Constructs a sequential Convolutional Neural Network tailored for 24x24 grayscale eye patches.
    
    Architecture summary:
      - Block 1: Conv2D(32, 3x3) -> BatchNormalization -> ReLU -> MaxPooling2D(2x2)
      - Block 2: Conv2D(64, 3x3) -> BatchNormalization -> ReLU -> MaxPooling2D(2x2)
      - Block 3: Conv2D(128, 3x3) -> BatchNormalization -> ReLU -> MaxPooling2D(2x2)
      - Flatten -> Dense(128, ReLU) -> Dropout(0.5) -> Dense(2, Softmax)
    """
    model = Sequential([
        # --- First Convolutional Block ---
        # 32 filters of size 3x3 to extract low-level edges, iris curves, and eyelids
        Conv2D(32, kernel_size=(3, 3), activation='relu', input_shape=input_shape, padding='same', name='conv1'),
        BatchNormalization(name='bn1'),
        MaxPooling2D(pool_size=(2, 2), name='pool1'),  # Downsample to 12x12

        # --- Second Convolutional Block ---
        # 64 filters to detect complex patterns (pupil presence, eyelid opening slit)
        Conv2D(64, kernel_size=(3, 3), activation='relu', padding='same', name='conv2'),
        BatchNormalization(name='bn2'),
        MaxPooling2D(pool_size=(2, 2), name='pool2'),  # Downsample to 6x6

        # --- Third Convolutional Block ---
        # 128 filters for high-level semantic features of eye state
        Conv2D(128, kernel_size=(3, 3), activation='relu', padding='same', name='conv3'),
        BatchNormalization(name='bn3'),
        MaxPooling2D(pool_size=(2, 2), name='pool3'),  # Downsample to 3x3

        # --- Flattening Layer ---
        # Flattens 3x3x128 feature tensor into a 1D vector of 1152 elements
        Flatten(name='flatten'),

        # --- Fully Connected / Dense Layers ---
        Dense(128, activation='relu', name='fc1'),
        Dropout(0.5, name='dropout'),  # 50% dropout to prevent overfitting on specific eyes

        # --- Output Classification Layer ---
        # 2 units: [P(Closed), P(Open)] using Softmax activation
        Dense(2, activation='softmax', name='output')
    ])

    # Compile model with Adam optimizer and Sparse/Categorical Crossentropy
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=LEARNING_RATE),
        loss='categorical_crossentropy',
        metrics=['accuracy']
    )

    return model

# ==============================================================================
# 3. Data Pipeline / Fallback Generator
# ==============================================================================
def create_synthetic_demo_data(num_samples=400):
    """
    Generates synthetic synthetic eye-like samples so this script runs and saves a valid
    'models/model.h5' even before the user manually downloads the full MRL/CEW dataset.
    """
    print("[INFO] Dataset folder not populated. Generating synthetic eye data for immediate verification...")
    x_data = np.zeros((num_samples, IMAGE_HEIGHT, IMAGE_WIDTH, IMAGE_CHANNELS), dtype=np.float32)
    y_data = np.zeros((num_samples, 2), dtype=np.float32)

    for i in range(num_samples):
        img = np.random.normal(0.4, 0.05, (IMAGE_HEIGHT, IMAGE_WIDTH, IMAGE_CHANNELS))
        if i % 2 == 0:
            # Closed eye: horizontal dark slit across center
            img[10:14, 4:20, :] = 0.1
            y_data[i] = [1.0, 0.0]  # Closed
        else:
            # Open eye: circular dark pupil in center with white sclera around it
            img[8:16, 8:16, :] = 0.05
            img[6:18, 4:8, :] = 0.85
            img[6:18, 16:20, :] = 0.85
            y_data[i] = [0.0, 1.0]  # Open
        x_data[i] = np.clip(img, 0.0, 1.0)

    # Train / Val split (80 / 20)
    split_idx = int(0.8 * num_samples)
    return (x_data[:split_idx], y_data[:split_idx]), (x_data[split_idx:], y_data[split_idx:])

def get_data_generators(dataset_path):
    """
    Prepares Keras ImageDataGenerators with data augmentation for real datasets.
    """
    train_dir = os.path.join(dataset_path, "train")
    val_dir = os.path.join(dataset_path, "val")

    # Data augmentation for training to improve model generalization under varying lighting
    train_datagen = ImageDataGenerator(
        rescale=1.0 / 255.0,
        rotation_range=12,
        width_shift_range=0.1,
        height_shift_range=0.1,
        shear_range=0.1,
        zoom_range=0.1,
        horizontal_flip=True,
        fill_mode='nearest'
    )

    val_datagen = ImageDataGenerator(rescale=1.0 / 255.0)

    color_mode = 'grayscale' if IMAGE_CHANNELS == 1 else 'rgb'

    train_generator = train_datagen.flow_from_directory(
        train_dir,
        target_size=(IMAGE_HEIGHT, IMAGE_WIDTH),
        batch_size=BATCH_SIZE,
        color_mode=color_mode,
        class_mode='categorical',
        shuffle=True
    )

    val_generator = val_datagen.flow_from_directory(
        val_dir,
        target_size=(IMAGE_HEIGHT, IMAGE_WIDTH),
        batch_size=BATCH_SIZE,
        color_mode=color_mode,
        class_mode='categorical',
        shuffle=False
    )

    return train_generator, val_generator

# ==============================================================================
# 4. Training Execution
# ==============================================================================
def main():
    print("=" * 60)
    print("   DRIVER DROWSINESS DETECTION - CNN MODEL TRAINING   ")
    print("=" * 60)

    model = build_eye_cnn_model()
    model.summary()

    # Callbacks for robust training
    callbacks = [
        ModelCheckpoint(
            filepath=MODEL_PATH,
            monitor='val_accuracy',
            save_best_only=True,
            mode='max',
            verbose=1
        ),
        EarlyStopping(
            monitor='val_loss',
            patience=5,
            restore_best_weights=True,
            verbose=1
        ),
        ReduceLROnPlateau(
            monitor='val_loss',
            factor=0.5,
            patience=3,
            min_lr=1e-6,
            verbose=1
        )
    ]

    has_real_dataset = (
        os.path.exists(DATASET_DIR) and
        os.path.exists(os.path.join(DATASET_DIR, "train")) and
        os.path.exists(os.path.join(DATASET_DIR, "val"))
    )

    if has_real_dataset:
        print(f"[INFO] Found dataset at '{DATASET_DIR}'. Initializing data generators...")
        train_gen, val_gen = get_data_generators(DATASET_DIR)
        print(f"[INFO] Class mapping: {train_gen.class_indices}")

        history = model.fit(
            train_gen,
            validation_data=val_gen,
            epochs=EPOCHS,
            callbacks=callbacks,
            verbose=1
        )
    else:
        print(f"[NOTICE] No directory '{DATASET_DIR}' found.")
        print("[NOTICE] Training on synthetic benchmark patterns so 'models/model.h5' is ready immediately.")
        (x_train, y_train), (x_val, y_val) = create_synthetic_demo_data(num_samples=600)
        
        history = model.fit(
            x_train, y_train,
            validation_data=(x_val, y_val),
            epochs=8,
            batch_size=BATCH_SIZE,
            callbacks=callbacks,
            verbose=1
        )

    # Save the final model explicitly
    model.save(MODEL_PATH)
    print("=" * 60)
    print(f"[SUCCESS] Model successfully saved to: {os.path.abspath(MODEL_PATH)}")
    print("[NEXT STEP] Run 'python drowsiness_detection.py' to start real-time webcam detection.")
    print("=" * 60)

if __name__ == "__main__":
    main()
