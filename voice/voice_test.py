import time

import numpy as np
import sounddevice as sd


SAMPLE_RATE = 16000
CHANNELS = 1
BLOCK_SIZE = 1600


print("🎤 MIC TEST")
print("Speak normally, then speak louder and farther away.")
print("Press Ctrl+C to stop.\n")


def rms_level(audio):
    audio = np.asarray(
        audio,
        dtype=np.float32
    )

    if len(audio) == 0:
        return 0.0

    return float(
        np.sqrt(
            np.mean(
                np.square(audio)
            )
        )
    )


def callback(
        indata,
        frames,
        time_info,
        status):

    if status:
        print(
            f"\nAudio status: {status}"
        )

    audio = (
        indata
        .copy()
        .reshape(-1)
    )

    rms = rms_level(
        audio
    )

    peak = float(
        np.max(
            np.abs(audio)
        )
    )

    print(
        f"\rRMS: {rms:.4f} | "
        f"Peak: {peak:.4f}",
        end=""
    )


try:

    with sd.InputStream(
            samplerate=SAMPLE_RATE,
            channels=CHANNELS,
            dtype="float32",
            blocksize=BLOCK_SIZE,
            callback=callback
    ):

        while True:

            sd.sleep(100)

except KeyboardInterrupt:

    print(
        "\n\nMIC TEST STOPPED."
    )

except Exception as exc:

    print(
        f"\n\nMIC ERROR: {exc}"
    )
