import re
import sys
import time
import threading
from collections import deque
from queue import Queue, Empty

import numpy as np
import sounddevice as sd
import pyttsx3
from faster_whisper import WhisperModel


# ============================================================
# BUDDY VOICE - CONTINUOUS JARVIS-STYLE VOICE BRIDGE
# ============================================================
#
# Microphone
#     ↓
# Dynamic noise floor / speech detection
#     ↓
# Rolling wake-word recognition
#     ↓
# "Buddy"
#     ↓
# TTS: "Yes? How can I help you?"
#     ↓
# Dynamic command recording
#     ↓
# faster-whisper
#     ↓
# Java Commands.process()
#     ↓
# Java -> Python:
#     SPEAK\t<text>
#     ↓
# pyttsx3
#
# Python -> Java stdout:
#     VOICE_COMMAND\t<command>
#
# ============================================================


# ============================================================
# BRIDGE MODE
# ============================================================

BRIDGE_MODE = "--bridge" in sys.argv


def log(message):
    """
    In bridge mode stdout is reserved for machine-readable
    communication with Java.
    """
    stream = sys.stderr if BRIDGE_MODE else sys.stdout

    print(
        message,
        file=stream,
        flush=True
    )


# ============================================================
# AUDIO SETTINGS
# ============================================================

SAMPLE_RATE = 16000
CHANNELS = 1

BLOCK_DURATION = 0.10
BLOCK_SIZE = int(
    SAMPLE_RATE * BLOCK_DURATION
)


# ============================================================
# WAKE WORD SETTINGS
# ============================================================

# Instead of taking isolated 2-second recordings, Buddy keeps
# a rolling microphone buffer.
WAKE_BUFFER_SECONDS = 3.0

# Whisper is periodically run against the most recent audio.
WAKE_CHECK_INTERVAL = 0.35

# Minimum audio that must be present before Whisper is called.
WAKE_MIN_AUDIO_SECONDS = 0.35

# Maximum amount of time between repeated wake detections.
WAKE_COOLDOWN = 0.8

# Dynamic wake speech detection.
WAKE_NOISE_CALIBRATION_SECONDS = 0.8

WAKE_NOISE_MULTIPLIER = 2.4

WAKE_MIN_DYNAMIC_THRESHOLD = 0.008
WAKE_MAX_DYNAMIC_THRESHOLD = 0.07

# Speech has to exceed the threshold for this many blocks.
WAKE_START_BLOCKS = 2

# How long a wake speech segment may continue before we process it.
WAKE_MAX_SPEECH_SECONDS = 2.5

# Silence after speech before processing the wake buffer.
WAKE_SILENCE_SECONDS = 0.55


# ============================================================
# COMMAND RECORDING SETTINGS
# ============================================================

MAX_RECORD_SECONDS = 15.0

PRE_RECORD_SECONDS = 0.60

SILENCE_DURATION = 1.8

START_SPEECH_BLOCKS = 3

RELEASE_THRESHOLD_FACTOR = 0.70

MIN_COMMAND_AUDIO_SECONDS = 0.45


# ============================================================
# MICROPHONE SETTINGS
# ============================================================

MIN_SPEECH_RMS = 0.008

NOISE_MULTIPLIER = 2.6

# Only accept a command when the captured voice is meaningfully
# louder than the current room noise floor. If it is not louder
# than background sound, we reject the capture and keep listening.
COMMAND_NOISE_MARGIN = 0.004

MIN_DYNAMIC_THRESHOLD = 0.010

MAX_DYNAMIC_THRESHOLD = 0.08


# ============================================================
# WHISPER SETTINGS
# ============================================================

WAKE_BEAM_SIZE = 4

COMMAND_BEAM_SIZE = 5

VAD_MIN_SILENCE_MS = 450

VAD_SPEECH_PAD_MS = 180


# ============================================================
# CONFIDENCE SETTINGS
# ============================================================

MIN_AVG_LOGPROB = -1.20

MAX_NO_SPEECH_PROB = 0.80

MAX_COMPRESSION_RATIO = 2.80


# ============================================================
# MODEL SETTINGS
# ============================================================

WAKE_MODEL_NAME = "base.en"

COMMAND_MODEL_NAME = "medium.en"


