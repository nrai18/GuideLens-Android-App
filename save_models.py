import google.generativeai as genai
import os
from dotenv import load_dotenv

load_dotenv()

API_KEY = os.getenv("GEMINI_API_KEY")
genai.configure(api_key=API_KEY)

with open("available_models.txt", "w", encoding="utf-8") as f:
    f.write("Available Gemini Models:\n")
    f.write("=" * 80 + "\n\n")
    
    for m in genai.list_models():
        if 'generateContent' in m.supported_generation_methods:
            f.write(f"Model Name: {m.name}\n")
            f.write(f"Display Name: {m.display_name}\n")
            if m.description:
                f.write(f"Description: {m.description}\n")
            f.write("-" * 80 + "\n\n")

print("Model information written to available_models.txt")
