// src/components/UrlInputForm.tsx
import React, { useState, useEffect } from 'react';

interface UrlInputFormProps {
  youtubeUrl: string;
  setYoutubeUrl: (url: string) => void;
  onTranscribe: () => void;
  isTranscribing: boolean;
  disabled: boolean; // General disable flag for the whole form
}

export const UrlInputForm: React.FC<UrlInputFormProps> = ({
  youtubeUrl,
  setYoutubeUrl,
  onTranscribe,
  isTranscribing,
  disabled,
}) => {
  const [isValidInput, setIsValidInput] = useState(true); // Basic input validation

  useEffect(() => {
    // Basic check, can be expanded (e.g., simple regex for URL format)
    setIsValidInput(youtubeUrl.trim().length > 0 && youtubeUrl.startsWith('http'));
  }, [youtubeUrl]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (isValidInput && !isTranscribing && !disabled) {
      onTranscribe();
    }
  };

  return (
    <form onSubmit={handleSubmit} className="mb-6">
      <label htmlFor="youtubeUrl" className="block text-sm font-medium text-gray-300 mb-1">
        YouTube Video URL
      </label>
      <div className="flex space-x-2">
        <input
          type="url"
          id="youtubeUrl"
          name="youtubeUrl"
          value={youtubeUrl}
          onChange={(e) => setYoutubeUrl(e.target.value)}
          placeholder="https://www.youtube.com/watch?v=..."
          className="flex-grow p-2 border border-gray-600 rounded-md shadow-sm focus:ring-sky-500 focus:border-sky-500 bg-gray-700 text-white disabled:opacity-50"
          disabled={isTranscribing || disabled}
          required
        />
        <button
          type="submit"
          className="px-4 py-2 bg-sky-600 text-white rounded-md hover:bg-sky-700 focus:outline-none focus:ring-2 focus:ring-sky-500 focus:ring-offset-2 focus:ring-offset-gray-900 disabled:bg-gray-500 disabled:cursor-not-allowed"
          disabled={!isValidInput || isTranscribing || disabled}
        >
          {isTranscribing ? 'Transcribing...' : 'Transcribe'}
        </button>
      </div>
      {!isValidInput && youtubeUrl.length > 0 && (
        <p className="text-red-400 text-sm mt-1">Please enter a valid URL (e.g., starting with http/https).</p>
      )}
    </form>
  );
};

export default UrlInputForm;
