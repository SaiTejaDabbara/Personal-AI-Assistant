import sounddevice as sd
import numpy as np
import time
from collections import deque
from faster_whisper import WhisperModel


# ==========================================
# SETTINGS
# ==========================================

SAMPLE_RATE = 16000
CHANNELS = 1

BLOCK_DURATION = 0.1
BLOCK_SIZE = int(SAMPLE_RATE * BLOCK_DURATION)

# Wake listening
WAKE_DURATION = 2.0
WAKE_COOLDOWN = 0.5

# Command recording
SILENCE_THRESHOLD = 0.01
SILENCE_DURATION = 1.2
MAX_RECORD_SECONDS = 12
PRE_RECORD_SECONDS = 0.5


# ==========================================
# LOAD MODELS
# ==========================================

print("Loading Buddy voice system...")

# Wake word recognition model
wake_model = WhisperModel(
    "base.en",
    device="cpu",
    compute_type="int8"
)

# High-accuracy command recognition model
command_model = WhisperModel(
    "high.en",
    device="cpu",
    compute_type="int8"
)

print("Buddy voice system ready!")
print("Say 'Buddy' to activate.\n")


# ==========================================
# WAKE WORD LISTENER
# ==========================================

def listen_for_wake_word():

    audio = sd.rec(
        int(WAKE_DURATION * SAMPLE_RATE),
        samplerate=SAMPLE_RATE,
        channels=CHANNELS,
        dtype="float32"
    )

    sd.wait()

    audio = audio.flatten()

    segments, info = wake_model.transcribe(
        audio,
        language="en",
        beam_size=3,
        vad_filter=True,
        condition_on_previous_text=False,
        initial_prompt="The wake word is Buddy."
    )

    heard = ""

    for segment in segments:
        heard += segment.text

    heard = heard.strip().lower()

    if heard:

        print(f"Heard: {heard}")

    wake_variations = [
        "buddy",
        "body"
    ]

    for word in wake_variations:

        if word in heard:

            return True

    return False


# ==========================================
# RECORD COMMAND UNTIL SILENCE
# ==========================================

def record_command():

    print("\n🎤 Buddy is listening... Speak now!")

    pre_buffer = deque(
        maxlen=int(
            PRE_RECORD_SECONDS / BLOCK_DURATION
        )
    )

    recorded_audio = []

    speech_started = False
    silence_time = 0.0
    elapsed = 0.0


    def callback(indata, frames, time_info, status):

        nonlocal speech_started
        nonlocal silence_time

        if status:
            print(status)

        audio_block = indata.copy()

        volume = np.sqrt(
            np.mean(
                np.square(audio_block)
            )
        )

        pre_buffer.append(audio_block)


        # Wait for speech
        if not speech_started:

            if volume > SILENCE_THRESHOLD:

                speech_started = True

                print("🗣️ Speech detected...")

                recorded_audio.extend(
                    list(pre_buffer)
                )

                silence_time = 0.0


        # Record after speech begins
        else:

            recorded_audio.append(
                audio_block
            )

            if volume > SILENCE_THRESHOLD:

                silence_time = 0.0

            else:

                silence_time += BLOCK_DURATION


    with sd.InputStream(
            samplerate=SAMPLE_RATE,
            channels=CHANNELS,
            dtype="float32",
            callback=callback,
            blocksize=BLOCK_SIZE
    ):

        while elapsed < MAX_RECORD_SECONDS:

            sd.sleep(
                int(BLOCK_DURATION * 1000)
            )

            elapsed += BLOCK_DURATION

            if (
                    speech_started
                    and silence_time >= SILENCE_DURATION
            ):

                print("🤫 Command ended.")

                break


    if not speech_started:

        print("No command detected.")

        return None


    audio = np.concatenate(
        recorded_audio,
        axis=0
    )

    return audio.flatten()


# ==========================================
# SPEECH TO TEXT
# ==========================================

def speech_to_text(audio):

    print("🧠 Processing command...")

    segments, info = command_model.transcribe(
        audio,
        language="en",
        beam_size=5,
        vad_filter=True,
        condition_on_previous_text=False
    )

    text = ""

    for segment in segments:

        text += segment.text

    return text.strip().lower()


# ==========================================
# MAIN VOICE LOOP
# ==========================================

try:

    while True:

        print(
            "🎤 Listening for Buddy...",
            end="\r"
        )

        wake_detected = listen_for_wake_word()


        if wake_detected:

            print(
                "\n🔥 BUDDY ACTIVATED! 🔥"
            )

            time.sleep(
                WAKE_COOLDOWN
            )


            # Listen for command
            audio = record_command()


            if audio is not None:

                command = speech_to_text(
                    audio
                )

                print(
                    f"\nBuddy heard: {command}"
                )

            else:

                print(
                    "Buddy did not hear a command."
                )


            print(
                "\n🔄 Returning to wake mode...\n"
            )


except KeyboardInterrupt:

    print(
        "\nBuddy voice system stopped."
    )