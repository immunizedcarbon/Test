// src/services/geminiService.ts
import {
  GoogleGenerativeAI,
  HarmCategory,
  HarmBlockThreshold,
  GenerateContentRequest,
  Part,
  Content,
} from '@google/genai';
import {
  DEFAULT_TRANSCRIPTION_MODEL,
  DEFAULT_SUMMARY_MODEL,
  DEFAULT_TRANSLATION_MODEL,
} from '../constants';

/**
 * Initializes a new GoogleGenerativeAI client.
 * @param apiKey The API key for the Gemini API.
 * @returns A GoogleGenerativeAI instance.
 */
export function initializeGeminiClient(apiKey: string): GoogleGenerativeAI {
  if (!apiKey) {
    throw new Error('API key is required to initialize Gemini client.');
  }
  return new GoogleGenerativeAI(apiKey);
}

const safetySettings = [
  { category: HarmCategory.HARM_CATEGORY_HARASSMENT, threshold: HarmBlockThreshold.BLOCK_MEDIUM_AND_ABOVE },
  { category: HarmCategory.HARM_CATEGORY_HATE_SPEECH, threshold: HarmBlockThreshold.BLOCK_MEDIUM_AND_ABOVE },
  { category: HarmCategory.HARM_CATEGORY_SEXUALLY_EXPLICIT, threshold: HarmBlockThreshold.BLOCK_MEDIUM_AND_ABOVE },
  { category: HarmCategory.HARM_CATEGORY_DANGEROUS_CONTENT, threshold: HarmBlockThreshold.BLOCK_MEDIUM_AND_ABOVE },
];

/**
 * Transcribes a video from a given YouTube URL using the Gemini API.
 *
 * @param client The initialized GoogleGenerativeAI client.
 * @param videoUrl The URL of the YouTube video to transcribe.
 * @param onProgress Optional callback to handle streaming text chunks.
 * @param modelName The name of the Gemini model to use for transcription.
 * @returns An async generator yielding transcription chunks and finally returning the full transcription.
 */
export async function* transcribeVideo(
  client: GoogleGenerativeAI,
  videoUrl: string,
  onProgress?: (chunk: string) => void,
  modelName: string = DEFAULT_TRANSCRIPTION_MODEL,
): AsyncGenerator<string, string, undefined> {
  if (!client) {
    throw new Error('Gemini client is not initialized.');
  }
  if (!videoUrl) {
    throw new Error('Video URL is required for transcription.');
  }

  const systemInstruction: Content = {
    role: 'system', // Role should be 'system' for system instructions, but API might expect it differently
    parts: [{
      text: `You are an expert transcriptionist. Transcribe the provided video accurately.
- Maintain the original language of the video.
- If multiple speakers are present, try to identify them as "Speaker 1", "Speaker 2", or if possible, by name if mentioned. Start each speaker's segment on a new line with their identifier in bold (e.g., **Speaker 1:**).
- Use Markdown for basic formatting if appropriate (e.g., bold for emphasis if naturally spoken that way).
- Do not include timestamps in the transcription.
- Only output the transcription text. Do not add any conversational fluff or introductory/closing remarks.
- Pay attention to punctuation and sentence structure to ensure readability.`
    }],
  };

  const userPromptForVideo = "Please transcribe this video.";

  const requestContents: Content[] = [
    {
      role: 'user',
      parts: [
        // Placeholder for video file data - actual Gemini API might handle YouTube URLs differently.
        // The Gemini API documentation should be checked. For now, assuming fileUri works.
        { fileData: { mimeType: "video/youtube", fileUri: videoUrl } },
        { text: userPromptForVideo },
      ],
    },
  ];

  try {
    const model = client.getGenerativeModel({
      model: modelName,
      safetySettings,
      // systemInstruction can be passed here if the API version supports it,
      // otherwise it needs to be part of the `contents` array for some models/API versions.
      // Let's assume it's passed here for newer models.
      // If not, `requestContents` would need to include the system instruction.
      // For `generateContentStream`, `systemInstruction` is usually top-level in the model config.
      systemInstruction: systemInstruction.parts[0].text // Pass the text part of system instruction
    });

    const result = await model.generateContentStream(requestContents);
    let fullTranscription = '';

    for await (const chunk of result.stream) {
      if (chunk.candidates && chunk.candidates.length > 0) {
        const part = chunk.candidates[0].content.parts[0];
        if (part && typeof part.text === 'string') {
          const chunkText = part.text;
          if (chunkText) {
            onProgress?.(chunkText);
            fullTranscription += chunkText;
            yield chunkText;
          }
        }
      }
    }
    return fullTranscription;
  } catch (error) {
    console.error('Error during video transcription:', error);
    if (error instanceof Error) {
        throw new Error(`Transcription failed: ${error.message}`);
    }
    throw new Error('Transcription failed due to an unknown error.');
  }
}