# ============================================================
# GLOBAL STATE
# ============================================================

tts_engine = None

tts_lock = threading.Lock()

tts_queue = Queue()

tts_ready = threading.Event()

tts_pending = threading.Event()

is_speaking = threading.Event()

shutdown_event = threading.Event()


# ============================================================
# JAVA -> PYTHON TTS WORKER
# ============================================================

def _tts_worker():

    global tts_engine

    try:

        engine = pyttsx3.init()

        engine.setProperty(
            "rate",
            185
        )

        engine.setProperty(
            "volume",
            1.0
        )

        tts_engine = engine

        tts_ready.set()

        log(
            "Voice output ready!"
        )

    except Exception as exc:

        tts_engine = None

        tts_ready.set()

        log(
            f"TTS initialization failed: {exc}"
        )

        return

    while not shutdown_event.is_set():

        try:

            text = tts_queue.get(
                timeout=0.2
            )

        except Empty:

            continue

        except Exception:

            break

        if text is None:

            tts_queue.task_done()

            break

        try:

            with tts_lock:

                is_speaking.set()

                engine.say(
                    text
                )

                engine.runAndWait()

        except Exception as exc:

            log(
                f"TTS error: {exc}"
            )

        finally:

            is_speaking.clear()

            tts_queue.task_done()

            if tts_queue.empty():

                tts_pending.clear()

    try:

        engine.stop()

    except Exception:

        pass


def initialize_tts():

    thread = threading.Thread(
        target=_tts_worker,
        daemon=True,
        name="Buddy-TTS"
    )

    thread.start()

    tts_ready.wait(
        timeout=10.0
    )


def speak(text):

    if not text:
        return

    clean_text = (
        str(text)
        .replace("\r", " ")
        .replace("\n", " ")
        .replace("\t", " ")
        .strip()
    )

    if not clean_text:
        return

    if tts_engine is None:

        log(
            "TTS unavailable. Cannot speak."
        )

        return

    tts_pending.set()

    tts_queue.put(
        clean_text
    )


# ============================================================
# JAVA -> PYTHON MESSAGE LISTENER
# ============================================================

def start_java_message_listener():

    def listener():

        log(
            "Java -> Python voice listener started."
        )

        while not shutdown_event.is_set():

            try:

                line = sys.stdin.readline()

                if not line:

                    # Java closed stdin.
                    time.sleep(
                        0.1
                    )

                    continue

                line = line.rstrip(
                    "\r\n"
                )

                if not line:

                    continue

                # ------------------------------------------------
                # Java asks Python to speak.
                # ------------------------------------------------

                if line.startswith(
                    "SPEAK\t"
                ):

                    text = line[
                        len("SPEAK\t"):
                    ]

                    if text.strip():

                        log(
                            f"Java says: {text}"
                        )

                        speak(
                            text
                        )

                    continue

                # ------------------------------------------------
                # Java can explicitly stop Buddy.
                # ------------------------------------------------

                if line == "STOP":

                    log(
                        "Java requested voice shutdown."
                    )

                    shutdown_event.set()

                    break

            except Exception as exc:

                log(
                    f"Java message listener error: {exc}"
                )

                time.sleep(
                    0.2
                )

    thread = threading.Thread(
        target=listener,
        daemon=True,
        name="Buddy-Java-Listener"
    )

    thread.start()


# ============================================================
# LOAD MODELS
# ============================================================

log(
    "Loading Buddy voice system..."
)

try:

    wake_model = WhisperModel(
        WAKE_MODEL_NAME,
        device="cpu",
        compute_type="int8"
    )

    log(
        f"Wake model loaded: {WAKE_MODEL_NAME}"
    )

    command_model = WhisperModel(
        COMMAND_MODEL_NAME,
        device="cpu",
        compute_type="int8"
    )

    log(
        f"Command model loaded: {COMMAND_MODEL_NAME}"
    )

except Exception as exc:

    log(
        f"Whisper model loading failed: {exc}"
    )

    raise


initialize_tts()

log(
    "Buddy voice system ready!"
)


# ============================================================
# AUDIO HELPERS
# ============================================================

def rms_level(audio):

    if audio is None or len(audio) == 0:

        return 0.0

    audio = np.asarray(
        audio,
        dtype=np.float32
    )

    return float(
        np.sqrt(
            np.mean(
                np.square(audio)
            )
        )
    )


