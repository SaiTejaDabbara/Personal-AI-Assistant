package buddy.brain;

public class BuddyChatCapabilityTest {

    public static void main(String[] args) {
        BuddyChat chat = new BuddyChat();

        String desktopReply = chat.chat("Can you contrl my desktop?");
        assertContains(desktopReply, "supported desktop actions");
        assertContains(desktopReply, "mouse or keyboard");

        String followUp = chat.chat("why");
        assertContains(followUp, "implemented and connected");

        String emailReply = chat.chat("Can you send emails and automate processes?");
        assertContains(emailReply, "cannot send emails");
        assertContains(emailReply, "arbitrary workflows");

        String courseReply = chat.chat(
                "I want to learn AI courses. Can you suggest top platforms for free?");
        assertContains(courseReply, "Elements of AI");
        assertContains(courseReply, "Kaggle Learn");
        assertContains(courseReply, "current terms");

        System.out.println("BUDDY_CHAT_CAPABILITIES_OK");
    }

    private static void assertContains(String reply, String expected) {
        if (reply == null || !reply.contains(expected)) {
            throw new IllegalStateException(
                    "Expected reply to contain '" + expected + "', but got: " + reply);
        }
    }
}
