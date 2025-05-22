// src/components/ApiKeyModal.tsx
import React, { useState } from 'react';
import { LOCAL_STORAGE_API_KEY } from '../constants'; // Assuming you have this constant

interface ApiKeyModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSaveKey: (apiKey: string) => void; // Callback to App.tsx to set the key in state and re-init client
}

export const ApiKeyModal: React.FC<ApiKeyModalProps> = ({ isOpen, onClose, onSaveKey }) => {
  const [apiKeyInput, setApiKeyInput] = useState('');
  const [error, setError] = useState('');

  if (!isOpen) {
    return null;
  }

  const handleSave = () => {
    if (apiKeyInput.trim() === '') {
      setError('API Key cannot be empty.');
      return;
    }
    setError('');
    onSaveKey(apiKeyInput); // Pass to parent
    // Optionally save to localStorage, but App.tsx should manage the active key for the client
    // localStorage.setItem(LOCAL_STORAGE_API_KEY, apiKeyInput);
    onClose(); // Close modal after attempting to save
  };

  const handleCancel = () => {
    setError('');
    onClose();
  };

  return (
    <div className="fixed inset-0 bg-gray-900 bg-opacity-75 flex items-center justify-center z-50 p-4">
      <div className="bg-gray-800 p-6 rounded-lg shadow-xl w-full max-w-md">
        <h2 className="text-xl font-semibold text-white mb-4">Enter Gemini API Key</h2>
        <p className="text-sm text-gray-400 mb-1">
          Your API key is required to use the transcription and other AI features.
          It will be stored in your browser's local storage for convenience.
        </p>
        <p className="text-xs text-gray-500 mb-4">
          You can obtain an API key from Google AI Studio. Never share your API key publicly.
        </p>
        <input
          type="password" // Use password type to obscure the key
          value={apiKeyInput}
          onChange={(e) => {
            setApiKeyInput(e.target.value);
            if (error) setError('');
          }}
          placeholder="Enter your Gemini API Key"
          className="w-full p-2 border border-gray-600 rounded-md shadow-sm focus:ring-sky-500 focus:border-sky-500 bg-gray-700 text-white mb-2"
        />
        {error && <p className="text-red-400 text-sm mb-3">{error}</p>}
        <div className="flex justify-end space-x-3">
          <button
            onClick={handleCancel}
            className="px-4 py-2 text-sm bg-gray-600 text-gray-200 rounded-md hover:bg-gray-500 focus:outline-none focus:ring-2 focus:ring-gray-400"
          >
            Cancel
          </button>
          <button
            onClick={handleSave}
            className="px-4 py-2 text-sm bg-sky-600 text-white rounded-md hover:bg-sky-700 focus:outline-none focus:ring-2 focus:ring-sky-500"
          >
            Save and Use Key
          </button>
        </div>
      </div>
    </div>
  );
};

export default ApiKeyModal;
