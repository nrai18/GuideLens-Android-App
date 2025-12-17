import google.generativeai as genai
import os
from dotenv import load_dotenv

load_dotenv()

API_KEY = os.getenv("GEMINI_API_KEY")
genai.configure(api_key=API_KEY)

print("Testing Gemini model...")
print(f"Using model: gemini-2.5-flash-preview-12-2025")

try:
    model = genai.GenerativeModel(
        model_name="gemini-2.5-flash-preview-12-2025",
        generation_config={
            "temperature": 0.7,
            "top_p": 0.95,
            "top_k": 40,
            "max_output_tokens": 1024,
            "response_mime_type": "text/plain",
        }
    )
    
    # Test with simple prompt
    response = model.generate_content("What is Paracetamol used for?")
    print(f"\n✅ SUCCESS!")
    print(f"Response: {response.text}")
    
except Exception as e:
    print(f"\n❌ ERROR: {e}")
    print(f"\nTrying to list models...")
    for m in genai.list_models():
        if 'generateContent' in m.supported_generation_methods:
            print(f"- {m.name}")