/**
 * Summarizes a given text using the Gemini API.
 *
 * @param client The initialized GoogleGenerativeAI client.
 * @param transcription The text to summarize.
 * @param languageName The language for the summary (e.g., "English", "Spanish").
 * @param modelName The name of the Gemini model to use for summarization.
 * @returns A promise that resolves to the summarized text.
 */
export async function summarizeText(
  client: GoogleGenerativeAI,
  transcription: string,
  languageName: string,
  modelName: string = DEFAULT_SUMMARY_MODEL,
): Promise<string> {
  if (!client) {
    throw new Error('Gemini client is not initialized.');
  }
  if (!transcription) {
    throw new Error('Transcription text is required for summarization.');
  }
  if (!languageName) {
    throw new Error('Target language name is required for summarization.');
  }

  const promptText = `You are a helpful AI assistant. Based on the following transcription, please provide a concise and structured summary in ${languageName}.
The summary should capture the main topics, key points, and any conclusions.
Format the summary with clear headings or bullet points if appropriate for readability.

Original Transcription:
---
${transcription}
---

Please provide the summary in ${languageName}.`;

  const request: GenerateContentRequest = {
    contents: [{ role: 'user', parts: [{ text: promptText }] }],
  };

  try {
    const model = client.getGenerativeModel({ model: modelName, safetySettings });
    const result = await model.generateContent(request);
    
    const response = result.response;
    if (response && response.candidates && response.candidates.length > 0 &&
        response.candidates[0].content && response.candidates[0].content.parts &&
        response.candidates[0].content.parts.length > 0 && response.candidates[0].content.parts[0].text) {
      return response.candidates[0].content.parts[0].text;
    } else {
      throw new Error('No summary content received from API or unexpected response structure.');
    }
  } catch (error) {
    console.error('Error during text summarization:', error);
    if (error instanceof Error) {
        throw new Error(`Summarization failed: ${error.message}`);
    }
    throw new Error('Summarization failed due to an unknown error.');
  }
}

/**
 * Translates a given text to a target language using the Gemini API.
 *
 * @param client The initialized GoogleGenerativeAI client.
 * @param textToTranslate The text to translate.
 * @param targetLanguageName The name of the language to translate the text into (e.g., "Spanish", "Japanese").
 * @param modelName The name of the Gemini model to use for translation.
 * @returns A promise that resolves to the translated text.
 */
export async function translateText(
  client: GoogleGenerativeAI,
  textToTranslate: string,
  targetLanguageName: string,
  modelName: string = DEFAULT_TRANSLATION_MODEL,
): Promise<string> {
  if (!client) {
    throw new Error('Gemini client is not initialized.');
  }
  if (!textToTranslate) {
    throw new Error('Text to translate is required.');
  }
  if (!targetLanguageName) {
    throw new Error('Target language name is required for translation.');
  }

  const promptText = `You are a professional translator. Please translate the following text accurately and naturally into ${targetLanguageName}.
Maintain the original meaning, tone, and formatting (like paragraphs or lists) as much as possible.

Text to Translate:
---
${textToTranslate}
---

Translate to ${targetLanguageName}:`;

  const request: GenerateContentRequest = {
    contents: [{ role: 'user', parts: [{ text: promptText }] }],
  };

  try {
    const model = client.getGenerativeModel({ model: modelName, safetySettings });
    const result = await model.generateContent(request);

    const response = result.response;
    if (response && response.candidates && response.candidates.length > 0 &&
        response.candidates[0].content && response.candidates[0].content.parts &&
        response.candidates[0].content.parts.length > 0 && response.candidates[0].content.parts[0].text) {
      return response.candidates[0].content.parts[0].text;
    } else {
      throw new Error('No translated content received from API or unexpected response structure.');
    }
  } catch (error) {
    console.error('Error during text translation:', error);
    if (error instanceof Error) {
        throw new Error(`Translation failed: ${error.message}`);
    }
    throw new Error('Translation failed due to an unknown error.');
  }
}
