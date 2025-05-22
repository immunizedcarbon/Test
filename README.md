# YouTube Video Transcription with Gemini

**Version:** 1.0.0

A state-of-the-art web application to transcribe YouTube videos using Google's Gemini API. It also provides features to summarize and translate the transcription, offering a seamless experience for processing video content.

## Features

*   **YouTube Video Transcription**: Input a YouTube video URL and get a full transcription.
*   **Streaming Support**: View transcription results as they arrive from the API.
*   **Markdown Rendering**: Transcriptions, summaries, and translations are rendered from Markdown to HTML for clear formatting (including speaker labels, bolding, lists).
*   **Text Summarization**: Generate concise summaries of the transcribed text using the Gemini API.
*   **Text Translation**: Translate the transcription into various supported languages.
*   **Save Outputs**: Download transcriptions, summaries, or translations as `.txt` files, including metadata.
*   **User-Friendly Interface**: Dark theme, responsive design, progress indicators, and clear error feedback.
*   **Secure API Key Handling**: Supports API key via `.env` file (recommended) or manual input via a modal (stored in browser's `localStorage`).

## Tech Stack

*   **Frontend**: React, TypeScript
*   **Build Tool**: Vite
*   **Styling**: Tailwind CSS (with `@tailwindcss/typography` for Markdown)
*   **AI**: Google Gemini API (`@google/generai`)

## Prerequisites

*   Node.js (v18.x or later recommended)
*   npm, yarn, or pnpm

## Setup and Installation

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/immunizedcarbon/Test
    cd Test
    ```

2.  **Install dependencies:**
    ```bash
    npm install
    # or yarn install / pnpm install
    ```

3.  **Set up your Google Gemini API Key:**

    This application requires a Google Gemini API Key to function.

    *   **Recommended Method (.env file):**
        1.  In the root of the project, copy the `.env.example` file to a new file named `.env`:
            ```bash
            cp .env.example .env
            ```
        2.  Open the `.env` file in your text editor.
        3.  Replace `YOUR_API_KEY_HERE` with your actual Google Gemini API Key:
            ```env
            VITE_GEMINI_API_KEY=AIz...YOUR_ACTUAL_KEY...xyz
            ```
        4.  Save the file. The `.env` file is included in `.gitignore` and **should never be committed to your repository.**

    *   **Alternative Method (In-App Modal):**
        If you do not set up the `.env` file, the application will prompt you to enter your API key via a modal when you first run it. This key will be stored in your browser's `localStorage` for convenience during the session.

## Running the Application Locally

1.  **Start the development server:**
    ```bash
    npm run dev
    # or yarn dev / pnpm dev
    ```

2.  Open your web browser and navigate to the URL shown in your terminal (usually `http://localhost:5173`).

## Usage

1.  **Enter API Key**: If prompted, enter your Google Gemini API Key.
2.  **Enter YouTube URL**: Paste the full URL of the YouTube video you want to transcribe into the input field.
3.  **Transcribe**: Click the "Transcribe" button. Progress will be shown below the input form.
4.  **View Outputs**:
    *   The **Transcription** tab will show the live transcription.
    *   Navigate to the **Summary** tab and click "Generate Summary" to get a summary of the transcription.
    *   Navigate to the **Translation** tab, select your target language, and click "Translate" to translate the transcription.
5.  **Save Outputs**: Each view (Transcription, Summary, Translation) has a "Save" button to download the respective text as a `.txt` file.

## Project Structure (Overview)

```
/
├── public/               # Static assets
├── src/
│   ├── components/       # React UI Components
│   ├── services/         # Gemini API interaction logic (geminiService.ts)
│   ├── utils/            # Utility functions (textUtils.ts)
│   ├── assets/           # Static assets like images/styles for components (if any)
│   ├── constants.ts      # Application-wide constants
│   ├── types.ts          # TypeScript type definitions
│   ├── App.tsx           # Main application component
│   ├── main.tsx          # React entry point
│   └── index.css         # Global styles & Tailwind directives
├── .env.example          # Example environment variables file
├── .gitignore            # Specifies intentionally untracked files that Git should ignore
├── index.html            # Main HTML page
├── package.json
├── README.md             # This file
├── tailwind.config.js    # Tailwind CSS configuration
└── vite.config.ts        # Vite configuration
```

## Security Notes

*   **API Key Security**: Your Google Gemini API Key is sensitive.
    *   **NEVER** hardcode it directly into the source code.
    *   **NEVER** commit your `.env` file (containing your actual key) to version control. The provided `.gitignore` prevents this.
    *   If you use the in-app modal, the key is stored in your browser's `localStorage`. Be mindful of this if you are on a shared computer.
*   **Responsible Use**: Only transcribe content for which you have the necessary rights or permissions.

## "State-of-the-Art" Considerations

This project endeavors to be "state-of-the-art" for a personal tool by:
*   Utilizing a modern technology stack (Vite, React, TypeScript, Tailwind CSS).
*   Integrating with the powerful Google Gemini API for AI tasks.
*   Implementing streaming for live transcription updates.
*   Focusing on a clean, responsive, and user-friendly interface with clear feedback mechanisms.
*   Providing robust error handling.
*   Ensuring secure API key management practices for local development.

---
*This application is for personal and educational purposes.*
```
