import buddy.commands.Commands;

import java.util.Locale;

public class CommandsBehaviorTest {
    public static void main(String[] args) {
        Commands commands = new Commands();

        commands.process("remember my name is Alice");

        String directReply = commands.process("what is my name");
        String typoReply = commands.process("whay is my name");

        assertNameReply(directReply, "what is my name");
        assertNameReply(typoReply, "whay is my name");

        System.out.println("NAME_REPLY_OK: " + directReply + " | " + typoReply);
    }

    private static void assertNameReply(String reply, String originalInput) {
        String normalized = reply == null ? "" : reply.trim();

        if (normalized.isEmpty()) {
            throw new IllegalStateException("Buddy returned an empty response for '" + originalInput + "'.");
        }

        if (!normalized.toLowerCase(Locale.ROOT).contains("your name is alice")) {
            throw new IllegalStateException("Expected a friendly 'Hi, your name is Alice.' response for '"
                    + originalInput + "', but got: " + reply);
        }
    }
}
