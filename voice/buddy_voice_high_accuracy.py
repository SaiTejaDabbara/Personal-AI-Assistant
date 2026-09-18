import re
import sys
import time
from collections import deque

import numpy as np
import sounddevice as sd
from faster_whisper import WhisperModel


# ============================================================
# BUDDY VOICE - HIGH ACCURACY MODE
# ============================================================
#
# Pipeline:
#   microphone
#      -> noise calibration
#      -> wake-word recognition
#      -> command recording with pre-roll
#      -> VAD-aware transcription
#      -> confidence / hallucination checks
#      -> cleaned command
#
# This file intentionally does NOT execute desktop actions itself.
# The recognized command can be passed to Buddy's Java Commands.process().
#
# ============================================================


# ============================================================
# BRIDGE MODE
# ============================================================

BRIDGE_MODE = "--bridge" in sys.argv


def log(message):
    """Keep stdout machine-readable when Java bridge mode is active."""
    stream = sys.stderr if BRIDGE_MODE else sys.stdout
    log(message, file=stream, flush=True)


# ============================================================
# SETTINGS
# ============================================================

SAMPLE_RATE = 16000
CHANNELS = 1

BLOCK_DURATION = 0.10
BLOCK_SIZE = int(SAMPLE_RATE * BLOCK_DURATION)

# Wake-word recognition
WAKE_DURATION = 2.0
WAKE_COOLDOWN = 0.60

# Command recording
MAX_RECORD_SECONDS = 12.0
PRE_RECORD_SECONDS = 0.60
SILENCE_DURATION = 1.10

# Mic detection
MIN_SPEECH_RMS = 0.008
NOISE_MULTIPLIER = 2.2
MIN_DYNAMIC_THRESHOLD = 0.008
MAX_DYNAMIC_THRESHOLD = 0.08

# Accuracy / transcription
WAKE_BEAM_SIZE = 4
COMMAND_BEAM_SIZE = 5

# Whisper VAD
VAD_MIN_SILENCE_MS = 450
VAD_SPEECH_PAD_MS = 180

# Reject extremely weak / suspicious recognitions
MIN_AVG_LOGPROB = -1.20
MAX_NO_SPEECH_PROB = 0.80
MAX_COMPRESSION_RATIO = 2.80

# The current setup uses faster-whisper.
# Keep the model names you already use if these are locally available.
WAKE_MODEL_NAME = "base.en"
COMMAND_MODEL_NAME = "high.en"


# ============================================================
# LOAD MODELS
# ============================================================

log("Loading Buddy voice system...")

wake_model = WhisperModel(
    WAKE_MODEL_NAME,
    device="cpu",
    compute_type="int8"
)

command_model = WhisperModel(
    COMMAND_MODEL_NAME,
    device="cpu",
    compute_type="int8"
)

log("Buddy voice system ready!")
log("Say 'Buddy' to activate.\n")


# ============================================================
# AUDIO HELPERS
# ============================================================

def rms_level(audio):
    """Return RMS energy for a float32 mono signal."""
    if audio is None or len(audio) == 0:
        return 0.0

    audio = np.asarray(audio, dtype=np.float32)

    return float(
        np.sqrt(
            np.mean(
                np.square(audio)
            )
        )
    )


def peak_level(audio):
    """Return peak absolute amplitude."""
    if audio is None or len(audio) == 0:
        return 0.0

    return float(
        np.max(
            np.abs(audio)
        )
    )


def normalize_audio(audio):
    """
    Safely normalize microphone audio without aggressively amplifying noise.
    """
    if audio is None or len(audio) == 0:
        return None

    audio = np.asarray(
        audio,
        dtype=np.float32
    )

    audio = np.nan_to_num(
        audio,
        nan=0.0,
        posinf=0.0,
        neginf=0.0
    )

    peak = peak_level(audio)

    if peak <= 0.0001:
        return audio

    # Prevent clipping and avoid huge amplification of very quiet noise.
    if peak < 0.35:
        gain = min(
            3.0,
            0.35 / peak
        )

        audio = audio * gain

    audio = np.clip(
        audio,
        -1.0,
        1.0
    )

    return audio.astype(
        np.float32
    )


def calibrate_noise(seconds=0.75):
    """
    Measure the current microphone background level.

    This is more reliable than a single hard-coded threshold because
    microphones and room noise vary significantly.
    """

    log("Calibrating microphone noise...")

    try:
        audio = sd.rec(
            int(
                seconds * SAMPLE_RATE
            ),
            samplerate=SAMPLE_RATE,
            channels=CHANNELS,
            dtype="float32"
        )

        sd.wait()

        audio = audio.flatten()

        level = rms_level(audio)

        dynamic_threshold = max(
            MIN_DYNAMIC_THRESHOLD,
            level * NOISE_MULTIPLIER
        )

        dynamic_threshold = min(
            MAX_DYNAMIC_THRESHOLD,
            dynamic_threshold
        )

        log(
            f"Noise level: {level:.4f} | "
            f"speech threshold: {dynamic_threshold:.4f}"
        )

        return dynamic_threshold

    except Exception as exc:

        log(
            f"Microphone calibration failed: {exc}"
        )

        return MIN_DYNAMIC_THRESHOLD


