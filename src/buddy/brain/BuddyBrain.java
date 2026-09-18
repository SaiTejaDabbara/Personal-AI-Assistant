package buddy.brain;

import buddy.commands.Commands;
import buddy.ui.BuddyState;
import buddy.ui.BuddyUI;
import buddy.voice.BuddyVoiceBridge;
import buddy.web.BuddyWebServer;

import java.util.Scanner;

public class BuddyBrain {

    private final Commands commands;
    private final BuddyVoiceBridge voiceBridge;
    private final BuddyWebServer webServer;
    private final BuddyUI ui;
    private final boolean autostart;

    public BuddyBrain() {
        this(false);
    }

    public BuddyBrain(boolean autostart) {
        this.autostart = autostart;
        this.commands = new Commands();
        this.voiceBridge = new BuddyVoiceBridge(commands);
        this.webServer = new BuddyWebServer(commands);
        this.ui = new BuddyUI(commands);
    }

    public String processCommand(String userCommand) {
        if (userCommand == null || userCommand.trim().isEmpty()) {
            return "Please tell me what you want me to do.";
        }

        String trimmed = userCommand.trim();
        if (ui != null) {
            ui.showUserMessage(trimmed);
            ui.setState(BuddyState.THINKING);
            ui.showBrainStatus("UNDERSTANDING", "Analyzing request...");
        }

        try {
            String response = commands.process(trimmed);
            if (ui != null) {
                ui.showBuddyResponse(response);
                ui.setState(BuddyState.READY);
                ui.showBrainStatus("READY", "Listening for the next command.");
            }
            return response;
        } catch (Exception e) {
            String message = "I hit a problem while processing that request.";
            if (ui != null) {
                ui.showError(message + " " + e.getMessage());
                ui.setState(BuddyState.ERROR);
            }
            return message;
        }
    }

    public void start() {
        if (ui != null) {
            ui.show();
            if (autostart) {
                ui.minimize();
            }
            ui.setState(BuddyState.READY);
            ui.showBrainStatus("READY", "Buddy is online and listening.");
        }

        try {
            webServer.start();
            System.out.println("Buddy web dashboard available at http://localhost:8080");
            if (ui != null) {
                ui.addActivity("WEB", "Server", "Buddy web dashboard listening on localhost:8080", BuddyState.READY);
            }
        } catch (Exception e) {
            System.err.println("Buddy web UI could not start: " + e.getMessage());
            if (ui != null) {
                ui.showError("Web dashboard unavailable: " + e.getMessage());
            }
        }

        try {
            voiceBridge.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    voiceBridge.close();
                } catch (Exception ignored) {
                }
            }));
            if (ui != null) {
                ui.addActivity("VOICE", "Bridge", "Voice listener started successfully", BuddyState.READY);
            }
        } catch (Exception e) {
            System.err.println("Voice bridge could not start: " + e.getMessage());
            if (ui != null) {
                ui.showError("Voice bridge unavailable: " + e.getMessage());
            }
        }

        Scanner scanner = new Scanner(System.in);
        System.out.println("Buddy is ready!");
        System.out.println("Type a command. Type 'exit' to stop Buddy.");

        while (true) {
            System.out.print("You: ");
            if (!scanner.hasNextLine()) {
                break;
            }

            String userCommand = scanner.nextLine();
            if (userCommand.equalsIgnoreCase("exit")) {
                System.out.println("Buddy: Goodbye!");
                break;
            }

            if (userCommand.trim().isEmpty()) {
                continue;
            }

            processCommand(userCommand);
        }

        scanner.close();

        try {
            voiceBridge.close();
        } catch (Exception ignored) {
        }
        if (ui != null) {
            ui.shutdown();
        }
    }
}