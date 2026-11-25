package buddy.brain;

import java.util.Map;

public class ChatModelConfigTest {
    public static void main(String[] args) {
        ChatModelConfig config = ChatModelConfig.getInstance();
        Map<String, Number> original = config.snapshot();

        try {
            config.update(Map.of(
                    "temperature", "0.7",
                    "n_predict", "256",
                    "top_k", "20"));

            Map<String, Number> updated = config.snapshot();
            if (updated.get("temperature").doubleValue() != 0.7) {
                throw new AssertionError("temperature was not updated");
            }
            if (updated.get("n_predict").intValue() != 256) {
                throw new AssertionError("n_predict was not updated");
            }
            if (!config.completionOptionsJson().contains("\"temperature\":0.7")) {
                throw new AssertionError("completion options did not include temperature");
            }

            config.update(Map.of(
                    "temperature", Double.toString(original.get("temperature").doubleValue()),
                    "n_predict", Integer.toString(original.get("n_predict").intValue()),
                    "top_k", Integer.toString(original.get("top_k").intValue())));
        } catch (RuntimeException e) {
            throw e;
        }

        System.out.println("CHAT_MODEL_CONFIG_OK");
    }
}
