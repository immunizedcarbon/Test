// src/components/TranscriptionView.tsx
import React from 'react';

interface TranscriptionViewProps {
  text: string | null;
  isLoading: boolean;
  error: string | null;
  onSave: () => void;
  markdownToHtml: (md: string) => string;
}

export const TranscriptionView: React.FC<TranscriptionViewProps> = ({
  text,
  isLoading,
  error,
  onSave,
  markdownToHtml,
}) => {
  const hasText = text && text.trim().length > 0;

  return (
    <div className="p-1">
      {isLoading && <p className="text-sky-300">Transcription in progress...</p>}
      {error && <p className="text-red-400">Error: {error}</p>}
      {!isLoading && !error && !hasText && (
        <p className="text-gray-400">No transcription available yet. Please enter a URL and click "Transcribe".</p>
      )}
      {hasText && !error && (
        <>
          <div
            className="prose prose-sm prose-invert max-w-none bg-gray-800 p-4 rounded-md overflow-auto h-96" // Added h-96 for fixed height and overflow
            dangerouslySetInnerHTML={{ __html: markdownToHtml(text!) }}
          />
          <button
            onClick={onSave}
            disabled={isLoading || !hasText}
            className="mt-4 px-4 py-2 bg-sky-600 text-white rounded-md hover:bg-sky-700 focus:outline-none focus:ring-2 focus:ring-sky-500 disabled:bg-gray-500"
          >
            Save Transcription
          </button>
        </>
      )}
    </div>
  );
};

export default TranscriptionView;
