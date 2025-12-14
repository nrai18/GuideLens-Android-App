from flask import Flask, request, jsonify
import google.generativeai as genai
import os
from dotenv import load_dotenv

load_dotenv()

app = Flask(__name__)

# Configure Gemini with your key
API_KEY = os.getenv("GEMINI_API_KEY")
if not API_KEY:
    print("Warning: GEMINI_API_KEY not found in environment variables")
genai.configure(api_key=API_KEY)

# Use the available model
model = genai.GenerativeModel(
  model_name="gemini-2.5-flash",
  generation_config={
    "temperature": 0.7,
    "top_p": 0.95,
    "top_k": 40,
    "max_output_tokens": 1024,
    "response_mime_type": "text/plain",
  }
)

@app.route('/identify', methods=['POST'])
def identify():
    try:
        data = request.json
        if not data or 'text' not in data:
            return jsonify({'error': 'No text provided'}), 400
            
        text = data['text']
        print(f"\n📝 Received text: {text}")
        
        prompt = f"""
        Scanned text from medicine package: "{text}"
        
        Reply with ONLY ONE LINE in this exact format:
        [Medicine Name] - [Primary Use]
        
        Example: "Paracetamol 500mg - Pain and fever relief"
        
        No warnings. Keep it extremely concise.
        """
        
        response = model.generate_content(prompt)
        result = response.text.replace('\n', '').strip()
        
        print(f"✅ AI Response: {result}")
        return jsonify({'result': result})
        
    except Exception as e:
        print(f"❌ Error: {e}")
        return jsonify({'error': str(e)}), 500

if __name__ == '__main__':
    print("🚀 Medical ID Server is running on port 5000")
    print("📡 Point your app to http://<YOUR_PC_IP>:5000/identify")
    app.run(host='0.0.0.0', port=5000)
