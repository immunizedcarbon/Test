// src/components/OutputTabs.tsx
import React, { useState } from 'react';
import { TranscriptionView } from './TranscriptionView';
import { SummaryView } from './SummaryView';
import { TranslationView } from './TranslationView';
import { TAB_IDS, LANGUAGES } from '../constants';
import { LanguageCode } from '../types';

interface OutputTabsProps {
  transcriptionText: string | null;
  isTranscribing: boolean;
  transcriptionError: string | null;
  onSaveTranscription: () => void;

  summaryText: string | null;
  isSummarizing: boolean;
  summaryError: string | null;
  onSummarize: () => void;
  onSaveSummary: () => void;

  translationText: string | null;
  isTranslating: boolean;
  translationError: string | null;
  onTranslate: () => void;
  onSaveTranslation: () => void;
  targetLanguage: LanguageCode;
  setTargetLanguage: (code: LanguageCode) => void;
  
  markdownToHtml: (md: string) => string;
  isApiKeyReady: boolean; // To disable tabs if API not ready
}

export const OutputTabs: React.FC<OutputTabsProps> = (props) => {
  const [activeTab, setActiveTab] = useState<string>(TAB_IDS.TRANSCRIPTION);

  const isTranscriptionAvailable = !!props.transcriptionText && props.transcriptionText.trim().length > 0 && !props.transcriptionError;
  const canAccessAdvancedFeatures = props.isApiKeyReady && isTranscriptionAvailable;

  const renderTabContent = () => {
    switch (activeTab) {
      case TAB_IDS.TRANSCRIPTION:
        return (
          <TranscriptionView
            text={props.transcriptionText}
            isLoading={props.isTranscribing}
            error={props.transcriptionError}
            onSave={props.onSaveTranscription}
            markdownToHtml={props.markdownToHtml}
          />
        );
      case TAB_IDS.SUMMARY:
        return (
          <SummaryView
            text={props.summaryText}
            isLoading={props.isSummarizing}
            error={props.summaryError}
            onSave={props.onSaveSummary}
            onSummarize={props.onSummarize}
            isTranscriptionAvailable={canAccessAdvancedFeatures}
            markdownToHtml={props.markdownToHtml}
          />
        );
      case TAB_IDS.TRANSLATION:
        return (
          <TranslationView
            text={props.translationText}
            isLoading={props.isTranslating}
            error={props.translationError}
            onSave={props.onSaveTranslation}
            onTranslate={props.onTranslate}
            targetLanguage={props.targetLanguage}
            setTargetLanguage={props.setTargetLanguage}
            isTranscriptionAvailable={canAccessAdvancedFeatures}
            markdownToHtml={props.markdownToHtml}
          />
        );
      default:
        return null;
    }
  };

  const getTabClass = (tabId: string) => {
    let baseClass = "py-2 px-4 font-medium text-sm rounded-t-md cursor-pointer focus:outline-none";
    if (activeTab === tabId) {
      return `${baseClass} bg-gray-800 text-sky-400 border-b-2 border-sky-400`;
    }
    return `${baseClass} text-gray-400 hover:text-sky-300 hover:bg-gray-750`;
  };
  
  const isTabDisabled = (tabId: string) => {
    if (tabId === TAB_IDS.TRANSCRIPTION) return false; // Transcription tab always accessible
    return !canAccessAdvancedFeatures;
  };

  return (
    <div>
      <div className="border-b border-gray-700 mb-4">
        <nav className="-mb-px flex space-x-2" aria-label="Tabs">
          <button onClick={() => setActiveTab(TAB_IDS.TRANSCRIPTION)} className={getTabClass(TAB_IDS.TRANSCRIPTION)} disabled={isTabDisabled(TAB_IDS.TRANSCRIPTION)}>
            Transcription
          </button>
          <button onClick={() => setActiveTab(TAB_IDS.SUMMARY)} className={getTabClass(TAB_IDS.SUMMARY)} disabled={isTabDisabled(TAB_IDS.SUMMARY)}>
            Summary
          </button>
          <button onClick={() => setActiveTab(TAB_IDS.TRANSLATION)} className={getTabClass(TAB_IDS.TRANSLATION)} disabled={isTabDisabled(TAB_IDS.TRANSLATION)}>
            Translation
          </button>
        </nav>
      </div>
      {!props.isApiKeyReady && activeTab !== TAB_IDS.TRANSCRIPTION && (
         <p className="text-yellow-400 p-1">
           API key is not configured. Summary and Translation features are disabled.
         </p>
      )}
      {props.isApiKeyReady && !isTranscriptionAvailable && activeTab !== TAB_IDS.TRANSCRIPTION && (
         <p className="text-yellow-400 p-1">
           Please generate a transcription first to use Summary or Translation.
         </p>
      )}
      <div>{renderTabContent()}</div>
    </div>
  );
};

export default OutputTabs;
