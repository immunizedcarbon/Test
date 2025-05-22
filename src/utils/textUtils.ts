// src/utils/textUtils.ts

/**
 * Cleans a YouTube URL and extracts the video ID.
 * @param youtubeUrl The YouTube URL to clean.
 * @returns An object with the clean URL and video ID, or null if invalid.
 */
export function cleanYoutubeUrl(youtubeUrl: string): { cleanUrl: string | null; videoId: string | null } {
  if (!youtubeUrl || typeof youtubeUrl !== 'string') {
    return { cleanUrl: null, videoId: null };
  }

  let videoId: string | null = null;

  // Regular expressions to match various YouTube URL patterns
  const patterns = [
    /(?:https?:\/\/)?(?:www\.)?youtube\.com\/watch\?v=([a-zA-Z0-9_-]{11})/,      // Standard watch URL
    /(?:https?:\/\/)?(?:www\.)?youtu\.be\/([a-zA-Z0-9_-]{11})/,                  // Shortened youtu.be URL
    /(?:https?:\/\/)?(?:www\.)?youtube\.com\/embed\/([a-zA-Z0-9_-]{11})/,       // Embed URL
    /(?:https?:\/\/)?(?:www\.)?youtube\.com\/live\/([a-zA-Z0-9_-]{11})/,         // Live URL
    /(?:https?:\/\/)?(?:www\.)?youtube\.com\/shorts\/([a-zA-Z0-9_-]{11})/,      // Shorts URL
    /(?:https?:\/\/)?(?:www\.)?youtube\.com\/v\/([a-zA-Z0-9_-]{11})/,            // Legacy v/ URL
    /(?:https?:\/\/)?(?:www\.)?youtube\.com\/attribution_link\?a=.*?&u=%2Fwatch%3Fv%3D([a-zA-Z0-9_-]{11})(&.*|%26.*)?/ // Attribution link
  ];

  for (const pattern of patterns) {
    const match = youtubeUrl.match(pattern);
    if (match && match[1]) {
      videoId = match[1];
      break;
    }
  }

  if (videoId) {
    return { cleanUrl: `https://www.youtube.com/watch?v=${videoId}`, videoId: videoId };
  } else {
    // Check for URLs that might just contain the ID without the typical path
    const idOnlyMatch = youtubeUrl.match(/^([a-zA-Z0-9_-]{11})$/);
    if (idOnlyMatch && idOnlyMatch[1]) {
      videoId = idOnlyMatch[1];
      return { cleanUrl: `https://www.youtube.com/watch?v=${videoId}`, videoId: videoId };
    }
    return { cleanUrl: null, videoId: null };
  }
}

