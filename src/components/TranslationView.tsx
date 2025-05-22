// src/components/TranslationView.tsx
import React from 'react';
import { LanguageCode, LanguageName } from '../types'; // Assuming types.ts is in src/
import { LANGUAGES } from '../constants'; // Assuming constants.ts is in src/

interface TranslationViewProps {
  text: string | null;
  isLoading: boolean;
  error: string | null;
  onSave: () => void;
  onTranslate: () => void;
  targetLanguage: LanguageCode;
  setTargetLanguage: (code: LanguageCode) => void;
  // availableLanguages: { [key: LanguageName]: LanguageCode }; // Passed directly as LANGUAGES now
  isTranscriptionAvailable: boolean;
  markdownToHtml: (md: string) => string;
}

export const TranslationView: React.FC<TranslationViewProps> = ({
  text,
  isLoading,
  error,
  onSave,
  onTranslate,
  targetLanguage,
  setTargetLanguage,
  isTranscriptionAvailable,
  markdownToHtml,
}) => {
  const hasText = text && text.trim().length > 0;

  return (
    <div className="p-1">
      <div className="flex items-center mb-4 space-x-2">
        <select
          value={targetLanguage}
          onChange={(e) => setTargetLanguage(e.target.value as LanguageCode)}
          disabled={isLoading || !isTranscriptionAvailable}
          className="p-2 border border-gray-600 rounded-md shadow-sm focus:ring-sky-500 focus:border-sky-500 bg-gray-700 text-white disabled:opacity-70"
        >
          {Object.entries(LANGUAGES).map(([name, code]) => (
            <option key={code} value={code}>
              {name}
            </option>
          ))}
        </select>
        <button
          onClick={onTranslate}
          disabled={isLoading || !isTranscriptionAvailable}
          className="px-4 py-2 bg-sky-600 text-white rounded-md hover:bg-sky-700 focus:outline-none focus:ring-2 focus:ring-sky-500 disabled:bg-gray-500"
        >
          {isLoading ? 'Translating...' : 'Translate'}
        </button>
      </div>

      {isLoading && <p className="text-sky-300">Translation in progress...</p>}
      {error && <p className="text-red-400">Error: {error}</p>}
      {!isLoading && !error && !hasText && isTranscriptionAvailable && (
        <p className="text-gray-400">Select a language and click "Translate" to translate the transcription.</p>
      )}
       {!isLoading && !error && !hasText && !isTranscriptionAvailable && (
        <p className="text-gray-400">Please transcribe a video first to enable translation.</p>
      )}
      {hasText && !error && (
        <>
          <div
            className="prose prose-sm prose-invert max-w-none bg-gray-800 p-4 rounded-md overflow-auto h-96"
            dangerouslySetInnerHTML={{ __html: markdownToHtml(text!) }}
          />
          <button
            onClick={onSave}
            disabled={isLoading || !hasText}
            className="mt-4 px-4 py-2 bg-sky-600 text-white rounded-md hover:bg-sky-700 focus:outline-none focus:ring-2 focus:ring-sky-500 disabled:bg-gray-500"
          >
            Save Translation
          </button>
        </>
      )}
    </div>
  );
};
export default TranslationView;
