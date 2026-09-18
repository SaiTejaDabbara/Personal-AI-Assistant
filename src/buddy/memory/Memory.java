package buddy.memory;

public class Memory {

    private final String key;
    private final String value;

    public Memory(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }
}