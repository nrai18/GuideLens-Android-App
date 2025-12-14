import google.generativeai as genai
import os

API_KEY = "AIzaSyBXmBmG1T4zNwSR4lAOAjk5stWsMz6PWoo"
genai.configure(api_key=API_KEY)

print("Available models:")
try:
    for m in genai.list_models():
        if 'generateContent' in m.supported_generation_methods:
            print(f"- {m.name}")
except Exception as e:
    print(f"Error listing models: {e}")
