// src/components/ProgressBarDisplay.tsx
import React from 'react';

interface ProgressBarDisplayProps {
  progress?: number; // Optional: 0-100 for determinate progress
  status: string;
  error?: string | null;
  isVisible: boolean;
}

export const ProgressBarDisplay: React.FC<ProgressBarDisplayProps> = ({
  progress,
  status,
  error,
  isVisible,
}) => {
  if (!isVisible && !error) {
    return null;
  }

  const barColor = error ? 'bg-red-500' : 'bg-sky-600';
  const textColor = error ? 'text-red-300' : 'text-sky-300';
  const statusColor = error ? 'text-red-400' : 'text-gray-300';

  return (
    <div className="mb-4 p-3 bg-gray-800 rounded-md">
      {error ? (
        <p className={`text-sm font-semibold ${statusColor}`}>{error}</p>
      ) : (
        <>
          <p className={`text-sm font-medium ${textColor} mb-1`}>{status}</p>
          {progress !== undefined ? (
            <div className="w-full bg-gray-700 rounded-full h-2.5">
              <div
                className={`${barColor} h-2.5 rounded-full transition-all duration-300 ease-out`}
                style={{ width: `${progress}%` }}
              ></div>
            </div>
          ) : (
            // Indeterminate progress (e.g., pulsing bar)
            <div className="w-full bg-gray-700 rounded-full h-2.5 overflow-hidden">
              <div
                className={`${barColor} h-2.5 w-1/3 rounded-full animate-pulse`}
              ></div>
            </div>
          )}
        </>
      )}
    </div>
  );
};

export default ProgressBarDisplay;
