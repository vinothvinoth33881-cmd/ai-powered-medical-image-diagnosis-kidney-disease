"""
Utility script to generate an emergency alarm sound (alarm.wav)
Uses only Python's standard library (wave, math, struct) so no extra packages are needed.
"""
import math
import struct
import wave
import os

def create_alarm_sound(filename="alarm.wav", duration_seconds=1.2, sample_rate=44100):
    """
    Generates a 2-tone pulsating buzzer alarm sound and saves it as a 16-bit PCM WAV.
    """
    os.makedirs(os.path.dirname(filename) if os.path.dirname(filename) else ".", exist_ok=True)
    num_samples = int(duration_seconds * sample_rate)
    
    with wave.open(filename, "w") as wav_file:
        wav_file.setnchannels(1)        # Mono
        wav_file.setsampwidth(2)       # 2 bytes = 16 bits
        wav_file.setframerate(sample_rate)
        
        for i in range(num_samples):
            t = float(i) / sample_rate
            # Pulsating frequency modulation: alternate between 880Hz and 1760Hz
            frequency = 950.0 if (int(t * 8) % 2 == 0) else 1450.0
            
            # Sine wave with gentle envelope
            sample = math.sin(2.0 * math.pi * frequency * t)
            # Add harmonic for buzzer buzz
            sample += 0.3 * math.sin(4.0 * math.pi * frequency * t)
            
            # Amplitude scaling
            amplitude = 26000
            value = int(sample * amplitude)
            # Clamp to 16-bit signed integer limits
            value = max(-32768, min(32767, value))
            data = struct.pack("<h", value)
            wav_file.writeframesraw(data)

    print(f"[SUCCESS] Generated alarm audio file: '{filename}' ({duration_seconds}s)")

if __name__ == "__main__":
    create_alarm_sound("alarm.wav")
