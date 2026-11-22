package buddy.brain;

import buddy.LocalModelRuntime;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class OllamaBrain {

    public String understand(String userInput) {

        try {

            URL url =
                    new URL(LocalModelRuntime.CHAT_COMPLETION_URL);

            HttpURLConnection connection =
                    (HttpURLConnection)
                            url.openConnection();


            connection.setRequestMethod("POST");

            connection.setRequestProperty(
                    "Content-Type",
                    "application/json"
            );

            connection.setDoOutput(true);


            String prompt =
                    """
                    You are the AI brain of a Windows assistant named Buddy.

                    Understand the user's command.

                    Convert the user's request into a simple command
                    that another Java program can execute.

                    Examples:

                    User: Could you please open Google for me?
                    Command: open google

                    User: Launch Chrome
                    Command: open chrome

                    User: I want to watch YouTube
                    Command: open youtube

                    User: Find Java tutorials
                    Command: search java tutorials

                    User: What time is it?
                    Command: what is the time

                    User: Close Chrome
                    Command: close chrome

                    Return ONLY the command.
                    Do not explain anything.

                    User request:
                    """
                            + userInput;


            String json =
                    "{"
                            + "\"prompt\":"
                            + quote(prompt)
                            + ","

                            + "\"stream\":false,"
                            + "\"temperature\":0,"
                            + "\"n_predict\":128"
                            + "}";


            try (OutputStream output =
                         connection.getOutputStream()) {

                output.write(
                        json.getBytes("UTF-8")
                );
            }


            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    connection.getInputStream()
                            )
                    );


            StringBuilder response =
                    new StringBuilder();


            String line;


            while ((line = reader.readLine()) != null) {

                response.append(line);
            }


            reader.close();


            String ollamaResponse =
                    response.toString();


            String command =
                    extractResponse(
                            ollamaResponse
                    );


            return command
                    .toLowerCase()
                    .trim();


        } catch (Exception e) {

            System.out.println(
                    "GPU runtime error: "
                            + e.getMessage()
            );


            // Fallback:
            // let old command system handle it

            return userInput;
        }
    }


    private String quote(String text) {

        return "\""
                + text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")
                + "\"";
    }


    private String extractResponse(
            String json
    ) {

        String key =
                "\"content\":\"";


        int start =
                json.indexOf(key);


        if (start == -1) {

            return "";
        }


        start += key.length();


        int end =
                json.indexOf(
                        "\"",
                        start
                );


        if (end == -1) {

            return "";
        }


        return json
                .substring(
                        start,
                        end
                )
                .replace("\\n", "")
                .replace("\\\"", "\"");
    }
}