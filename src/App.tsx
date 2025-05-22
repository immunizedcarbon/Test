// src/App.tsx
import React, { useState, useEffect, useCallback } from 'react';
import { GoogleGenerativeAI } from '@google/genai';
import { APP_TITLE, APP_VERSION, LANGUAGES, DEFAULT_TRANSCRIPTION_MODEL, DEFAULT_SUMMARY_MODEL, DEFAULT_TRANSLATION_MODEL, LOCAL_STORAGE_API_KEY } from './constants';
import { LanguageCode } from './types';
import { cleanYoutubeUrl, markdownToHtml, saveTextToFile } from './utils/textUtils';
import { initializeGeminiClient, transcribeVideo, summarizeText, translateText } from './services/geminiService';

import UrlInputForm from './components/UrlInputForm';
import ProgressBarDisplay from './components/ProgressBarDisplay';
import ApiKeyModal from './components/ApiKeyModal';
import OutputTabs from './components/OutputTabs';

function App() {
  // API Key and Client State
  const [apiKey, setApiKey] = useState<string | null>(null);
  const [aiClient, setAiClient] = useState<GoogleGenerativeAI | null>(null);
  const [isApiKeyModalOpen, setIsApiKeyModalOpen] = useState(false);
  const [isApiKeyReady, setIsApiKeyReady] = useState(false);
  const [appError, setAppError] = useState<string | null>(null);

  // Input State
  const [youtubeUrl, setYoutubeUrl] = useState<string>('');
  const [videoId, setVideoId] = useState<string | null>(null);
  const [videoTitle, setVideoTitle] = useState<string | null>(null); // Optional

  // Transcription State
  const [isTranscribing, setIsTranscribing] = useState(false);
  const [transcriptionProgress, setTranscriptionProgress] = useState(0); // 0-100 or for chunk count
  const [transcriptionStatus, setTranscriptionStatus] = useState('');
  const [transcriptionText, setTranscriptionText] = useState<string | null>(null);
  const [transcriptionError, setTranscriptionError] = useState<string | null>(null);

  // Summary State
  const [isSummarizing, setIsSummarizing] = useState(false);
  const [summaryText, setSummaryText] = useState<string | null>(null);
  const [summaryError, setSummaryError] = useState<string | null>(null);

  // Translation State
  const [isTranslating, setIsTranslating] = useState(false);
  const [translationText, setTranslationText] = useState<string | null>(null);
  const [translationError, setTranslationError] = useState<string | null>(null);
  const [targetLanguage, setTargetLanguage] = useState<LanguageCode>(LANGUAGES['English']); // Default to English

  // Effect for API Key Initialization
  useEffect(() => {
    let key = import.meta.env.VITE_GEMINI_API_KEY as string || null;
    if (!key) {
      key = localStorage.getItem(LOCAL_STORAGE_API_KEY);
    }

    if (key) {
      setApiKey(key);
    } else {
      setIsApiKeyModalOpen(true);
      setAppError("Gemini API Key is not configured. Please enter it below.");
    }
  }, []);

  // Effect to initialize AI Client when API key changes
  useEffect(() => {
    if (apiKey) {
      try {
        const client = initializeGeminiClient(apiKey);
        setAiClient(client);
        setIsApiKeyReady(true);
        setAppError(null); // Clear previous API key errors
        setIsApiKeyModalOpen(false); // Close modal if it was open
        console.log("Gemini AI Client Initialized");
      } catch (error) {
        console.error("Failed to initialize Gemini client:", error);
        setAiClient(null);
        setIsApiKeyReady(false);
        setAppError(error instanceof Error ? error.message : "Failed to initialize Gemini client.");
        // Optionally, reopen modal or show persistent error
        // setIsApiKeyModalOpen(true); 
      }
    } else {
      setIsApiKeyReady(false);
      // setAppError("API Key is not set."); // Avoid setting this if modal is about to open
    }
  }, [apiKey]);

  const handleSaveApiKey = useCallback((newKey: string) => {
    if(newKey.trim()){
      localStorage.setItem(LOCAL_STORAGE_API_KEY, newKey);
      setApiKey(newKey); // This will trigger the useEffect to re-initialize the client
    } else {
      setAppError("Attempted to save an empty API key.");
      setIsApiKeyModalOpen(true); // Keep modal open if save fails
    }
  }, []);
  
  const clearPreviousResults = () => {
    setTranscriptionText(null);
    setTranscriptionError(null);
    setTranscriptionProgress(0);
    setTranscriptionStatus('');
    setSummaryText(null);
    setSummaryError(null);
    setTranslationText(null);
    setTranslationError(null);
    setVideoId(null);
    setVideoTitle(null); // Reset video title
  };

  const handleTranscription = useCallback(async () => {
    if (!aiClient) {
      setTranscriptionError("AI Client not initialized. Check API Key.");
      setIsApiKeyModalOpen(true); // Prompt for key if missing
      return;
    }
    if (!youtubeUrl) {
      setTranscriptionError("YouTube URL is empty.");
      return;
    }

    const { cleanUrl, videoId: extractedVideoId } = cleanYoutubeUrl(youtubeUrl);
    if (!cleanUrl || !extractedVideoId) {
      setTranscriptionError("Invalid YouTube URL provided.");
      return;
    }
    
    clearPreviousResults();
    setVideoId(extractedVideoId);
    // Attempt to derive a title (simple approach, could be improved)
    try {
        const urlObj = new URL(youtubeUrl);
        const potentialTitle = urlObj.pathname.split('/').pop() || extractedVideoId;
        setVideoTitle(potentialTitle.replace(/watch|v=|embed|shorts/g, '').replace(/[_-]/g, ' ').trim());
    } catch (e) {
        setVideoTitle(extractedVideoId); // Fallback to video ID
    }


    setIsTranscribing(true);
    setTranscriptionStatus("Starting transcription...");
    let fullTranscript = "";
    let chunkCount = 0;

    try {
      // Note: The 'fileUri' for YouTube videos is a specific feature.
      // The Gemini API might require a specific format or direct audio/video data for other sources.
      // For YouTube, this approach is based on the assumption Gemini can fetch it.
      const stream = transcribeVideo(aiClient, cleanUrl, (chunk) => {
        chunkCount++;
        setTranscriptionStatus(`Transcribing... received chunk ${chunkCount}`);
        // Here, progress could be based on time if chunks are time-aligned, or just chunk count
        // For simplicity, we might not show a percentage bar but just activity.
        // setTranscriptionProgress(prev => Math.min(95, prev + 5)); // Example incremental progress
      }, DEFAULT_TRANSCRIPTION_MODEL);

      for await (const chunk of stream) {
        fullTranscript += chunk;
        setTranscriptionText(fullTranscript); // Live update
      }
      
      // Attempt to extract title from first line if model provides it
      const lines = fullTranscript.split('\n');
      if (lines.length > 0 && lines[0].toLowerCase().startsWith("title:")) {
          setVideoTitle(lines[0].substring(6).trim());
          // Optionally remove title from transcription text
          // setTranscriptionText(lines.slice(1).join('\n')); 
      } else if (!videoTitle && extractedVideoId) {
          setVideoTitle(`Video: ${extractedVideoId}`); // Default title
      }


      setTranscriptionStatus("Transcription complete.");
    } catch (error) {
      console.error("Transcription error:", error);
      setTranscriptionError(error instanceof Error ? error.message : "An unknown transcription error occurred.");
      setTranscriptionStatus("Transcription failed.");
    } finally {
      setIsTranscribing(false);
      setTranscriptionProgress(100); // Mark as complete or failed
    }
  }, [aiClient, youtubeUrl, videoTitle, videoId]); // Added videoTitle, videoId

  const handleSummarization = useCallback(async () => {
    if (!aiClient || !transcriptionText) {
      setSummaryError("AI Client not ready or no transcription available.");
      return;
    }
    setIsSummarizing(true);
    setSummaryError(null);
    setSummaryText(null); // Clear previous summary
    try {
      const langName = Object.keys(LANGUAGES).find(key => LANGUAGES[key] === targetLanguage) || 'English';
      const summary = await summarizeText(aiClient, transcriptionText, langName, DEFAULT_SUMMARY_MODEL);
      setSummaryText(summary);
    } catch (error) {
      console.error("Summarization error:", error);
      setSummaryError(error instanceof Error ? error.message : "An unknown summarization error occurred.");
    } finally {
      setIsSummarizing(false);
    }
  }, [aiClient, transcriptionText, targetLanguage]);

  const handleTranslation = useCallback(async () => {
    if (!aiClient || !transcriptionText) {
      setTranslationError("AI Client not ready or no transcription available.");
      return;
    }
    setIsTranslating(true);
    setTranslationError(null);
    setTranslationText(null); // Clear previous translation
    try {
      const langName = Object.keys(LANGUAGES).find(key => LANGUAGES[key] === targetLanguage) || 'English';
      const translation = await translateText(aiClient, transcriptionText, langName, DEFAULT_TRANSLATION_MODEL);
      setTranslationText(translation);
    } catch (error) {
      console.error("Translation error:", error);
      setTranslationError(error instanceof Error ? error.message : "An unknown translation error occurred.");
    } finally {
      setIsTranslating(false);
    }
  }, [aiClient, transcriptionText, targetLanguage]);

  const handleSave = useCallback((type: 'transcription' | 'summary' | 'translation') => {
    let textToSave: string | null = null;
    let prefix = "File";
    const currentVideoTitle = videoTitle || videoId || "Untitled"; // Ensure there's always a fallback

    switch (type) {
      case 'transcription':
        textToSave = transcriptionText;
        prefix = "Transcription";
        break;
      case 'summary':
        textToSave = summaryText;
        prefix = "Summary";
        break;
      case 'translation':
        textToSave = translationText;
        prefix = "Translation";
        break;
    }

    if (textToSave && videoId) { // videoId must exist for saving
      saveTextToFile(textToSave, prefix, currentVideoTitle, videoId);
    } else {
      setAppError(`Cannot save: No ${type} text available or video ID is missing.`);
      // Clear appError after a few seconds
      setTimeout(() => setAppError(null), 5000);
    }
  }, [transcriptionText, summaryText, translationText, videoId, videoTitle]);


  return (
    <div className="min-h-screen bg-gray-900 text-gray-100 flex flex-col items-center p-4 selection:bg-sky-700 selection:text-white">
      <div className="w-full max-w-3xl">
        <header className="mb-8 text-center">
          <h1 className="text-4xl font-bold text-sky-400">{APP_TITLE}</h1>
          <p className="text-sm text-gray-500">v{APP_VERSION}</p>
        </header>

        <ApiKeyModal
          isOpen={isApiKeyModalOpen}
          onClose={() => setIsApiKeyModalOpen(false)}
          onSaveKey={handleSaveApiKey}
        />

        {appError && !isApiKeyModalOpen && ( // Don't show general app error if modal is open for API key
          <div className="mb-4 p-3 bg-red-800 border border-red-600 text-red-200 rounded-md text-sm">
            Global Error: {appError}
          </div>
        )}
        
        {!isApiKeyReady && !isApiKeyModalOpen && (
           <div className="mb-4 p-3 bg-yellow-800 border border-yellow-600 text-yellow-200 rounded-md text-sm">
             Warning: AI features are disabled until the Gemini API Key is configured correctly.
           </div>
        )}

        <main>
          <UrlInputForm
            youtubeUrl={youtubeUrl}
            setYoutubeUrl={setYoutubeUrl}
            onTranscribe={handleTranscription}
            isTranscribing={isTranscribing}
            disabled={!isApiKeyReady || isTranscribing}
          />

          <ProgressBarDisplay
            progress={isTranscribing ? transcriptionProgress : undefined} // Show progress only during transcription
            status={transcriptionStatus}
            error={transcriptionError} // Show transcription error specifically here
            isVisible={isTranscribing || !!transcriptionError || (transcriptionStatus !== '' && transcriptionStatus !== 'Transcription complete.')}
          />
          
          {/* General status area for non-transcription operations, could be refined */}
           {(isSummarizing || isTranslating || summaryError || translationError) && (
            <div className="my-2 p-2 bg-gray-800 rounded-md text-sm">
              {isSummarizing && <p>Summarizing...</p>}
              {summaryError && <p className="text-red-400">Summary Error: {summaryError}</p>}
              {isTranslating && <p>Translating...</p>}
              {translationError && <p className="text-red-400">Translation Error: {translationError}</p>}
            </div>
          )}


          {isApiKeyReady ? (
            <OutputTabs
              transcriptionText={transcriptionText}
              isTranscribing={isTranscribing}
              transcriptionError={transcriptionError}
              onSaveTranscription={() => handleSave('transcription')}
              
              summaryText={summaryText}
              isSummarizing={isSummarizing}
              summaryError={summaryError}
              onSummarize={handleSummarization}
              onSaveSummary={() => handleSave('summary')}
              
              translationText={translationText}
              isTranslating={isTranslating}
              translationError={translationError}
              onTranslate={handleTranslation}
              onSaveTranslation={() => handleSave('translation')}
              targetLanguage={targetLanguage}
              setTargetLanguage={setTargetLanguage}
              
              markdownToHtml={markdownToHtml}
              isApiKeyReady={isApiKeyReady}
            />
          ) : (
            <div className="mt-6 p-4 bg-gray-800 rounded-md text-center text-gray-400">
              <p>Please configure your Gemini API Key to enable application features.</p>
              <button 
                onClick={() => setIsApiKeyModalOpen(true)}
                className="mt-2 px-3 py-1.5 text-sm bg-sky-600 text-white rounded-md hover:bg-sky-700"
              >
                Enter API Key
              </button>
            </div>
          )}
        </main>

        <footer className="mt-12 text-center text-xs text-gray-600">
          <p>&copy; {new Date().getFullYear()} Your Personal Transcription App. Use responsibly.</p>
          <p>This tool is for personal use only. Ensure you have rights to transcribe any content.</p>
        </footer>
      </div>
    </div>
  );
}

export default App;
