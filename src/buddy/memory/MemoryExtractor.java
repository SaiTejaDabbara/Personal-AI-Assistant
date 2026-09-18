package buddy.memory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class MemoryExtractor {

    private static final String OLLAMA_URL =
            "http://localhost:11434/api/generate";

    private static final String MODEL =
            "llama3.2";


    private final HttpClient httpClient;


    public MemoryExtractor() {

        httpClient =
                HttpClient.newBuilder()
                        .build();
    }


    public Memory extractMemory(
            String userMessage
    ) {

        try {

            String prompt =
                    """
                    You are Buddy's memory system.

                    Analyze the meaning of the user's message.

                    Decide whether the user is clearly stating a
                    personal fact that Buddy should remember for
                    future conversations.

                    Do not remember questions, guesses, possibilities,
                    jokes, uncertainty, alternatives, comparisons,
                    or statements asking for confirmation.

                    Only remember clear facts.

                    If a fact should be remembered, respond with
                    EXACTLY:

                    MEMORY|key|value

                    If nothing should be remembered, respond with
                    EXACTLY:

                    NONE

                    Examples:

                    User: My name is Saiteja
                    MEMORY|name|Saiteja

                    User: My name is Bunny right?
                    NONE

                    User: My name is Bunny or Saiteja
                    NONE

                    User: I live in Hyderabad
                    MEMORY|location|Hyderabad

                    User: I might live in Hyderabad
                    NONE

                    User: %s
                    """.formatted(
                            userMessage
                    );


            String json =
                    "{"
                            + "\"model\":\""
                            + MODEL
                            + "\","

                            + "\"prompt\":\""
                            + escapeJson(
                            prompt
                    )
                            + "\","

                            + "\"stream\":false"
                            + "}";


            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            OLLAMA_URL
                                    )
                            )
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .POST(
                                    HttpRequest.BodyPublishers
                                            .ofString(
                                                    json
                                            )
                            )
                            .build();


            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers
                                    .ofString()
                    );


            if (
                    response.statusCode() != 200
            ) {

                return null;
            }


            String aiResponse =
                    extractResponse(
                            response.body()
                    );


            return parseMemory(
                    aiResponse
            );

        } catch (Exception e) {

            e.printStackTrace();

            return null;
        }
    }


    private Memory parseMemory(
            String response
    ) {

        if (
                response == null
        ) {

            return null;
        }


        response =
                response.trim();


        if (
                response.equalsIgnoreCase(
                        "NONE"
                )
        ) {

            return null;
        }


        if (
                !response.startsWith(
                        "MEMORY|"
                )
        ) {

            return null;
        }


        String[] parts =
                response.split(
                        "\\|",
                        3
                );


        if (
                parts.length != 3
        ) {

            return null;
        }


        String key =
                parts[1].trim();


        String value =
                parts[2].trim();


        if (
                key.isEmpty()
                        || value.isEmpty()
        ) {

            return null;
        }


        return new Memory(
                key,
                value
        );
    }


    private String extractResponse(
            String json
    ) {

        String key =
                "\"response\":\"";


        int start =
                json.indexOf(
                        key
                );


        if (
                start == -1
        ) {

            return "";
        }


        start +=
                key.length();


        StringBuilder response =
                new StringBuilder();


        boolean escaped =
                false;


        for (
                int i = start;
                i < json.length();
                i++
        ) {

            char current =
                    json.charAt(
                            i
                    );


            if (
                    escaped
            ) {

                switch (
                        current
                ) {

                    case 'n':

                        response.append(
                                "\n"
                        );

                        break;

                    case 'r':

                        response.append(
                                "\r"
                        );

                        break;

                    case 't':

                        response.append(
                                "\t"
                        );

                        break;

                    case '"':

                        response.append(
                                "\""
                        );

                        break;

                    case '\\':

                        response.append(
                                "\\"
                        );

                        break;

                    default:

                        response.append(
                                current
                        );
                }


                escaped =
                        false;

            } else {

                if (
                        current == '\\'
                ) {

                    escaped =
                            true;

                } else if (
                        current == '"'
                ) {

                    break;

                } else {

                    response.append(
                            current
                    );
                }
            }
        }


        return response
                .toString()
                .trim();
    }


    private String escapeJson(
            String text
    ) {

        return text
                .replace(
                        "\\",
                        "\\\\"
                )
                .replace(
                        "\"",
                        "\\\""
                )
                .replace(
                        "\n",
                        "\\n"
                )
                .replace(
                        "\r",
                        ""
                );
    }
}