def peak_level(audio):

    if audio is None or len(audio) == 0:

        return 0.0

    return float(
        np.max(
            np.abs(audio)
        )
    )


def normalize_audio(audio):

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

    peak = peak_level(
        audio
    )

    if peak <= 0.0001:

        return audio

    if peak < 0.35:

        gain = min(
            3.0,
            0.35 / peak
        )

        audio = (
            audio * gain
        )

    audio = np.clip(
        audio,
        -1.0,
        1.0
    )

    return audio.astype(
        np.float32
    )


# ============================================================
# DYNAMIC NOISE CALIBRATION
# ============================================================

def calibrate_noise(
    seconds=0.75
):

    try:

        while (
            is_speaking.is_set()
            or tts_pending.is_set()
        ):

            time.sleep(
                0.05
            )

        log(
            "🎚️ Calibrating microphone noise floor..."
        )

        audio = sd.rec(
            int(
                seconds
                * SAMPLE_RATE
            ),
            samplerate=SAMPLE_RATE,
            channels=CHANNELS,
            dtype="float32"
        )

        sd.wait()

        audio = audio.flatten()

        level = rms_level(
            audio
        )

        threshold = max(
            MIN_DYNAMIC_THRESHOLD,
            level * NOISE_MULTIPLIER,
            level + COMMAND_NOISE_MARGIN
        )

        threshold = min(
            MAX_DYNAMIC_THRESHOLD,
            threshold
        )

        log(
            f"Noise RMS={level:.5f}, "
            f"dynamic threshold={threshold:.5f}"
        )

        return threshold

    except Exception as exc:

        log(
            f"Noise calibration error: {exc}"
        )

        return MIN_DYNAMIC_THRESHOLD


def calibrate_wake_noise():

    try:

        while (
            is_speaking.is_set()
            or tts_pending.is_set()
        ):

            time.sleep(
                0.05
            )

        audio = sd.rec(
            int(
                WAKE_NOISE_CALIBRATION_SECONDS
                * SAMPLE_RATE
            ),
            samplerate=SAMPLE_RATE,
            channels=CHANNELS,
            dtype="float32"
        )

        sd.wait()

        audio = audio.flatten()

        level = rms_level(
            audio
        )

        threshold = max(
            WAKE_MIN_DYNAMIC_THRESHOLD,
            level * WAKE_NOISE_MULTIPLIER
        )

        threshold = min(
            WAKE_MAX_DYNAMIC_THRESHOLD,
            threshold
        )

        log(
            f"Wake noise RMS={level:.5f}, "
            f"wake threshold={threshold:.5f}"
        )

        return threshold

    except Exception as exc:

        log(
            f"Wake noise calibration error: {exc}"
        )

        return WAKE_MIN_DYNAMIC_THRESHOLD


# ============================================================
# TEXT CLEANING
# ============================================================

def clean_transcription(text):

    if text is None:

        return ""

    text = text.strip()

    text = re.sub(
        r"\s+",
        " ",
        text
    )

    return text.strip(
        " \t\r\n.,!?;:"
    )


def normalize_for_wake(text):

    if not text:

        return ""

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

    return normalized


def contains_wake_word(text):

    normalized = normalize_for_wake(
        text
    )

    if not normalized:

        return False

    tokens = normalized.split()

    # Primary recognition.
    if "buddy" in tokens:

        return True

    # Whisper commonly turns "Buddy" into "body".
    if "body" in tokens:

        return True

    return False


# ============================================================
# WAKE WORD TRANSCRIPTION
# ============================================================

def transcribe_wake_audio(audio):

    if audio is None:

        return ""

    if len(audio) == 0:

        return ""

    try:

        segments, info = (
            wake_model.transcribe(
                audio,
                language="en",
                beam_size=WAKE_BEAM_SIZE,
                vad_filter=True,
                vad_parameters={
                    "min_silence_duration_ms": 250
                },
                condition_on_previous_text=False,
                temperature=0.0,
                initial_prompt="Buddy"
            )
        )

        heard_parts = []

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

            log(
                "Wake Whisper segment: "
                f"'{text}' "
                f"(no_speech={no_speech_prob:.2f}, "
                f"logprob={avg_logprob:.2f})"
            )

            if no_speech_prob > 0.90:

                continue

            # Wake recognition should be slightly more tolerant
            # than command recognition.
            if avg_logprob < -2.20:

                continue

            heard_parts.append(
                text
            )

        heard = " ".join(
            heard_parts
        )

        heard = clean_transcription(
            heard
        )

        if heard:

            log(
                f"👂 Wake recognizer heard: "
                f"'{heard}'"
            )

        return heard

    except Exception as exc:

        log(
            f"Wake recognition error: {exc}"
        )

        return ""


