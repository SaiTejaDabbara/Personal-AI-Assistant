import ollama
import json
import webbrowser
import subprocess


# ==========================================
# BUDDY AI BRAIN
# ==========================================

SYSTEM_PROMPT = """
You are the AI brain of a Windows personal assistant called Buddy.

Your job is to understand what the user wants.

You have these actions:

open
close
search
chat

Return ONLY valid JSON.

Format:

{
    "action": "open | close | search | chat",
    "target": "target name or search query",
    "response": "short friendly response"
}

Examples:

User: Open Google

{
    "action": "open",
    "target": "google",
    "response": "Opening Google."
}

User: Open YouTube

{
    "action": "open",
    "target": "youtube",
    "response": "Opening YouTube."
}

User: Open Chrome

{
    "action": "open",
    "target": "chrome",
    "response": "Opening Chrome."
}

User: Search for Java tutorials

{
    "action": "search",
    "target": "Java tutorials",
    "response": "Searching for Java tutorials."
}

User: Hello Buddy

{
    "action": "chat",
    "target": "",
    "response": "Hello! How can I help you?"
}
"""


# ==========================================
# TOOL EXECUTOR
# ==========================================

def execute_action(action, target):

    target = target.lower().strip()


    # OPEN ACTION
    if action == "open":

        if target == "google":

            webbrowser.open(
                "https://www.google.com"
            )

            return "Opening Google."


        elif target == "youtube":

            webbrowser.open(
                "https://www.youtube.com"
            )

            return "Opening YouTube."


        elif target == "chatgpt":

            webbrowser.open(
                "https://chatgpt.com"
            )

            return "Opening ChatGPT."


        elif target == "chrome":

            subprocess.Popen(
                [
                    "cmd",
                    "/c",
                    "start",
                    "chrome"
                ],
                shell=False
            )

            return "Opening Chrome."


        else:

            return (
                f"I understood you want to open "
                f"{target}, but I don't have a tool "
                f"for that yet."
            )


    # SEARCH ACTION
    elif action == "search":

        query = target.replace(
            " ",
            "+"
        )

        webbrowser.open(
            f"https://www.google.com/search?q={query}"
        )

        return f"Searching for {target}."


    # CLOSE ACTION
    elif action == "close":

        if target == "chrome":

            subprocess.run(
                [
                    "taskkill",
                    "/IM",
                    "chrome.exe",
                    "/F"
                ],
                capture_output=True
            )

            return "Closing Chrome."


        else:

            return (
                f"I don't have a close tool for "
                f"{target} yet."
            )


    # CHAT ACTION
    elif action == "chat":

        return None


    return "I don't understand that action."


# ==========================================
# BUDDY BRAIN
# ==========================================

def understand_command(user_text):

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


    reply = response[
        "message"
    ]["content"]


    return json.loads(
        reply
    )


# ==========================================
# MAIN PROGRAM
# ==========================================

print("🤖 Buddy Action Test")
print("Type 'exit' to quit.\n")


while True:

    user_text = input("You: ")


    if user_text.lower() == "exit":

        print("Buddy stopped.")

        break


    try:

        result = understand_command(
            user_text
        )


        action = result.get(
            "action",
            "chat"
        )

        target = result.get(
            "target",
            ""
        )

        ai_response = result.get(
            "response",
            ""
        )


        print(
            f"\n🧠 Action: {action}"
        )

        print(
            f"🎯 Target: {target}"
        )


        # Chat response
        if action == "chat":

            print(
                f"\n🤖 Buddy: "
                f"{ai_response}\n"
            )


        # Execute action
        else:

            execution_response = execute_action(
                action,
                target
            )

            print(
                f"\n🤖 Buddy: "
                f"{execution_response}\n"
            )


    except Exception as e:

        print(
            f"\n❌ Error: {e}\n"
        )