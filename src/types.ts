// src/types.ts
export interface TranscriptionResult {
  text: string;
  title?: string; // Optional: if we extract title from transcript
  error?: string;
}

export interface SummaryResult {
  text: string;
  error?: string;
}

export interface TranslationResult {
  text: string;
  error?: string;
}

export type LanguageCode = string; // e.g., 'en', 'es'
export type LanguageName = string; // e.g., 'English', 'Spanish'

export interface ApiError {
  message: string;
  details?: any; // Can be more specific if API error structure is known
}

export interface ProgressState {
  percentage: number;
  statusText: string;
}

// Could be extended for more specific Gemini content part types if needed directly in UI
// For now, keeping it generic for text-based interactions.
export interface GeminiContentRequest {
  role: 'user' | 'model'; // Typically 'user' for requests
  parts: Array<{ text: string } | { inlineData: { mimeType: string; data: string } } | { fileData: { mimeType: string; fileUri: string } }>;
}

export interface GeminiSafetySetting {
  category: string; // e.g., HarmCategory.HARM_CATEGORY_DANGEROUS_CONTENT
  threshold: string; // e.g., HarmBlockThreshold.BLOCK_MEDIUM_AND_ABOVE
}
