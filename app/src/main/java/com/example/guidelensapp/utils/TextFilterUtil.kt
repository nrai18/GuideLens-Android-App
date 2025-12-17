package com.example.guidelensapp.utils

import android.util.Log

/**
 * Utility for filtering and extracting relevant keywords from scanned text.
 * Particularly useful for medicine identification where OCR might capture
 * unnecessary words along with critical medicine information.
 */
object TextFilterUtil {
    private const val TAG = "TextFilterUtil"
    
    // Common stop words to remove (medical context)
    private val STOP_WORDS = setOf(
        "the", "is", "are", "was", "were", "a", "an", "and", "or", "but",
        "in", "on", "at", "to", "for", "of", "with", "by", "from", "it",
        "this", "that", "these", "those", "as", "be", "been", "has", "have",
        "had", "do", "does", "did", "will", "would", "should", "could", "may",
        "might", "must", "can", "use", "used", "take", "taking", "taken"
    )
    
    // Medical/pharmaceutical keywords to prioritize
    private val MEDICAL_INDICATORS = setOf(
        "mg", "ml", "tablet", "capsule", "syrup", "medicine", "drug", "pill",
        "dose", "dosage", "prescription", "rx", "treatment", "relief", "pain",
        "fever", "antibiotic", "vitamin", "supplement", "injection", "cream",
        "ointment", "gel", "drops", "suspension", "solution", "powder"
    )
    
    /**
     * Main function to filter relevant words from scanned text.
     * Removes common stop words and keeps medical/pharmaceutical terms.
     * 
     * @param text Raw OCR text from medicine package
     * @return Filtered string with only relevant keywords
     */
    fun filterRelevantWords(text: String): String {
        if (text.isBlank()) return text
        
        Log.d(TAG, "Original text: $text")
        
        // Extract keywords
        val keywords = extractMedicineKeywords(text)
        
        // If we got good keywords, use them
        if (keywords.isNotEmpty()) {
            val filtered = keywords.joinToString(" ")
            Log.d(TAG, "Filtered to: $filtered")
            return filtered
        }
        
        // Fallback: just remove stop words
        val words = text.split("\\s+".toRegex())
        val filtered = words.filter { word ->
            val cleaned = word.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
            cleaned.isNotEmpty() && cleaned !in STOP_WORDS
        }.take(6) // Limit to 6 most relevant words
        
        val result = filtered.joinToString(" ")
        Log.d(TAG, "Fallback filtered to: $result")
        return result.ifEmpty { text } // Return original if filtering removed everything
    }
    
    /**
     * Extract medicine-specific keywords from text.
     * Prioritizes words that contain medical indicators or numbers (dosages).
     * 
     * @param text Input text
     * @return List of relevant medicine keywords
     */
    fun extractMedicineKeywords(text: String): List<String> {
        val words = text.split("\\s+".toRegex())
        val keywords = mutableListOf<String>()
        
        for (word in words) {
            val cleaned = word.replace(Regex("[^a-zA-Z0-9]"), "")
            val lower = cleaned.lowercase()
            
            // Skip if empty or stop word
            if (cleaned.isEmpty() || lower in STOP_WORDS) continue
            
            // Priority 1: Contains numbers (likely dosage like "500mg", "10ml")
            if (cleaned.matches(Regex(".*\\d+.*"))) {
                keywords.add(cleaned)
                continue
            }
            
            // Priority 2: Medical indicator words
            if (MEDICAL_INDICATORS.any { lower.contains(it) }) {
                keywords.add(cleaned)
                continue
            }
            
            // Priority 3: Capitalized words (likely brand names)
            if (word.firstOrNull()?.isUpperCase() == true && cleaned.length > 2) {
                keywords.add(cleaned)
                continue
            }
        }
        
        // Remove duplicates while preserving order
        return removeDuplicates(keywords).take(4) // Max 4 keywords
    }
    
    /**
     * Remove duplicate words from list while preserving order.
     * 
     * @param words List of words
     * @return De-duplicated list
     */
    fun removeDuplicates(words: List<String>): List<String> {
        val seen = mutableSetOf<String>()
        return words.filter { word ->
            val lower = word.lowercase()
            if (lower in seen) {
                false
            } else {
                seen.add(lower)
                true
            }
        }
    }
    
    /**
     * Check if text appears to be medicine-related.
     * Useful for validation before processing.
     * 
     * @param text Input text
     * @return True if text contains medical indicators
     */
    fun isMedicineRelated(text: String): Boolean {
        val lower = text.lowercase()
        return MEDICAL_INDICATORS.any { lower.contains(it) } ||
               text.matches(Regex(".*\\d+\\s*(mg|ml|g).*"))
    }
}
