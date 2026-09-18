package buddy.memory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MemoryManager {

    private static final String WEBSITE_FILE =
            "buddy_websites.txt";

    private static final String APPLICATION_FILE =
            "buddy_applications.txt";

    private static final String USER_MEMORY_FILE =
            "buddy_user_memory.txt";


    public void saveWebsite(
            String name,
            String url
    ) {

        saveToFile(
                WEBSITE_FILE,
                name,
                url
        );
    }


    public void saveApplication(
            String name,
            String path
    ) {

        saveToFile(
                APPLICATION_FILE,
                name,
                path
        );
    }


    public void saveUserMemory(
            String key,
            String value
    ) {

        updateUserMemory(
                key,
                value
        );
    }


    private void saveToFile(
            String fileName,
            String name,
            String value
    ) {

        try {

            String data =
                    name.toLowerCase()
                            + "="
                            + value
                            + System.lineSeparator();


            Files.writeString(
                    Path.of(
                            fileName
                    ),
                    data,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );

        } catch (IOException e) {

            System.out.println(
                    "Could not save Buddy's memory."
            );
        }
    }


    public Map<String, String> loadWebsites() {

        return loadFromFile(
                WEBSITE_FILE
        );
    }


    public Map<String, String> loadApplications() {

        return loadFromFile(
                APPLICATION_FILE
        );
    }


    public Map<String, String> loadUserMemory() {

        return loadFromFile(
                USER_MEMORY_FILE
        );
    }


    public String getUserMemory(
            String key
    ) {

        Map<String, String> memory =
                loadUserMemory();


        return memory.get(
                key.toLowerCase()
        );
    }


    public void updateUserMemory(
            String key,
            String value
    ) {

        try {

            Map<String, String> memory =
                    loadUserMemory();


            memory.put(
                    key.toLowerCase(),
                    value
            );


            StringBuilder data =
                    new StringBuilder();


            for (
                    Map.Entry<String, String> entry
                    : memory.entrySet()
            ) {

                data.append(
                        entry.getKey()
                );

                data.append(
                        "="
                );

                data.append(
                        entry.getValue()
                );

                data.append(
                        System.lineSeparator()
                );
            }


            Files.writeString(
                    Path.of(
                            USER_MEMORY_FILE
                    ),
                    data.toString(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );

        } catch (IOException e) {

            System.out.println(
                    "Could not update Buddy's user memory."
            );
        }
    }


    private Map<String, String> loadFromFile(
            String fileName
    ) {

        Map<String, String> data =
                new HashMap<>();


        try {

            Path path =
                    Path.of(
                            fileName
                    );


            if (
                    !Files.exists(
                            path
                    )
            ) {

                return data;
            }


            List<String> lines =
                    Files.readAllLines(
                            path
                    );


            for (
                    String line
                    : lines
            ) {

                if (
                        !line.contains(
                                "="
                        )
                ) {

                    continue;
                }


                String[] parts =
                        line.split(
                                "=",
                                2
                        );


                if (
                        parts.length == 2
                ) {

                    String name =
                            parts[0]
                                    .trim()
                                    .toLowerCase();


                    String value =
                            parts[1]
                                    .trim();


                    data.put(
                            name,
                            value
                    );
                }
            }

        } catch (IOException e) {

            System.out.println(
                    "Could not load Buddy's memory."
            );
        }


        return data;
    }
}