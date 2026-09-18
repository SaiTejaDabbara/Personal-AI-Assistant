package buddy.ui;

import java.time.LocalDateTime;

public class BuddyUIEvent {
    private final LocalDateTime timestamp;
    private final String type;
    private final String title;
    private final String detail;
    private final BuddyState state;

    public BuddyUIEvent(LocalDateTime timestamp, String type, String title, String detail, BuddyState state) {
        this.timestamp = timestamp;
        this.type = type;
        this.title = title;
        this.detail = detail;
        this.state = state;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getDetail() {
        return detail;
    }

    public BuddyState getState() {
        return state;
    }
}