# ============================================================
# CONTINUOUS WAKE-WORD LISTENER
# ============================================================

def listen_for_wake_word():

    # --------------------------------------------------------
    # Wait until Buddy has completely stopped speaking.
    # --------------------------------------------------------

    while (
        is_speaking.is_set()
        or tts_pending.is_set()
    ):

        time.sleep(
            0.05
        )

    # --------------------------------------------------------
    # Dynamically calibrate the microphone.
    # --------------------------------------------------------

    threshold = calibrate_wake_noise()

    # --------------------------------------------------------
    # Rolling microphone buffer.
    # --------------------------------------------------------

    max_buffer_blocks = max(
        1,
        int(
            WAKE_BUFFER_SECONDS
            / BLOCK_DURATION
        )
    )

    audio_buffer = deque(
        maxlen=max_buffer_blocks
    )

    speech_blocks = 0

    speech_started = False

    speech_elapsed = 0.0

    silence_elapsed = 0.0

    last_check = 0.0

    # --------------------------------------------------------
    # Audio callback state.
    # --------------------------------------------------------

    callback_error = None

    def callback(
        indata,
        frames,
        time_info,
        status
    ):

        nonlocal speech_blocks
        nonlocal speech_started
        nonlocal speech_elapsed
        nonlocal silence_elapsed
        nonlocal callback_error

        try:

            if is_speaking.is_set():

                return

            audio_block = (
                indata
                .copy()
                .reshape(-1)
                .astype(np.float32)
            )

            level = rms_level(
                audio_block
            )

            audio_buffer.append(
                audio_block
            )

            if level >= threshold:

                speech_blocks += 1

                if (
                    not speech_started
                    and speech_blocks
                    >= WAKE_START_BLOCKS
                ):

                    speech_started = True

                    speech_elapsed = 0.0

                    silence_elapsed = 0.0

                    log(
                        "🎙️ Wake speech detected."
                    )

                if speech_started:

                    speech_elapsed += (
                        BLOCK_DURATION
                    )

                    silence_elapsed = 0.0

            else:

                if speech_started:

                    speech_elapsed += (
                        BLOCK_DURATION
                    )

                    silence_elapsed += (
                        BLOCK_DURATION
                    )

                else:

                    speech_blocks = 0

        except Exception as exc:

            callback_error = exc

    # --------------------------------------------------------
    # Start continuous microphone stream.
    # --------------------------------------------------------

    try:

        with sd.InputStream(
            samplerate=SAMPLE_RATE,
            channels=CHANNELS,
            dtype="float32",
            callback=callback,
            blocksize=BLOCK_SIZE
        ):

            start_time = time.monotonic()

            while not shutdown_event.is_set():

                if is_speaking.is_set():

                    return False

                if callback_error is not None:

                    log(
                        f"Wake microphone error: "
                        f"{callback_error}"
                    )

                    return False

                sd.sleep(
                    int(
                        BLOCK_DURATION
                        * 1000
                    )
                )

                now = time.monotonic()

                # ------------------------------------------------
                # Don't call Whisper constantly.
                # ------------------------------------------------

                if (
                    now - last_check
                    < WAKE_CHECK_INTERVAL
                ):

                    continue

                last_check = now

                # ------------------------------------------------
                # No speech yet.
                # ------------------------------------------------

                if not speech_started:

                    continue

                # ------------------------------------------------
                # Enough speech has been collected.
                # ------------------------------------------------

                should_check = (
                    speech_elapsed
                    >= WAKE_MIN_AUDIO_SECONDS
                    and (
                        silence_elapsed
                        >= WAKE_SILENCE_SECONDS
                        or speech_elapsed
                        >= WAKE_MAX_SPEECH_SECONDS
                    )
                )

                if not should_check:

                    continue

                # ------------------------------------------------
                # Copy rolling buffer.
                # ------------------------------------------------

                audio = np.concatenate(
                    list(audio_buffer)
                )

                audio = (
                    audio
                    - np.mean(audio)
                )

                audio = normalize_audio(
                    audio
                )

                if audio is None:

                    speech_started = False
                    speech_blocks = 0
                    speech_elapsed = 0.0
                    silence_elapsed = 0.0

                    continue

                # ------------------------------------------------
                # Whisper.
                # ------------------------------------------------

                heard = transcribe_wake_audio(
                    audio
                )

                # ------------------------------------------------
                # Wake detected.
                # ------------------------------------------------

                if contains_wake_word(
                    heard
                ):

                    log(
                        "🔥 Wake word 'Buddy' detected."
                    )

                    return True

                # ------------------------------------------------
                # No wake word.
                #
                # Reset the speech detector but retain the
                # rolling microphone architecture.
                # ------------------------------------------------

                speech_started = False

                speech_blocks = 0

                speech_elapsed = 0.0

                silence_elapsed = 0.0

                audio_buffer.clear()

                # Prevent immediate duplicate processing.
                time.sleep(
                    0.05
                )

                # ------------------------------------------------
                # Recalibrate occasionally after speech.
                # ------------------------------------------------

                if (
                    time.monotonic()
                    - start_time
                    > 30.0
                ):

                    threshold = (
                        calibrate_wake_noise()
                    )

                    start_time = (
                        time.monotonic()
                    )

    except Exception as exc:

        log(
            f"Wake microphone stream error: {exc}"
        )

        return False


