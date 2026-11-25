package buddy.brain;

import buddy.LocalModelRuntime;
import buddy.memory.MemoryManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

public class BuddyChat {

    private final MemoryManager memoryManager;
    private final HttpClient httpClient;
    private final ConversationContext conversationContext;
    private String previousChatUserMessage;
    private String previousChatResponse;

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

        if (instant == null) {
            instant = tryCapabilityReply(message);
        }

        if (instant == null) {
            instant = tryFreeAiCourseReply(message);
        }

        if (instant == null) {
            instant = tryCapabilityFollowUp(message);
        }

        if (instant != null) {
            conversationContext.setLastUserMessage(message);
            conversationContext.setLastAction("Conversation");

            System.out.println(
                    "BuddyChat fast reply: "
                            + (System.currentTimeMillis() - startTime)
                            + " ms"
            );

            return rememberConversation(message, instant);
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

        String recentConversation = previousChatUserMessage == null
                ? "none"
                : "User: " + previousChatUserMessage + "\nBuddy: " + previousChatResponse;

        String compactMemory =
                compactMemory(memoryText.toString());

        String prompt =
                "You are Buddy, a helpful personal AI desktop assistant.\n\n"
                        + "Answer clearly and directly. Use the minimum detail needed for a "
                        + "complete answer. Keep simple answers short. Keep longer answers "
                        + "short, focused, and under five bullets or paragraphs. Avoid filler "
                        + "and repetition.\n"
                        + "Be honest about uncertainty and do not invent facts or confuse "
                        + "different products or organizations. When explaining AI systems, "
                        + "describe established public principles and clearly label details "
                        + "that are not public. Do not refuse a general educational explanation "
                        + "just because a product is proprietary. Do not claim a system "
                        + "retrieves from a knowledge base unless it actually uses retrieval. "
                        + "Describe only "
                        + "Buddy's implemented capabilities: it can launch or close supported "
                        + "apps and websites, search, and manage supported files. It does not "
                        + "have general mouse or keyboard control, arbitrary window control, "
                        + "email integration, or unrestricted workflow automation. Never claim "
                        + "it can send email; offer to draft one instead. For general learning "
                        + "and recommendation questions, answer helpfully from general knowledge "
                        + "without requiring a live search, and qualify details such as current "
                        + "prices or availability that may change.\n"
                        + "Use memory or previous context only when it clearly helps "
                        + "answer the current message.\n"
                        + "Do not claim an action was performed unless Java actually "
                        + "performed it.\n\n"
                        + "USER MEMORY:\n"
                        + compactMemory
                        + "\n\nRECENT CONVERSATION:\n"
                        + recentConversation
                        + "\n\nCONTEXT:\n"
                        + contextText
                        + "\n\nUSER:\n"
                        + message
                        + "\n\nBUDDY:\n";

        String json =
                "{"
                        + "\"prompt\":\"" + escapeJson(prompt) + "\","
                        + "\"stream\":false,"
                        + ChatModelConfig.getInstance().completionOptionsJson()
                        + "}";

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(LocalModelRuntime.CHAT_COMPLETION_URL))
                        .timeout(Duration.ofSeconds(45))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();

