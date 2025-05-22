// src/constants.ts
export const APP_TITLE = "YouTube Video Transcription with Gemini";
export const APP_VERSION = "1.0.0";

export const GEMINI_API_MODEL_PRO = "gemini-1.5-pro-latest"; // More capable model
export const GEMINI_API_MODEL_FLASH = "gemini-1.5-flash-latest"; // Faster, more economical model

// Default model to use for various tasks, can be changed in UI or config later if needed
export const DEFAULT_TRANSCRIPTION_MODEL = GEMINI_API_MODEL_FLASH;
export const DEFAULT_SUMMARY_MODEL = GEMINI_API_MODEL_FLASH;
export const DEFAULT_TRANSLATION_MODEL = GEMINI_API_MODEL_FLASH;

export const LANGUAGES: { [key: string]: string } = {
  'English': 'en',
  'Spanish': 'es',
  'French': 'fr',
  'German': 'de',
  'Japanese': 'ja',
  'Korean': 'ko',
  'Chinese (Simplified)': 'zh-CN',
  'Hindi': 'hi',
  'Arabic': 'ar',
  'Portuguese': 'pt',
  // Add more languages as needed
};

export const TAB_IDS = {
  TRANSCRIPTION: 'transcription',
  SUMMARY: 'summary',
  TRANSLATION: 'translation',
};

// Debounce time for API calls or other actions (e.g., for resizing) in milliseconds
export const DEBOUNCE_TIME = 500;

// Maximum characters for certain inputs or outputs, if necessary
// export const MAX_VIDEO_URL_LENGTH = 2048; // Example

// Local storage keys
export const LOCAL_STORAGE_API_KEY = 'gemini_api_key';
