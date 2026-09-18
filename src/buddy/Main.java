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

                System.out.println(autostart ? "Starting Buddy in autostart mode..." : "Starting Buddy...");

                BuddyBrain buddy = new BuddyBrain(autostart);
                buddy.start();
        }

        private static void printHelp() {
                System.out.println("Buddy startup options:");
                System.out.println("  --autostart            Launch Buddy with the startup/minimized behavior");
                System.out.println("  --install-autostart    Register Buddy in the Windows Run key for login startup");
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