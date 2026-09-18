import sounddevice as sd
import numpy as np


print("🎤 MIC TEST: Speak now...")


def callback(indata, frames, time, status):

    if status:
        print(status)

    level = np.max(np.abs(indata))

    print(
        f"\rAudio Level: {level:.4f}",
        end=""
    )


with sd.InputStream(
        channels=1,
        dtype="float32",
        callback=callback
):

    while True:

        sd.sleep(100)