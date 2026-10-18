package buddy;

import buddy.brain.BuddyBrain;

import java.nio.file.Path;

public class Main {

        public static void main(String[] args) {
                if (args != null) {
                        for (String arg : args) {
                                String normalized = arg == null ? "" : arg.trim().toLowerCase();
                                if (normalized.equals("--install-autostart")) {
                                        installWindowsAutostart();
                                        return;
                                }
                                if (normalized.equals("--help") || normalized.equals("-h")) {
                                        printHelp();
                                        return;
                                }
                        }
                }

                boolean autostart = args != null && java.util.Arrays.stream(args)
                                .anyMatch(arg -> "--autostart".equalsIgnoreCase(arg == null ? "" : arg.trim()));

                String commandFromArgs = extractCommandArg(args);
                if (commandFromArgs != null) {
                        System.out.println(runCommandOnce(commandFromArgs));
                        return;
                }

                System.out.println(autostart ? "Starting Buddy in autostart mode..." : "Starting Buddy...");

                BuddyBrain buddy = new BuddyBrain(autostart);
                buddy.start();
        }

        public static String runCommandOnce(String userCommand) {
                BuddyBrain buddy = new BuddyBrain(false);
                return buddy.processCommand(userCommand);
        }

        private static String extractCommandArg(String[] args) {
                if (args == null || args.length == 0) {
                        return null;
                }

                for (int i = 0; i < args.length; i++) {
                        String arg = args[i];
                        if (arg == null) {
                                continue;
                        }

                        String trimmed = arg.trim();
                        if (trimmed.isEmpty()) {
                                continue;
                        }

                        String normalized = trimmed.toLowerCase();
                        if (normalized.equals("--command") || normalized.equals("--cmd")
                                        || normalized.equals("--once")) {
                                if (i + 1 < args.length && args[i + 1] != null && !args[i + 1].trim().isEmpty()) {
                                        return args[i + 1].trim();
                                }
                                return null;
                        }

                        if (normalized.startsWith("--")) {
                                continue;
                        }

                        return trimmed;
                }

                return null;
        }

        private static void printHelp() {
                System.out.println("Buddy startup options:");
                System.out.println("  --autostart            Launch Buddy with the startup/minimized behavior");
                System.out.println("  --install-autostart    Register Buddy in the Windows Run key for login startup");
                System.out.println("  --command <text>       Run a single command without opening the interactive UI");
                System.out.println("  --cmd <text>           Alias for --command");
                System.out.println("  --once <text>          Alias for --command");
                System.out.println("  " + "Example: java -jar Buddy.jar --command \"what is the date\"");
        }

        private static void installWindowsAutostart() {
                Path projectRoot = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
                Path scriptPath = projectRoot.resolve("buddy-startup.cmd").toAbsolutePath().normalize();

                if (!java.nio.file.Files.exists(scriptPath)) {
                        System.err.println("Could not find startup script at: " + scriptPath);
                        return;
                }

                String appData = System.getenv("APPDATA");
                if (appData == null || appData.isBlank()) {
                        System.err.println("APPDATA is not set; cannot register Buddy for Windows startup.");
                        return;
                }

                Path startupDir = Path.of(appData, "Microsoft", "Windows", "Start Menu", "Programs", "Startup")
                                .toAbsolutePath().normalize();

                try {
                        java.nio.file.Files.createDirectories(startupDir);
                        Path startupScript = startupDir.resolve("Buddy-startup.cmd");
                        java.nio.file.Files.copy(
                                        scriptPath,
                                        startupScript,
                                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                        System.out.println("Buddy autostart is registered successfully.");
                        System.out.println("Startup folder: " + startupDir);
                        System.out.println("Command script: " + startupScript);
                } catch (Exception e) {
                        System.err.println("Autostart setup could not be completed: " + e.getMessage());
                }
        }
}