# ============================================================
# COMMAND RECORDING
# ============================================================

def record_command():

    while (
        is_speaking.is_set()
        or tts_pending.is_set()
    ):

        time.sleep(
            0.05
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

    speech_candidate_blocks = 0

    speech_audio_seconds = 0.0

    silence_time = 0.0

    elapsed = 0.0

    interrupted_by_tts = False

    callback_error = None

    def callback(
        indata,
        frames,
        time_info,
        status
    ):

        nonlocal speech_started
        nonlocal speech_candidate_blocks
        nonlocal speech_audio_seconds
        nonlocal silence_time
        nonlocal interrupted_by_tts
        nonlocal callback_error

        try:

            if is_speaking.is_set():

                interrupted_by_tts = True

                return

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

                if level >= threshold:

                    speech_candidate_blocks += 1

                else:

                    speech_candidate_blocks = 0

                if (
                    speech_candidate_blocks
                    >= START_SPEECH_BLOCKS
                ):

                    speech_started = True

                    speech_audio_seconds = 0.0

                    recorded_audio.extend(
                        list(pre_buffer)
                    )

                    silence_time = 0.0

                    log(
                        "🎙️ Command speech detected."
                    )

            else:

                recorded_audio.append(
                    audio_block
                )

                speech_audio_seconds += (
                    BLOCK_DURATION
                )

                release_threshold = (
                    threshold
                    * RELEASE_THRESHOLD_FACTOR
                )

                if (
                    level
                    >= release_threshold
                ):

                    silence_time = 0.0

                else:

                    silence_time += (
                        BLOCK_DURATION
                    )

        except Exception as exc:

            callback_error = exc

    try:

        with sd.InputStream(
            samplerate=SAMPLE_RATE,
            channels=CHANNELS,
            dtype="float32",
            callback=callback,
            blocksize=BLOCK_SIZE
        ):

            while (
                elapsed
                < MAX_RECORD_SECONDS
            ):

                if is_speaking.is_set():

                    interrupted_by_tts = True

                    break

                if callback_error is not None:

                    log(
                        f"Command microphone error: "
                        f"{callback_error}"
                    )

                    return None

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

                    break

    except Exception as exc:

        log(
            f"Microphone recording error: {exc}"
        )

        return None

    if interrupted_by_tts:

        return None

    if not speech_started:

        return None

    if (
        speech_audio_seconds
        < MIN_COMMAND_AUDIO_SECONDS
    ):

        return None

    if not recorded_audio:

        return None

    audio = np.concatenate(
        recorded_audio,
        axis=0
    )

    audio = (
        audio
        - np.mean(audio)
    )

    audio = normalize_audio(
        audio
    )

    if audio is None:

        return None

    signal_rms = rms_level(audio)

    if signal_rms < MIN_SPEECH_RMS:

        return None

    if signal_rms < max(MIN_SPEECH_RMS, threshold * 1.35):

        log(
            f"Voice rejected: signal RMS {signal_rms:.5f} did not exceed the noise floor by a safe margin."
        )

        return None

    return audio.flatten()


# ============================================================
# SPEECH TO TEXT
# ============================================================

def speech_to_text(audio):

    if audio is None:

        return ""

    if len(audio) == 0:

        return ""

    try:

        segments, info = (
            command_model.transcribe(
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
                    "Commands can include opening "
                    "applications, closing applications, "
                    "creating files or folders, moving "
                    "files, copying files, downloading, "
                    "searching, playing media, renaming, "
                    "deleting, and normal conversation."
                )
            )
        )

        accepted = []

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

            if (
                no_speech_prob
                > MAX_NO_SPEECH_PROB
            ):

                continue

            if (
                avg_logprob
                < MIN_AVG_LOGPROB
            ):

                continue

            if (
                compression_ratio
                > MAX_COMPRESSION_RATIO
            ):

                continue

            accepted.append(
                text
            )

        result = " ".join(
            accepted
        )

        return clean_transcription(
            result
        ).lower()

    except Exception as exc:

        log(
            f"Speech recognition error: {exc}"
        )

        return ""


