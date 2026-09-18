package buddy.brain;

public class BuddyActionPlan {

    private final String intent;
    private final String target;
    private final String targetType;
    private final String location;
    private final String destination;
    private final String source;
    private final boolean requiresConfirmation;
    private final String message;

    public BuddyActionPlan(String intent, String target, String targetType, String location, String source, boolean requiresConfirmation) {
        this(intent, target, targetType, location, "", source, requiresConfirmation, "");
    }

    public BuddyActionPlan(String intent, String target, String targetType, String location, String destination, String source, boolean requiresConfirmation) {
        this(intent, target, targetType, location, destination, source, requiresConfirmation, "");
    }

    public BuddyActionPlan(String intent, String target, String targetType, String location, String destination, String source, boolean requiresConfirmation, String message) {
        this.intent = safe(intent);
        this.target = safe(target);
        this.targetType = safe(targetType);
        this.location = safe(location);
        this.destination = safe(destination);
        this.source = safe(source);
        this.requiresConfirmation = requiresConfirmation;
        this.message = safe(message);
    }

    public String getIntent() { return intent; }
    public String getTarget() { return target; }
    public String getTargetType() { return targetType; }
    public String getLocation() { return location; }
    public String getDestination() { return destination; }
    public String getSource() { return source; }
    public boolean requiresConfirmation() { return requiresConfirmation; }
    public String getMessage() { return message; }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
