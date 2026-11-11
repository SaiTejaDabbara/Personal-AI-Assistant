package buddy.brain;

import java.util.List;

public class BuddyActionPlannerTest {

    public static void main(String[] args) {
        BuddyActionPlanner planner = new BuddyActionPlanner();
        List<String> explicitActions = BuddyActionPlanner.splitExplicitActions(
                "open Notepad and open Calculator");
        if (!explicitActions.equals(List.of("open Notepad", "open Calculator"))) {
            throw new IllegalStateException("Unexpected explicit action split: " + explicitActions);
        }

        String truncatedResponse = """
                {"actions":[
                  {"intent":"open","target":"Notepad","targetType":"application"},
                  {"intent":"open","target":"Calculator","targetType":"application"},
                  {"intent":"open","target":"unfinished
                """;

        List<BuddyActionPlan> parsedPlans = planner.parseActions(truncatedResponse);
        List<BuddyActionPlan> safePlans = planner.sanitizePlans(
                parsedPlans,
                "open Notepad and open Calculator");

        if (safePlans.size() != 2) {
            throw new IllegalStateException("Expected 2 complete plans, got " + safePlans.size());
        }

        assertPlan(safePlans.get(0), "Notepad");
        assertPlan(safePlans.get(1), "Calculator");

        String repeatedFieldsResponse =
                "{\"intent\":\"open\",\"target\":\"Notepad\","
                        + "\"targetType\":\"application\","
                        + "\"intent\":\"open\",\"target\":\"Calculator\","
                        + "\"targetType\":\"application\"}";
        List<BuddyActionPlan> repeatedFieldPlans = planner.sanitizePlans(
                planner.parseActions(repeatedFieldsResponse),
                "open Notepad and open Calculator");

        if (repeatedFieldPlans.size() != 2) {
            throw new IllegalStateException(
                    "Expected 2 plans from repeated intent fields, got "
                            + repeatedFieldPlans.size());
        }

        assertPlan(repeatedFieldPlans.get(0), "Notepad");
        assertPlan(repeatedFieldPlans.get(1), "Calculator");

        System.out.println("MULTI_ACTION_PARSING_OK");
    }

    private static void assertPlan(BuddyActionPlan plan, String expectedTarget) {
        if (!"open".equals(plan.getIntent()) || !expectedTarget.equals(plan.getTarget())) {
            throw new IllegalStateException(
                    "Expected open " + expectedTarget + ", got "
                            + plan.getIntent() + " " + plan.getTarget());
        }
    }
}
