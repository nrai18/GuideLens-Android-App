from flask import Flask, request, jsonify
import google.generativeai as genai
import os
from dotenv import load_dotenv
import re

load_dotenv()

app = Flask(__name__)

# Configure Gemini with your key
API_KEY = os.getenv("GEMINI_API_KEY")
if not API_KEY:
    print("⚠️  Warning: GEMINI_API_KEY not found in environment variables")
    print("   Please add GEMINI_API_KEY to your .env file")
genai.configure(api_key=API_KEY)

# Use Gemini 2.5 Flash (Stable Version from June 2025)
model = genai.GenerativeModel(
    model_name="models/gemini-2.5-flash",
    generation_config={
        "temperature": 0.7,
        "top_p": 0.95,
        "top_k": 40,
        "max_output_tokens": 1024,
        "response_mime_type": "text/plain",
    }
)

# Common stop words to filter out
STOP_WORDS = {
    'the', 'is', 'are', 'was', 'were', 'a', 'an', 'and', 'or', 'but',
    'in', 'on', 'at', 'to', 'for', 'of', 'with', 'by', 'from', 'it',
    'this', 'that', 'these', 'those', 'use', 'used', 'take'
}

# Medical keywords to prioritize
MEDICAL_KEYWORDS = {
    'mg', 'ml', 'tablet', 'capsule', 'syrup', 'medicine', 'drug', 'pill',
    'dose', 'dosage', 'prescription', 'relief', 'pain', 'fever'
}

def filter_text(text: str) -> str:
    """
    Filter scanned text to extract only relevant medicine keywords.
    Removes stop words and prioritizes medical terms.
    """
    if not text or len(text.strip()) == 0:
        return text
    
    words = text.split()
    filtered_words = []
    
    for word in words:
        # Remove special characters but keep alphanumeric
        cleaned = re.sub(r'[^a-zA-Z0-9]', '', word)
        lower = cleaned.lower()
        
        # Skip empty or stop words
        if not cleaned or lower in STOP_WORDS:
            continue
        
        # Keep if: has numbers (dosage), medical keyword, or capitalized (brand name)
        if (re.search(r'\d', cleaned) or 
            any(keyword in lower for keyword in MEDICAL_KEYWORDS) or
            (word[0].isupper() and len(cleaned) > 2)):
            filtered_words.append(cleaned)
    
    # Limit to 4 most relevant words
    result = ' '.join(filtered_words[:4])
    print(f"   Filtered: '{text}' → '{result}'")
    return result if result else text  # Return original if nothing left

@app.route('/health', methods=['GET'])
def health():
    """Health check endpoint for connectivity testing"""
    return jsonify({
        'status': 'ok',
        'server': 'running',
        'api': 'gemini-1.5-flash'
    }), 200

@app.route('/identify', methods=['POST'])
def identify():
    try:
        data = request.json
        if not data or 'text' not in data:
            return jsonify({'error': 'No text provided'}), 400
        
        raw_text = data['text']
        print(f"\n📝 Received raw text: {raw_text}")
        
        # Filter text to get relevant keywords
        filtered_text = filter_text(raw_text)
        print(f"🔍 Using filtered text: {filtered_text}")
        
        # Check if we have valid text to analyze
        if not filtered_text or len(filtered_text.strip()) < 2:
            return jsonify({
                'result': None,
                'error': 'No meaningful text detected after filtering'
            }), 400
        
        # Create prompt for Gemini
        prompt = f"""
Scanned text from medicine package: "{filtered_text}"

Identify the medicine and provide a ONE-LINE response in this exact format:
[Medicine Name] - [Primary Use]

Example: "Paracetamol 500mg - Pain and fever relief"

Keep it extremely concise. No warnings or additional information.
"""
        
        # Call Gemini API
        response = model.generate_content(prompt)
        result = response.text.replace('\n', ' ').strip()
        
        print(f"✅ AI Response: {result}")
        return jsonify({
            'result': result,
            'error': None,
            'filtered_text': filtered_text
        }), 200
        
    except Exception as e:
        error_msg = str(e)
        print(f"❌ Error: {error_msg}")
        
        # Check for specific Gemini API errors
        if "quota" in error_msg.lower() or "limit" in error_msg.lower():
            return jsonify({
                'result': None,
                'error': 'API quota exceeded. Please try again later.'
            }), 429
        
        return jsonify({
            'result': None,
            'error': f'Server error: {error_msg}'
        }), 500

if __name__ == '__main__':
    print("=" * 60)
    print("🚀 GuideLens Medicine ID Server")
    print("=" * 60)
    print(f"📡 Server URL: http://localhost:5000")
    print(f"🔌 USB Setup: adb reverse tcp:5000 tcp:5000")
    print(f"💊 API: Gemini 2.5 Flash (Stable)")
    print("=" * 60)
    print("\nEndpoints:")
    print("  GET  /health   - Health check")
    print("  POST /identify - Medicine identification")
    print("\n✅ Server is ready!\n")
    app.run(host='0.0.0.0', port=5000, debug=True)

