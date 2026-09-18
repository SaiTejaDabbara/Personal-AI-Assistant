import sounddevice as sd
import numpy as np
from faster_whisper import WhisperModel
import time


# ==========================================
# SETTINGS
# ==========================================

SAMPLE_RATE = 16000
CHANNELS = 1

WAKE_DURATION = 2.0
COOLDOWN = 1.0


# ==========================================
# LOAD MODEL
# ==========================================

print("Loading Buddy wake listener...")

model = WhisperModel(
    "base.en",
    device="cpu",
    compute_type="int8"
)

print("Wake listener ready!")
print("Say: Buddy")
print()


# ==========================================
# LISTEN FOR WAKE WORD
# ==========================================

def listen_for_wake_word():

    print("🎤 Listening for Buddy...", end="\r")

    audio = sd.rec(
        int(WAKE_DURATION * SAMPLE_RATE),
        samplerate=SAMPLE_RATE,
        channels=CHANNELS,
        dtype="float32"
    )

    sd.wait()

    audio = audio.flatten()

    segments, info = model.transcribe(
        audio,
        language="en",
        beam_size=3,
        vad_filter=True,
        condition_on_previous_text=False,
        initial_prompt=(
            "The wake word is Buddy. "
            "Listen for the word Buddy."
        )
    )

    text = ""

    for segment in segments:
        text += segment.text

    text = text.strip().lower()

    return text


# ==========================================
# MAIN LOOP
# ==========================================

while True:

    try:

        heard = listen_for_wake_word()

        if heard:

            print(f"\nHeard: {heard}")

        # Accept common Whisper variations
        wake_variations = [
            "buddy",
            "body",
            "buddy."
        ]

        if any(
            word in heard
            for word in wake_variations
        ):

            print("\n🔥 BUDDY WAKE WORD DETECTED! 🔥\n")

            time.sleep(COOLDOWN)

    except KeyboardInterrupt:

        print("\nBuddy wake listener stopped.")

        break