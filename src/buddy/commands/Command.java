package buddy.commands;

public class Command {

    private String action;
    private String target;

    public Command(String action, String target) {
        this.action = action;
        this.target = target;
    }

    public String getAction() {
        return action;
    }

    public String getTarget() {
        return target;
    }
}