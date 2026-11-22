package buddy;

public final class LocalModelRuntime {

    public static final String CHAT_COMPLETION_URL =
            "http://127.0.0.1:11436/completion";

    public static final String CHAT_COMPLETIONS_URL =
            "http://127.0.0.1:11437/v1/chat/completions";

    public static final String CHAT_MODEL =
            "llama3.2";

    public static final String PLANNER_MODEL =
            "qwen2.5:0.5b";

    private LocalModelRuntime() {
    }
}