# ============================================================
# SEND COMMAND TO JAVA
# ============================================================

def send_command_to_java(
    command
):

    if not BRIDGE_MODE:

        log(
            f"VOICE_COMMAND: {command}"
        )

        return

    if not command:

        return

    print(
        f"VOICE_COMMAND\t{command}",
        file=sys.stdout,
        flush=True
    )


# ============================================================
# MAIN VOICE LOOP
# ============================================================

def main():

    start_java_message_listener()

    log(
        "🎤 Buddy voice loop starting..."
    )

    log(
        "🎤 Listening continuously for wake word 'Buddy'..."
    )

    try:

        while not shutdown_event.is_set():

            # ------------------------------------------------
            # WAKE WORD MODE
            # ------------------------------------------------

            wake_detected = (
                listen_for_wake_word()
            )

            if not wake_detected:

                if shutdown_event.is_set():

                    break

                continue

            # ------------------------------------------------
            # WAKE DETECTED
            # ------------------------------------------------

            log(
                "🔥 Buddy activated."
            )

            speak(
                "Yes? How can I help you?"
            )

            # ------------------------------------------------
            # Wait until acknowledgement has completely finished.
            # ------------------------------------------------

            while (
                is_speaking.is_set()
                or tts_pending.is_set()
            ):

                time.sleep(
                    0.05
                )

            time.sleep(
                WAKE_COOLDOWN
            )

            # ------------------------------------------------
            # CONTINUOUS COMMAND MODE
            # ------------------------------------------------

            while not shutdown_event.is_set():

                while (
                    is_speaking.is_set()
                    or tts_pending.is_set()
                ):

                    time.sleep(
                        0.05
                    )

                log(
                    "🎤 Listening for command..."
                )

                audio = record_command()

                if audio is None:

                    continue

                log(
                    "🧠 Processing..."
                )

                command = speech_to_text(
                    audio
                )

                if not command:

                    speak(
                        "Sorry, I didn't catch that."
                    )

                    continue

                log(
                    f"Buddy heard: {command}"
                )

                send_command_to_java(
                    command
                )

                # ------------------------------------------------
                # IMPORTANT:
                #
                # We stay in command mode.
                #
                # Java controls the response through:
                #
                # SPEAK\t<text>
                #
                # We do not require "Buddy" again for every
                # command.
                # ------------------------------------------------

    except KeyboardInterrupt:

        log(
            "Buddy voice system stopped."
        )

    except Exception as exc:

        log(
            f"Buddy voice system error: {exc}"
        )

    finally:

        shutdown_event.set()

        try:

            tts_queue.put(
                None
            )

        except Exception:

            pass


# ============================================================
# ENTRY POINT
# ============================================================

if __name__ == "__main__":

    main()