        try {

            long runtimeStart = System.currentTimeMillis();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            long runtimeTime =
                    System.currentTimeMillis() - runtimeStart;

            System.out.println(
                    "BuddyChat GPU runtime HTTP time: "
                            + runtimeTime
                            + " ms"
            );

            if (response.statusCode() != 200) {
                System.out.println(
                        "BuddyChat HTTP error: "
                                + response.statusCode()
                );
                return rememberConversation(message, fallbackReply(message));
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
                return rememberConversation(message, fallbackReply(message));
            }

            return rememberConversation(message, result.trim());

        } catch (java.net.http.HttpTimeoutException e) {

            System.out.println("BuddyChat timed out.");

            return rememberConversation(
                    message,
                    "The local AI model is taking longer than expected. Please try again shortly.");

        } catch (Exception e) {

            System.out.println(
                    "BuddyChat error: "
                            + e.getMessage()
            );

            return rememberConversation(message, fallbackReply(message));
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

    private String tryCapabilityReply(String input) {
        String command = normalizeMessage(input);

        boolean asksAboutCapabilities = command.matches(
                "^(can|could|do|does|are|what|which|how)\\b.*");
        if (!asksAboutCapabilities) {
            return null;
        }

        boolean asksAboutDesktopControl =
                command.matches(".*\\b(?:control|contrl|manage|operate)\\b.*\\b(?:desktop|computer|pc)\\b.*")
                        || command.matches(".*\\b(?:desktop|computer|pc)\\b.*\\b(?:control|contrl|manage|operate)\\b.*");

        if (asksAboutDesktopControl) {
            return "I can carry out Buddy's supported desktop actions, such as opening or closing supported apps and websites, searching, and managing files. I cannot control the mouse or keyboard, operate arbitrary windows, or change system settings. Tell me the specific task and I can confirm whether Buddy supports it.";
        }

        boolean asksToSendEmail =
                command.matches(".*\\b(?:send|sending)\\b.*\\b(?:an?\\s+)?emails?\\b.*")
                        || command.matches(".*\\bemails?\\b.*\\b(?:send|sending)\\b.*");
        boolean asksAboutAutomation =
                command.matches(".*\\b(?:automate|automation|automating|workflow|processes?)\\b.*");

        if (asksToSendEmail || asksAboutAutomation) {
            return "Buddy is not connected to an email service, so I cannot send emails, but I can help draft one. I can also carry out supported local tasks, such as opening apps and organizing files; I cannot run arbitrary workflows or unattended automation. Describe the process and I will tell you which steps Buddy can handle.";
        }

        return null;
    }

    private String tryFreeAiCourseReply(String input) {
        String command = normalizeMessage(input);
        boolean asksAboutAi = command.matches(
                ".*\\b(?:ai|artificial intelligence|machine learning)\\b.*");
        boolean asksAboutLearning = command.matches(
                ".*\\b(?:course|courses|learn|learning|platform|platforms|study|training)\\b.*");
        boolean asksForFreeResources = command.matches(
                ".*\\b(?:free|no cost|without paying)\\b.*");

        if (!asksAboutAi || !asksAboutLearning || !asksForFreeResources) {
            return null;
        }

        return "Here are good free places to start learning AI:\n\n"
                + "• Elements of AI — beginner-friendly introduction to AI concepts.\n"
                + "• Google Machine Learning Crash Course — practical ML fundamentals and exercises.\n"
                + "• fast.ai — hands-on deep learning, best if you are comfortable with Python.\n"
                + "• Kaggle Learn — short, practical lessons in Python, machine learning, and data skills.\n"
                + "• Microsoft Learn and IBM SkillsBuild — structured AI modules and learning paths.\n\n"
                + "Course content is often free, while certificates or some graded features may cost extra; check each provider's current terms. If you share your experience level and whether you prefer coding or theory, I can suggest a learning path.";
    }

    private String tryCapabilityFollowUp(String input) {
        String command = normalizeMessage(input);
        if (!command.matches("^(why|why not|why cant you|why can't you)$")
                || previousChatUserMessage == null) {
            return null;
        }

        String previousQuestion = normalizeMessage(previousChatUserMessage);
        if (previousQuestion.contains("desktop")
                || previousQuestion.contains("computer")
                || previousQuestion.contains("email")
                || previousQuestion.contains("automate")
                || previousQuestion.contains("workflow")) {
            return "Buddy can only perform actions that are explicitly implemented and connected to it. It can launch supported apps and websites and manage files, but it has no general mouse/keyboard or email integration. That limitation prevents me from claiming I can control or send things that Buddy cannot actually access.";
        }

        return null;
    }

    private String normalizeMessage(String input) {
        return input.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .replaceAll("[!?.,]+$", "")
                .trim();
    }

    private String rememberConversation(String userMessage, String response) {
        previousChatUserMessage = userMessage;
        previousChatResponse = response;
        return response;
    }

    private String compactMemory(String memory) {

        if (memory == null || memory.isBlank() || memory.equals("none")) {
            return "none";
        }

        final int maxChars = 700;

        if (memory.length() <= maxChars) {
            return memory;
        }

        return memory.substring(0, maxChars);
    }

    private String compactContext(String context) {

        if (context == null || context.isBlank()) {
            return "none";
        }

        final int maxChars = 900;

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

        String key = "\"content\":\"";

        int start = json.indexOf(key);

        if (start == -1) {
            System.out.println(
                    "GPU runtime response: " + json
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
