package buddy.logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ActivityLogger {

    private static final Path LOG_FILE = Path.of("buddy_activity.txt");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm:ss a");

    public void log(String command, String result) {
        String entry = LocalDateTime.now().format(TIMESTAMP_FORMAT)
                + " | Command: " + command
                + " | Result: " + result
                + System.lineSeparator();

        try {
            Files.writeString(
                    LOG_FILE,
                    entry,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.out.println("Could not write Buddy's activity log.");
        }
    }
}
