import ollama
import json


print("🧠 Buddy Action Brain Test")
print("Type 'exit' to quit.\n")


SYSTEM_PROMPT = """
You are the AI brain of a Windows personal assistant called Buddy.

You do NOT directly control the computer.

Instead, another program can execute actions for you.

Your job is to understand what the user wants and return ONLY
valid JSON.

Available actions:

1. open
   Opens an application or website.

2. close
   Closes an application.

3. search
   Searches the web.

4. chat
   Normal conversation or questions.

Return JSON in exactly this format:

{
    "action": "open | close | search | chat",
    "target": "the application, website, or search query",
    "response": "short response for the user"
}

Examples:

User: Open Google

{
    "action": "open",
    "target": "google",
    "response": "Opening Google."
}

User: Can you open Chrome?

{
    "action": "open",
    "target": "chrome",
    "response": "Opening Chrome."
}

User: Search YouTube for Java tutorials

{
    "action": "search",
    "target": "YouTube for Java tutorials",
    "response": "Searching YouTube for Java tutorials."
}

User: Hello Buddy

{
    "action": "chat",
    "target": "",
    "response": "Hello! How can I help you?"
}

Do not explain the JSON.
Do not use markdown.
Return only JSON.
"""


while True:

    user_text = input("You: ")

    if user_text.lower() == "exit":

        print("Buddy brain stopped.")

        break


    try:

        response = ollama.chat(

            model="llama3.2",

            messages=[

                {
                    "role": "system",
                    "content": SYSTEM_PROMPT
                },

                {
                    "role": "user",
                    "content": user_text
                }

            ],

            format="json"
        )


        reply = response["message"]["content"]

        data = json.loads(reply)


        print("\n🧠 Buddy understood:")

        print(
            "Action:",
            data.get("action")
        )

        print(
            "Target:",
            data.get("target")
        )

        print(
            "Response:",
            data.get("response")
        )

        print()


    except Exception as e:

        print(
            "\nError:",
            e,
            "\n"
        )