# ============================================================
# TEXT CLEANING
# ============================================================

def clean_transcription(text):
    """
    Conservative cleanup.

    Do not aggressively rewrite speech because changing a recognized
    application name can be worse than leaving the original wording.
    """

    if text is None:
        return ""

    text = text.strip()

    # Remove common Whisper formatting artifacts.
    text = re.sub(
        r"\s+",
        " ",
        text
    )

    text = text.strip(
        " \t\r\n.,!?;:"
    )

    return text


def contains_wake_word(text):
    if not text:
        return False

    normalized = re.sub(
        r"[^a-z0-9\s]",
        " ",
        text.lower()
    )

    normalized = re.sub(
        r"\s+",
        " ",
        normalized
    ).strip()

    # "buddy" is the intended wake word.
    #
    # Keep a very small tolerance for the common Whisper confusion
    # "body". Do not add a large list of fuzzy words because that
    # increases false activations.
    tokens = set(
        normalized.split()
    )

    return (
        "buddy" in tokens
        or "body" in tokens
    )


# ============================================================
# WAKE WORD LISTENER
# ============================================================

def listen_for_wake_word():
    """
    Capture a short audio window and run the wake model.

    A small chunk plus VAD keeps false positives lower than blindly
    accepting any non-empty transcript.
    """

    audio = sd.rec(
        int(
            WAKE_DURATION * SAMPLE_RATE
        ),
        samplerate=SAMPLE_RATE,
        channels=CHANNELS,
        dtype="float32"
    )

    sd.wait()

    audio = audio.flatten()

    if rms_level(audio) < MIN_SPEECH_RMS:
        return False

    try:

        segments, info = wake_model.transcribe(
            audio,
            language="en",
            beam_size=WAKE_BEAM_SIZE,
            vad_filter=True,
            vad_parameters={
                "min_silence_duration_ms": 350
            },
            condition_on_previous_text=False,
            temperature=0.0,
            initial_prompt="Buddy"
        )

        heard_parts = []

        for segment in segments:

            text = clean_transcription(
                segment.text
            )

            if not text:
                continue

            # Ignore obvious low-confidence wake hypotheses.
            if (
                getattr(
                    segment,
                    "no_speech_prob",
                    0.0
                ) > 0.85
            ):
                continue

            if (
                getattr(
                    segment,
                    "avg_logprob",
                    0.0
                ) < -1.80
            ):
                continue

            heard_parts.append(
                text
            )

        heard = " ".join(
            heard_parts
        ).strip()

        if heard:
            log(
                f"\nWake heard: {heard}"
            )

        return contains_wake_word(
            heard
        )

    except Exception as exc:

        log(
            f"Wake recognition error: {exc}"
        )

        return False


# ============================================================
# COMMAND RECORDING
# ============================================================

def record_command():
    """
    Record until the user stops speaking.

    Improvements over the original:
      - calibrates the room first
      - keeps a pre-roll buffer
      - uses a dynamic speech threshold
      - requires speech before starting the timer
      - avoids triggering on very small noise bursts
    """

    log(
        "\n🎤 Buddy is listening... Speak now!"
    )

    threshold = calibrate_noise()

    pre_buffer = deque(
        maxlen=max(
            1,
            int(
                PRE_RECORD_SECONDS
                / BLOCK_DURATION
            )
        )
    )

    recorded_audio = []

    speech_started = False
    silence_time = 0.0
    elapsed = 0.0

    def callback(
            indata,
            frames,
            time_info,
            status):

        nonlocal speech_started
        nonlocal silence_time

        if status:
            log(
                f"\nAudio status: {status}"
            )

        audio_block = (
            indata
            .copy()
            .reshape(-1)
            .astype(np.float32)
        )

        level = rms_level(
            audio_block
        )

        pre_buffer.append(
            audio_block
        )

        if not speech_started:

            # Require a meaningful signal rather than one tiny spike.
            if level >= threshold:

                speech_started = True

                log(
                    "🗣️ Speech detected..."
                )

                recorded_audio.extend(
                    list(pre_buffer)
                )

                silence_time = 0.0

        else:

            recorded_audio.append(
                audio_block
            )

            if level >= threshold:

                silence_time = 0.0

            else:

                silence_time += BLOCK_DURATION

    try:

        with sd.InputStream(
                samplerate=SAMPLE_RATE,
                channels=CHANNELS,
                dtype="float32",
                callback=callback,
                blocksize=BLOCK_SIZE
        ):

            while elapsed < MAX_RECORD_SECONDS:

                sd.sleep(
                    int(
                        BLOCK_DURATION
                        * 1000
                    )
                )

                elapsed += (
                    BLOCK_DURATION
                )

                if (
                        speech_started
                        and silence_time
                        >= SILENCE_DURATION
                ):

                    log(
                        "🤫 Command ended."
                    )

                    break

    except Exception as exc:

        log(
            f"Microphone recording error: {exc}"
        )

        return None

    if not speech_started:

        log(
            "No command detected."
        )

        return None

    if not recorded_audio:

        return None

    audio = np.concatenate(
        recorded_audio,
        axis=0
    )

    # Remove accidental DC offset.
    audio = audio - np.mean(
        audio
    )

    # Normalize safely.
    audio = normalize_audio(
        audio
    )

    if audio is None:

        return None

    if (
            rms_level(audio)
            < MIN_SPEECH_RMS
    ):

        log(
            "Recorded signal was too weak."
        )

        return None

    return audio.flatten()


