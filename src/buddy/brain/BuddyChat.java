package buddy.brain;

import buddy.memory.MemoryManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

public class BuddyChat {

    private static final String OLLAMA_URL =
            "http://localhost:11434/api/generate";

    private static final String MODEL =
            "qwen2.5:0.5b";

    private final MemoryManager memoryManager;
    private final HttpClient httpClient;
    private final ConversationContext conversationContext;

    public BuddyChat() {

        memoryManager =
                new MemoryManager();

        httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(3))
                        .build();

        conversationContext =
                new ConversationContext();
    }

    public String chat(String userMessage) {

        long startTime = System.currentTimeMillis();

        if (userMessage == null || userMessage.isBlank()) {
            return "I'm listening.";
        }

        String message = userMessage.trim();

        String instant = tryInstantReply(message);

        if (instant != null) {
            conversationContext.setLastUserMessage(message);
            conversationContext.setLastAction("Conversation");

            System.out.println(
                    "BuddyChat fast reply: "
                            + (System.currentTimeMillis() - startTime)
                            + " ms"
            );

            return instant;
        }

        conversationContext.setLastUserMessage(message);

        Map<String, String> userMemory =
                memoryManager.loadUserMemory();

        StringBuilder memoryText =
                new StringBuilder();

        if (userMemory != null && !userMemory.isEmpty()) {

            for (Map.Entry<String, String> entry : userMemory.entrySet()) {

                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }

                memoryText.append(entry.getKey())
                        .append(": ")
                        .append(entry.getValue())
                        .append("\n");
            }
        }

        if (memoryText.isEmpty()) {
            memoryText.append("none");
        }

        String contextText =
                conversationContext.getContextText();

        if (contextText == null || contextText.isBlank()) {
            contextText = "none";
        }

        contextText = compactContext(contextText);

        String compactMemory =
                compactMemory(memoryText.toString());

        String prompt =
                "You are Buddy, a helpful personal AI desktop assistant.\n\n"
                        + "Answer naturally, briefly, and directly.\n"
                        + "Use memory or previous context only when it clearly helps "
                        + "answer the current message.\n"
                        + "Do not claim an action was performed unless Java actually "
                        + "performed it.\n\n"
                        + "USER MEMORY:\n"
                        + compactMemory
                        + "\n\nCONTEXT:\n"
                        + contextText
                        + "\n\nUSER:\n"
                        + message
                        + "\n\nBUDDY:\n";

        String json =
                "{"
                        + "\"model\":\"" + MODEL + "\","
                        + "\"prompt\":\"" + escapeJson(prompt) + "\","
                        + "\"stream\":false,"
                        + "\"keep_alive\":\"60m\","
                        + "\"options\":{"
                        + "\"temperature\":0.2,"
                        + "\"num_ctx\":512,"
                        + "\"num_predict\":48"
                        + "}"
                        + "}";

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(OLLAMA_URL))
                        .timeout(Duration.ofSeconds(20))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();

        try {

            long ollamaStart = System.currentTimeMillis();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            long ollamaTime =
                    System.currentTimeMillis() - ollamaStart;

            System.out.println(
                    "BuddyChat Ollama HTTP time: "
                            + ollamaTime
                            + " ms"
            );

            if (response.statusCode() != 200) {
                System.out.println(
                        "BuddyChat HTTP error: "
                                + response.statusCode()
                );
                return fallbackReply(message);
            }

            String result =
                    extractResponse(response.body());

            long totalTime =
                    System.currentTimeMillis() - startTime;

            System.out.println(
                    "BuddyChat total time: "
                            + totalTime
                            + " ms"
            );

            if (result == null || result.isBlank()) {
                return fallbackReply(message);
            }

            return result.trim();

        } catch (java.net.http.HttpTimeoutException e) {

            System.out.println("BuddyChat timed out.");

            return "Sorry, I'm taking too long to respond.";

        } catch (Exception e) {

            System.out.println(
                    "BuddyChat error: "
                            + e.getMessage()
            );

            return fallbackReply(message);
        }
    }

    public ConversationContext getConversationContext() {
        return conversationContext;
    }

    private String tryInstantReply(String input) {

        String command =
                input.trim()
                        .toLowerCase(Locale.ROOT)
                        .replaceAll("\\s+", " ")
                        .replaceAll("[!?.,]+$", "")
                        .trim();

        switch (command) {

            case "hi":
            case "hello":
            case "hey":
            case "hey buddy":
                return "Hey! How can I help?";

            case "good morning":
                return "Good morning! How can I help?";

            case "good afternoon":
                return "Good afternoon! How can I help?";

            case "good evening":
                return "Good evening! How can I help?";

            case "how are you":
            case "how are you buddy":
                return "I'm doing great and ready to help.";

            case "what are you doing":
                return "I'm here and ready for your next command.";

            case "are you there":
            case "are you listening":
                return "Yes, I'm listening.";

            case "thanks":
            case "thank you":
            case "thanks buddy":
                return "You're welcome.";

            case "okay":
            case "ok":
                return "Okay.";

            default:
                return null;
        }
    }

    private String compactMemory(String memory) {

        if (memory == null || memory.isBlank() || memory.equals("none")) {
            return "none";
        }

        final int maxChars = 1800;

        if (memory.length() <= maxChars) {
            return memory;
        }

        return memory.substring(0, maxChars);
    }

    private String compactContext(String context) {

        if (context == null || context.isBlank()) {
            return "none";
        }

        final int maxChars = 2200;

        if (context.length() <= maxChars) {
            return context;
        }

        return context.substring(
                Math.max(0, context.length() - maxChars)
        );
    }

    private String fallbackReply(String message) {

        String command =
                message.trim().toLowerCase(Locale.ROOT);

        if (command.startsWith("hi")
                || command.startsWith("hello")
                || command.startsWith("hey")) {
            return "Hey! How can I help?";
        }

        return "I'm here. What would you like me to do?";
    }

    private String extractResponse(String json) {

        String key = "\"response\":\"";

        int start = json.indexOf(key);

        if (start == -1) {
            System.out.println(
                    "Ollama response: " + json
            );
            return "";
        }

        start += key.length();

        StringBuilder response =
                new StringBuilder();

        boolean escaped = false;

        for (int i = start; i < json.length(); i++) {

            char current = json.charAt(i);

            if (escaped) {

                switch (current) {

                    case 'n':
                        response.append('\n');
                        break;

                    case 'r':
                        response.append('\r');
                        break;

                    case 't':
                        response.append('\t');
                        break;

                    case '"':
                        response.append('"');
                        break;

                    case '\\':
                        response.append('\\');
                        break;

                    default:
                        response.append(current);
                        break;
                }

                escaped = false;

            } else {

                if (current == '\\') {
                    escaped = true;

                } else if (current == '"') {
                    break;

                } else {
                    response.append(current);
                }
            }
        }

        return response.toString().trim();
    }

    private String escapeJson(String text) {

        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")
                .replace("\t", "\\t");
    }
}
