"""
Vietnamese Sentiment Analysis Service

This module provides a command-line interface for performing sentiment analysis
on Vietnamese text using the Vietnamese-Sentiment-visobert model. The service
reads text from standard input and outputs sentiment labels to standard output.

The module utilizes the Hugging Face Transformers library with a specialized
Vietnamese sentiment analysis model (5CD-AI/Vietnamese-Sentiment-visobert).
Processing occurs line-by-line with real-time output for integration with
other services via stdin/stdout communication.

Usage:
    The service reads text input line-by-line from stdin and outputs the
    corresponding sentiment label for each line. Send "<<STOP>>" to terminate
    the service gracefully.

Performance Metrics:
    The module tracks and reports loading times for imports and model
    initialization to stderr for monitoring purposes.
"""

import sys
import time
from typing import Optional

from transformers import pipeline


MODEL_NAME = "5CD-AI/Vietnamese-Sentiment-visobert"
STOP_COMMAND = "<<STOP>>"


def load_sentiment_model():
    """
    Load and initialize the Vietnamese sentiment analysis model.

    Returns:
        The initialized sentiment analysis pipeline.
    """
    start = time.time()
    sentiment_task = pipeline(
        "sentiment-analysis",
        model=MODEL_NAME,
        tokenizer=MODEL_NAME
    )  # type: ignore
    end = time.time()
    print(f"Model loaded in {end - start:.3f} seconds", file=sys.stderr, flush=True)
    return sentiment_task


def analyze_sentiment(text: str, sentiment_task) -> Optional[str]:
    """
    Analyze the sentiment of the given text.

    Args:
        text: The text to analyze.
        sentiment_task: The sentiment analysis pipeline.

    Returns:
        The sentiment label or None if analysis fails.
    """
    try:
        result = sentiment_task(text)
        return result[0]["label"]
    except Exception as e:
        print(f"Error analyzing text: {e}", file=sys.stderr, flush=True)
        return None


def process_stdin(sentiment_task):
    """
    Process text from stdin line-by-line and output sentiment labels.

    Args:
        sentiment_task: The sentiment analysis pipeline.
    """
    while True:
        line = sys.stdin.readline()
        if not line:
            break

        text = line.strip()
        if not text:
            continue

        if text == STOP_COMMAND:
            break

        label = analyze_sentiment(text, sentiment_task)
        if label:
            print(label, flush=True)


def main():
    """Main entry point for the sentiment analysis service."""
    start = time.time()

    # Load the sentiment model
    sentiment_task = load_sentiment_model()

    end = time.time()
    print(f"Service ready in {end - start:.3f} seconds", file=sys.stderr, flush=True)

    # Process input from stdin
    process_stdin(sentiment_task)


if __name__ == "__main__":
    main()