# ============================================================
# SPEECH TO TEXT
# ============================================================

def speech_to_text(audio):
    """
    High-accuracy command transcription.

    We keep individual segment metadata so low-confidence or
    hallucinated segments can be rejected before Commands receives
    anything.
    """

    if audio is None or len(audio) == 0:
        return ""

    log(
        "🧠 Processing command..."
    )

    try:

        segments, info = command_model.transcribe(
            audio,
            language="en",
            beam_size=COMMAND_BEAM_SIZE,
            vad_filter=True,
            vad_parameters={
                "min_silence_duration_ms":
                    VAD_MIN_SILENCE_MS,
                "speech_pad_ms":
                    VAD_SPEECH_PAD_MS
            },
            condition_on_previous_text=False,
            temperature=0.0,
            initial_prompt=(
                "Buddy desktop assistant. "
                "Commands may include opening applications, "
                "creating files or folders, moving files, "
                "searching, downloading, renaming, deleting, "
                "playing media, and normal conversation."
            )
        )

        accepted = []

        confidence_values = []

        for segment in segments:

            text = clean_transcription(
                segment.text
            )

            if not text:
                continue

            no_speech_prob = float(
                getattr(
                    segment,
                    "no_speech_prob",
                    0.0
                )
            )

            avg_logprob = float(
                getattr(
                    segment,
                    "avg_logprob",
                    0.0
                )
            )

            compression_ratio = float(
                getattr(
                    segment,
                    "compression_ratio",
                    0.0
                )
            )

            # Skip segments that are very likely silence.
            if (
                    no_speech_prob
                    > MAX_NO_SPEECH_PROB
            ):
                continue

            # Skip very suspicious low-confidence output.
            if (
                    avg_logprob
                    < MIN_AVG_LOGPROB
            ):
                continue

            # Whisper sometimes produces repetitive hallucinations.
            if (
                    compression_ratio
                    > MAX_COMPRESSION_RATIO
            ):
                continue

            accepted.append(
                text
            )

            confidence_values.append(
                avg_logprob
            )

        result = " ".join(
            accepted
        )

        result = clean_transcription(
            result
        )

        if not result:
            log(
                "⚠️ Speech recognition was uncertain."
            )

            return ""

        average_logprob = (
            sum(confidence_values)
            / len(confidence_values)
            if confidence_values
            else -99.0
        )

        log(
            f"Recognition score: "
            f"{average_logprob:.2f}"
        )

        log(
            f"Recognized: {result}"
        )

        return result.lower()

    except Exception as exc:

        log(
            f"Speech recognition error: {exc}"
        )

        return ""


# ============================================================
# MAIN VOICE LOOP
# ============================================================

def main():

    log(
        "🎤 Listening for Buddy...",
    )

    try:

        while True:

            wake_detected = (
                listen_for_wake_word()
            )

            if not wake_detected:

                continue

            log(
                "\n🔥 BUDDY ACTIVATED! 🔥"
            )

            time.sleep(
                WAKE_COOLDOWN
            )

            audio = record_command()

            if audio is None:

                log(
                    "Buddy did not hear a command."
                )

                log(
                    "\n🔄 Returning to wake mode...\n"
                )

                continue

            command = speech_to_text(
                audio
            )

            if not command:

                log(
                    "⚠️ I wasn't confident enough "
                    "to use that command."
                )

                log(
                    "Please try again."
                )

            else:

                log(
                    f"\nBuddy heard: {command}"
                )

                if BRIDGE_MODE:
                    print(
                        f"VOICE_COMMAND\\t{command}",
                        file=sys.stdout,
                        flush=True
                    )

            log(
                "\n🔄 Returning to wake mode...\n"
            )

    except KeyboardInterrupt:

        log(
            "\nBuddy voice system stopped."
        )

    except Exception as exc:

        log(
            f"\nBuddy voice system error: {exc}"
        )


if __name__ == "__main__":
    main()
