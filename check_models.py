import google.generativeai as genai
import os
from dotenv import load_dotenv

load_dotenv()

API_KEY = os.getenv("GEMINI_API_KEY")
genai.configure(api_key=API_KEY)

print("Listing ALL available models with full names:\n")
for m in genai.list_models():
    if 'generateContent' in m.supported_generation_methods:
        print(f"Full name: {m.name}")
        print(f"Display name: {m.display_name}")
        print(f"Description: {m.description[:80] if m.description else 'N/A'}...")
        print("-" * 60)