// Helper for escaping HTML special characters
function escapeHtml(text: string): string {
  if (typeof text !== 'string') return '';
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

/**
 * Converts basic Markdown text to HTML.
 * XSS Prevention: Escapes HTML special characters in the input text first.
 * Supports:
 * - Bold: **text** or __text__
 * - Italic: *text* or _text_
 * - Speaker Labels: **Speaker X:** or Speaker X: (makes the label bold)
 * - Unordered lists: - item or * item
 * - Ordered lists: 1. item
 * - Inline code: `code`
 * - Code blocks: ```code block```
 * - Paragraphs (double line breaks) and line breaks (single line breaks).
 * @param markdownText The Markdown text to convert.
 * @returns HTML string.
 */
export function markdownToHtml(markdownText: string): string {
  if (typeof markdownText !== 'string' || !markdownText.trim()) {
    return '';
  }

  let html = escapeHtml(markdownText);

  // Code blocks (```...```) - process first to avoid inner markdown processing
  // Must handle multi-line code blocks correctly
  html = html.replace(/```([\s\S]*?)```/g, (match, codeContent) => {
    // Code inside ``` already escaped by initial escapeHtml.
    // We just need to wrap it in <pre><code>
    return `<pre><code>${codeContent.trim()}</code></pre>`;
  });
  
  // Inline code (`code`) - Escape content again if needed, though initial escapeHtml should handle it.
  html = html.replace(/`([^`]+?)`/g, (match, codeContent) => {
      // content already escaped by the initial escapeHtml call
      return `<code>${codeContent}</code>`;
  });

  // Speaker labels (example: **Speaker X:** or Speaker X:) - make bold
  // This regex tries to be more specific: starts at the beginning of a line,
  // captures "Speaker" followed by a letter or number, and a colon.
  // It handles optional bolding around the speaker label itself.
  html = html.replace(/^(?:<strong>)?(Speaker\s[A-Za-z0-9]+:)(?:<\/strong>)?/gm, '<strong>$1</strong>');


  // Bold
  html = html.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
  html = html.replace(/__(.*?)__/g, '<strong>$1</strong>');

  // Italic
  // Ensure it doesn't conflict with list markers by checking space or start/end of line
  html = html.replace(/(^|\s)\*(?!\s)(.*?)(?!\s)\*(\s|$)/g, '$1<em>$2</em>$3');
  html = html.replace(/(^|\s)_(?!\s)(.*?)(?!\s)_(\s|$)/g, '$1<em>$2</em>$3');


  // Handling lists (unordered and ordered)
  // This is a simplified approach. A more robust parser would be needed for complex cases.
  // Unordered lists
  html = html.replace(/^[\s]*[-\*]\s+(.*)/gm, (match, item) => {
    return `<ul><li>${item.trim()}</li></ul>`;
  });
  // Merge adjacent <ul> tags
  html = html.replace(/<\/ul>\n?<ul>/g, '');

  // Ordered lists
  html = html.replace(/^[\s]*[0-9]+\.\s+(.*)/gm, (match, item) => {
    return `<ol><li>${item.trim()}</li></ol>`;
  });
  // Merge adjacent <ol> tags
  html = html.replace(/<\/ol>\n?<ol>/g, '');

  // Paragraphs and line breaks
  // Split by lines, wrap non-list lines in <p>, then join.
  // This is a basic approach and might need refinement for complex HTML structures.
  const lines = html.split(/\n/);
  let inParagraph = false;
  html = lines.map(line => {
    line = line.trim(); // Trim whitespace from lines
    if (!line) { // Empty line
      if (inParagraph) {
        inParagraph = false;
        return '</p>';
      }
      return '';
    }
    if (line.startsWith('<li>') || line.startsWith('<ul>') || line.startsWith('<ol>') || line.startsWith('<pre>') || line.startsWith('<code>')) {
      // If it's already part of a list or code block, don't wrap in <p>
      if (inParagraph) {
        inParagraph = false;
        return '</p>' + line;
      }
      return line;
    }
    if (!inParagraph) {
      inParagraph = true;
      return '<p>' + line;
    }
    return '<br />' + line; // If already in paragraph, treat as line break within it.
  }).join('');

  if (inParagraph) { // Close any open paragraph
    html += '</p>';
  }
  // Remove empty paragraphs that might have been created
  html = html.replace(/<p>\s*<\/p>/g, '');
  html = html.replace(/<p><\/p>/g, ''); // for good measure

  return html;
}


/**
 * Sanitizes a string for use as a filename.
 * Replaces or removes characters not suitable for filenames.
 * @param title The string to sanitize.
 * @returns A sanitized string.
 */
function sanitizeVideoTitleForFilename(title: string): string {
  if (!title) return 'untitled';
  // Remove or replace characters that are problematic in filenames
  // Keep alphanumeric, spaces, hyphens, underscores. Replace others.
  let sanitized = title.replace(/[^\w\s\-\.]/g, '_'); // Replace non-word chars (excluding '.', '-' for extensions/common use) with underscore
  sanitized = sanitized.replace(/\s+/g, '_'); // Replace spaces with underscores
  sanitized = sanitized.replace(/_{2,}/g, '_'); // Replace multiple underscores with a single one
  sanitized = sanitized.substring(0, 100); // Limit length to avoid overly long filenames
  return sanitized;
}

/**
 * Saves text content to a file and triggers a download.
 * @param text The text content to save.
 * @param prefix The prefix for the filename (e.g., "TRANSCRIPT", "SUMMARY").
 * @param videoTitle The original title of the video.
 * @param videoId The YouTube video ID.
 */
export function saveTextToFile(text: string, prefix: string, videoTitle: string, videoId: string): void {
  const date = new Date().toISOString().split('T')[0]; // YYYY-MM-DD
  const sanitizedTitle = sanitizeVideoTitleForFilename(videoTitle || videoId || 'video');

  const filename = `${prefix}_${sanitizedTitle}_${videoId}_${date}.txt`;

  const metadataHeader = `Video Title: ${videoTitle || 'N/A'}\nVideo ID: ${videoId}\nDate Saved: ${date}\n\n---\n\n`;
  const contentToSave = metadataHeader + text;

  const blob = new Blob([contentToSave], { type: 'text/plain;charset=utf-S8' });
  const url = URL.createObjectURL(blob);

  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();

  // Cleanup
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}
