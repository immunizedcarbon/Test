// src/components/SummaryView.tsx
import React from 'react';

interface SummaryViewProps {
  text: string | null;
  isLoading: boolean;
  error: string | null;
  onSave: () => void;
  onSummarize: () => void;
  isTranscriptionAvailable: boolean;
  markdownToHtml: (md: string) => string;
}

export const SummaryView: React.FC<SummaryViewProps> = ({
  text,
  isLoading,
  error,
  onSave,
  onSummarize,
  isTranscriptionAvailable,
  markdownToHtml,
}) => {
  const hasText = text && text.trim().length > 0;

  return (
    <div className="p-1">
      <button
        onClick={onSummarize}
        disabled={isLoading || !isTranscriptionAvailable}
        className="mb-4 px-4 py-2 bg-sky-600 text-white rounded-md hover:bg-sky-700 focus:outline-none focus:ring-2 focus:ring-sky-500 disabled:bg-gray-500"
      >
        {isLoading ? 'Summarizing...' : 'Generate Summary'}
      </button>
      {isLoading && <p className="text-sky-300">Summarization in progress...</p>}
      {error && <p className="text-red-400">Error: {error}</p>}
      {!isLoading && !error && !hasText && isTranscriptionAvailable && (
        <p className="text-gray-400">Click "Generate Summary" to create a summary from the transcription.</p>
      )}
      {!isLoading && !error && !hasText && !isTranscriptionAvailable && (
        <p className="text-gray-400">Please transcribe a video first to enable summarization.</p>
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
            Save Summary
          </button>
        </>
      )}
    </div>
  );
};

export default SummaryView;
