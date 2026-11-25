package buddy.brain;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public final class ChatModelConfig {
    private static final Path CONFIG_PATH = Path.of("buddy_chat_config.properties");
    private static final ChatModelConfig INSTANCE = load();

    private final Map<String, Number> values = new LinkedHashMap<>();

    private ChatModelConfig() {
        values.put("temperature", 0.2);
        values.put("top_k", 40);
        values.put("top_p", 0.95);
        values.put("min_p", 0.05);
        values.put("repeat_penalty", 1.1);
        values.put("repeat_last_n", 64);
        values.put("presence_penalty", 0.0);
        values.put("frequency_penalty", 0.0);
        values.put("tfs_z", 1.0);
        values.put("typical_p", 1.0);
        values.put("n_predict", 96);
        values.put("seed", -1);
        values.put("mirostat", 0);
        values.put("mirostat_tau", 5.0);
        values.put("mirostat_eta", 0.1);
        values.put("top_n_sigma", -1.0);
        values.put("penalize_nl", 1);
        values.put("cache_prompt", 1);
    }

    public static ChatModelConfig getInstance() {
        return INSTANCE;
    }

    public synchronized Map<String, Number> snapshot() {
        return new LinkedHashMap<>(values);
    }

    public synchronized String update(Map<String, String> input) {
        for (Map.Entry<String, Number> entry : values.entrySet()) {
            String raw = input.get(entry.getKey());
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                double value = Double.parseDouble(raw.trim());
                if (!Double.isFinite(value)) {
                    throw new NumberFormatException("Not a finite number");
                }
                values.put(entry.getKey(), validate(entry.getKey(), value));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(
                        "Invalid value for " + entry.getKey() + ": " + raw);
            }
        }
        save();
        return "Chat model settings updated.";
    }

    public synchronized String completionOptionsJson() {
        StringBuilder json = new StringBuilder();
        for (Map.Entry<String, Number> entry : values.entrySet()) {
            if (json.length() > 0) {
                json.append(',');
            }
            String key = entry.getKey();
            json.append('"').append(key).append("\":");
            if (key.equals("penalize_nl") || key.equals("cache_prompt")) {
                json.append(entry.getValue().intValue() == 1 ? "true" : "false");
            } else {
                json.append(format(entry.getValue()));
            }
        }
        return json.toString();
    }

    private Number validate(String key, double value) {
        if (key.equals("temperature")) return clamp(value, 0.0, 2.0);
        if (key.equals("top_k")) return clampInt(value, -1, 1000);
        if (key.equals("top_p") || key.equals("min_p") || key.equals("typical_p")
                || key.equals("tfs_z")) {
            return clamp(value, 0.0, 1.0);
        }
        if (key.equals("repeat_penalty") || key.equals("mirostat_eta")) {
            return clamp(value, 0.0, 10.0);
        }
        if (key.equals("repeat_last_n")) return clampInt(value, -1, 8192);
        if (key.equals("presence_penalty") || key.equals("frequency_penalty")) {
            return clamp(value, -2.0, 2.0);
        }
        if (key.equals("n_predict")) return clampInt(value, 1, 8192);
        if (key.equals("seed")) return clampInt(value, -1, 2147483647);
        if (key.equals("mirostat")) return clampInt(value, 0, 2);
        if (key.equals("mirostat_tau")) return clamp(value, 0.0, 100.0);
        if (key.equals("top_n_sigma")) return clamp(value, -1.0, 100.0);
        if (key.equals("penalize_nl")) return clampInt(value, 0, 1);
        if (key.equals("cache_prompt")) return clampInt(value, 0, 1);
        return value;
    }

    private int clampInt(double value, int minimum, int maximum) {
        long rounded = Math.round(value);
        return (int) Math.max(minimum, Math.min(maximum, rounded));
    }

    private double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private String format(Number value) {
        if (value instanceof Integer) {
            return Integer.toString(value.intValue());
        }
        return String.format(Locale.ROOT, "%.6f", value.doubleValue())
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }

    private static ChatModelConfig load() {
        ChatModelConfig config = new ChatModelConfig();
        Properties properties = new Properties();
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
                properties.load(reader);
            } catch (IOException ignored) {
            }
        }
        for (Map.Entry<String, Number> entry : config.values.entrySet()) {
            String envName = "BUDDY_" + entry.getKey().toUpperCase(Locale.ROOT);
            String raw = System.getenv(envName);
            if (raw == null || raw.isBlank()) {
                raw = properties.getProperty(entry.getKey());
            }
            if (raw != null && !raw.isBlank()) {
                try {
                    double value = Double.parseDouble(raw.trim());
                    config.values.put(entry.getKey(), config.validate(entry.getKey(), value));
                } catch (RuntimeException ignored) {
                }
            }
        }
        return config;
    }

    private void save() {
        Properties properties = new Properties();
        for (Map.Entry<String, Number> entry : values.entrySet()) {
            properties.setProperty(entry.getKey(), format(entry.getValue()));
        }
        try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
            properties.store(writer, "Buddy chat model configuration");
        } catch (IOException e) {
            throw new IllegalStateException("Could not save chat model settings: " + e.getMessage(), e);
        }
    }
}
