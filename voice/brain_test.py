import ollama

print("🧠 Buddy AI Brain Test")
print("Type 'exit' to quit.\n")

while True:

    user_text = input("You: ")

    if user_text.lower() == "exit":
        print("Buddy brain stopped.")
        break

    response = ollama.chat(
        model="llama3.2",
        messages=[
            {
                "role": "system",
                "content": (
                    "You are Buddy, a personal AI assistant. "
                    "Be helpful, concise, and friendly."
                )
            },
            {
                "role": "user",
                "content": user_text
            }
        ]
    )

    reply = response["message"]["content"]

    print(f"\nBuddy: {reply}\n")