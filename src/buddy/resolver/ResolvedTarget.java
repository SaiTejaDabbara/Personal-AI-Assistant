package buddy.resolver;

public class ResolvedTarget {

    private final String type;
    private final String name;
    private final String value;

    public ResolvedTarget(String type, String name, String value) {
        this.type = type;
        this.name = name;
        this.value = value;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }
}