package buddy.voice;

import buddy.commands.Commands;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public class BuddyVoiceBridge implements AutoCloseable {

    private static final String VOICE_COMMAND_PREFIX =
            "VOICE_COMMAND\t";

    private static final String SPEAK_PREFIX =
            "SPEAK\t";

    private final Commands commands;

    private Process process;

    private Thread stdoutThread;
    private Thread stderrThread;

    private volatile boolean running;

    public BuddyVoiceBridge(Commands commands) {

        if (commands == null) {
            throw new IllegalArgumentException(
                    "commands cannot be null"
            );
        }

        this.commands = commands;
    }

    // ============================================================
    // START
    // ============================================================

    public synchronized void start() throws IOException {

        if (
                running
                        && process != null
                        && process.isAlive()
        ) {
            return;
        }

        String python =
                System.getenv()
                        .getOrDefault(
                                "BUDDY_PYTHON",
                                "python"
                        );

        String script =
                System.getenv()
                        .getOrDefault(
                                "BUDDY_VOICE_SCRIPT",
                                Path.of(
                                        "voice",
                                        "buddy_voice_high_accuracy_bridge.py"
                                ).toString()
                        );

        System.out.println(
                "Starting Buddy voice process..."
        );

        System.out.println(
                "Python: " + python
        );

        System.out.println(
                "Voice script: " + script
        );

        /*
         * Verify the script exists before starting Python.
         */
        Path scriptPath =
                Path.of(script).toAbsolutePath();

        System.out.println(
                "Voice script absolute path: "
                        + scriptPath
        );

        if (!java.nio.file.Files.exists(scriptPath)) {

            throw new IOException(
                    "Voice script not found: "
                            + scriptPath
            );
        }

        /*
         * Start Python.
         *
         * stdout:
         *     Machine-readable VOICE_COMMAND messages
         *
         * stderr:
         *     Human-readable Python diagnostics
         */
        process =
                new ProcessBuilder(
                        python,
                        scriptPath.toString(),
                        "--bridge"
                )
                        .redirectErrorStream(false)
                        .start();

        running = true;

        startStdoutReader();
        startStderrReader();

        System.out.println(
                "Buddy voice bridge started."
        );

        /*
         * Give Python a moment to start its initialization
         * so that startup errors are visible immediately.
         */
        Thread startupMonitor =
                new Thread(
                        () -> {

                            try {

                                Thread.sleep(1000);

                                if (
                                        running
                                                && process != null
                                                && !process.isAlive()
                                ) {

                                    int exitCode =
                                            process.exitValue();

                                    System.out.println(
                                            "Voice process exited during startup. "
                                                    + "Exit code: "
                                                    + exitCode
                                    );
                                }

                            } catch (InterruptedException e) {

                                Thread.currentThread()
                                        .interrupt();
                            }
                        },
                        "Buddy-Voice-Startup-Monitor"
                );

        startupMonitor.setDaemon(true);
        startupMonitor.start();
    }

    // ============================================================
    // PYTHON STDOUT
    // ============================================================

    private void startStdoutReader() {

        stdoutThread =
                new Thread(
                        () -> {

                            try (
                                    BufferedReader reader =
                                            new BufferedReader(
                                                    new InputStreamReader(
                                                            process.getInputStream(),
                                                            StandardCharsets.UTF_8
                                                    )
                                            )
                            ) {

                                String line;

                                while (
                                        running
                                                && (line = reader.readLine()) != null
                                ) {

                                    if (line == null) {
                                        continue;
                                    }

                                    line = line.trim();

                                    if (line.isBlank()) {
                                        continue;
                                    }

                                    /*
                                     * Python must use stdout only for
                                     * machine-readable bridge messages.
                                     */
                                    if (
                                            line.startsWith(
                                                    VOICE_COMMAND_PREFIX
                                            )
                                    ) {

                                        String command =
                                                line.substring(
                                                                VOICE_COMMAND_PREFIX.length()
                                                        )
                                                        .trim();

                                        if (!command.isBlank()) {

                                            handleVoiceCommand(
                                                    command
                                            );
                                        }

                                    } else {

                                        /*
                                         * Unexpected stdout from Python.
                                         *
                                         * This is useful because accidental
                                         * print() calls in Python can break
                                         * the bridge protocol.
                                         */
                                        System.out.println(
                                                "[Voice stdout] "
                                                        + line
                                        );
                                    }
                                }

                            } catch (IOException e) {

                                if (running) {

                                    System.out.println(
                                            "Voice input error: "
                                                    + e.getMessage()
                                    );
                                }
                            }

                        },
                        "Buddy-Voice-Output"
                );

        stdoutThread.setDaemon(true);
        stdoutThread.start();
    }

    // ============================================================
    // PYTHON STDERR / DIAGNOSTICS
    // ============================================================

    private void startStderrReader() {

        stderrThread =
                new Thread(
                        () -> {

                            try (
                                    BufferedReader reader =
                                            new BufferedReader(
                                                    new InputStreamReader(
                                                            process.getErrorStream(),
                                                            StandardCharsets.UTF_8
                                                    )
                                            )
                            ) {

                                String line;

                                while (
                                        running
                                                && (line = reader.readLine()) != null
                                ) {

                                    if (line == null) {
                                        continue;
                                    }

                                    line = line.trim();

                                    if (line.isBlank()) {
                                        continue;
                                    }

                                    /*
                                     * IMPORTANT:
                                     *
                                     * During debugging, show EVERYTHING
                                     * coming from Python stderr.
                                     *
                                     * This includes:
                                     *
                                     * 🎤 Listening for wake word...
                                     * 🔥 Buddy activated.
                                     * 🎤 Listening for command...
                                     * wake recognition messages
                                     * microphone errors
                                     * model errors
                                     * TTS errors
                                     */
                                    System.out.println(
                                            line
                                    );
                                }

                            } catch (IOException e) {

                                if (running) {

                                    System.out.println(
                                            "Voice diagnostic error: "
                                                    + e.getMessage()
                                    );
                                }
                            }

                        },
                        "Buddy-Voice-Diagnostics"
                );

        stderrThread.setDaemon(true);
        stderrThread.start();
    }

    // ============================================================
    // HANDLE VOICE COMMAND
    // ============================================================

    private void handleVoiceCommand(
            String command
    ) {

        System.out.println();

        System.out.println(
                "You: " + command
        );

        /*
         * Immediate acknowledgement.
         *
         * This is sent to Python BEFORE the potentially expensive
         * Commands.process() operation.
         */
        sendToVoiceProcess(
                SPEAK_PREFIX
                        + buildAcknowledgement(command)
        );

        try {

            String response =
                    commands.process(
                            command
                    );

            if (
                    response == null
                            || response.isBlank()
            ) {

                response = "Done.";
            }

            System.out.println(
                    "Buddy: " + response
            );

            /*
             * Send final response to Python TTS.
             */
            sendToVoiceProcess(
                    SPEAK_PREFIX
                            + sanitizeForTransport(
                            response
                    )
            );

        } catch (Exception e) {

            System.out.println(
                    "Buddy error: "
                            + e.getMessage()
            );

            sendToVoiceProcess(
                    SPEAK_PREFIX
                            + "Sorry boss, I had trouble with that."
            );
        }
    }

    // ============================================================
    // NATURAL ACKNOWLEDGEMENT
    // ============================================================

    private String buildAcknowledgement(
            String command
    ) {

        if (
                command == null
                        || command.isBlank()
        ) {

            return "Yes boss, I'm on it.";
        }

        String value =
                command
                        .toLowerCase()
                        .trim();

        /*
         * Opening
         */
        if (
                containsAny(
                        value,
                        "open",
                        "launch",
                        "start",
                        "run",
                        "visit"
                )
        ) {

            return "Yes boss, I'm opening that now.";
        }

        /*
         * Closing
         */
        if (
                containsAny(
                        value,
                        "close",
                        "quit",
                        "terminate",
                        "kill"
                )
        ) {

            return "Yes boss, I'm closing that now.";
        }

        /*
         * Search
         */
        if (
                containsAny(
                        value,
                        "search",
                        "google",
                        "lookup",
                        "look up",
                        "find",
                        "browse",
                        "news"
                )
        ) {

            return "Yes boss, I'm searching for that now.";
        }

        /*
         * File operations
         */
        if (
                containsAny(
                        value,
                        "create",
                        "make",
                        "new",
                        "folder"
                )
        ) {

            return "Yes boss, I'm creating that now.";
        }

        if (
                containsAny(
                        value,
                        "move",
                        "copy",
                        "rename"
                )
        ) {

            return "Yes boss, I'm handling that now.";
        }

        if (
                containsAny(
                        value,
                        "delete",
                        "remove"
                )
        ) {

            return "Yes boss, I'm taking care of that now.";
        }

        /*
         * Download
         */
        if (
                containsAny(
                        value,
                        "download"
                )
        ) {

            return "Yes boss, I'm downloading that now.";
        }

        /*
         * Media
         */
        if (
                containsAny(
                        value,
                        "play",
                        "stop",
                        "pause",
                        "resume"
                )
        ) {

            return "Yes boss, I'm handling that now.";
        }

        /*
         * Questions / normal conversation
         */
        if (
                value.endsWith("?")
                        || value.startsWith("what ")
                        || value.startsWith("why ")
                        || value.startsWith("how ")
                        || value.startsWith("who ")
                        || value.startsWith("when ")
                        || value.startsWith("where ")
                        || value.startsWith("can you ")
                        || value.startsWith("could you ")
                        || value.startsWith("tell me ")
        ) {

            return "Yes boss, I'm on it.";
        }

        return "Yes boss, I'm on it.";
    }

    // ============================================================
    // TEXT MATCH HELPER
    // ============================================================

    private boolean containsAny(
            String text,
            String... values
    ) {

        for (String value : values) {

            if (
                    value != null
                            && text.contains(value)
            ) {

                return true;
            }
        }

        return false;
    }

    // ============================================================
    // SANITIZE TTS TEXT
    // ============================================================

    private String sanitizeForTransport(
            String text
    ) {

        if (
                text == null
                        || text.isBlank()
        ) {

            return "Done.";
        }

        return text
                .replace(
                        "\r",
                        " "
                )
                .replace(
                        "\n",
                        " "
                )
                .replace(
                        "\t",
                        " "
                )
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }

    // ============================================================
    // SEND TEXT TO PYTHON
    // ============================================================

    private synchronized void sendToVoiceProcess(
            String line
    ) {

        if (
                !running
                        || process == null
                        || !process.isAlive()
                        || line == null
                        || line.isBlank()
        ) {

            return;
        }

        try {

            process.getOutputStream()
                    .write(
                            (
                                    line
                                            + System.lineSeparator()
                            )
                                    .getBytes(
                                            StandardCharsets.UTF_8
                                    )
                    );

            process.getOutputStream()
                    .flush();

        } catch (IOException e) {

            if (running) {

                System.out.println(
                        "Voice speech error: "
                                + e.getMessage()
                );
            }
        }
    }

    // ============================================================
    // RUNNING CHECK
    // ============================================================

    public boolean isRunning() {

        return running
                && process != null
                && process.isAlive();
    }

    // ============================================================
    // CLOSE
    // ============================================================

    @Override
    public synchronized void close() {

        if (!running && process == null) {
            return;
        }

        running = false;

        /*
         * Tell Python to stop gracefully first.
         */
        if (
                process != null
                        && process.isAlive()
        ) {

            try {

                process.getOutputStream()
                        .write(
                                (
                                        "STOP"
                                                + System.lineSeparator()
                                )
                                        .getBytes(
                                                StandardCharsets.UTF_8
                                        )
                        );

                process.getOutputStream()
                        .flush();

            } catch (IOException ignored) {
            }
        }

        if (process != null) {

            process.destroy();

            try {

                if (
                        !process.waitFor(
                                1500,
                                TimeUnit.MILLISECONDS
                        )
                ) {

                    process.destroyForcibly();

                    process.waitFor(
                            1000,
                            TimeUnit.MILLISECONDS
                    );
                }

            } catch (InterruptedException e) {

                Thread.currentThread()
                        .interrupt();

                process.destroyForcibly();
            }

            process = null;
        }

        System.out.println(
                "Buddy voice bridge stopped."
        );
    }
}