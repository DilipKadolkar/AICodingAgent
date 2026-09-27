package com.codecafe.aicodingagent.exception;

/**
 * Base type for all failures talking to the local Ollama model, so callers
 * can distinguish connection, availability, timeout, and protocol failures
 * instead of catching a generic exception.
 */
public sealed class OllamaException extends RuntimeException
        permits OllamaException.ConnectionFailed,
                OllamaException.ModelNotAvailable,
                OllamaException.RequestTimedOut,
                OllamaException.MalformedResponse {

    private OllamaException(String message, Throwable cause) {
        super(message, cause);
    }

    public static final class ConnectionFailed extends OllamaException {
        public ConnectionFailed(String baseUrl, Throwable cause) {
            super("Could not reach the local Ollama server at " + baseUrl
                    + ". Make sure Ollama is running.", cause);
        }
    }

    public static final class ModelNotAvailable extends OllamaException {
        public ModelNotAvailable(String model) {
            super("The model '" + model + "' is not available in Ollama. "
                    + "Pull it first with: ollama pull " + model, null);
        }
    }

    public static final class RequestTimedOut extends OllamaException {
        public RequestTimedOut(String baseUrl, Throwable cause) {
            super("The request to Ollama at " + baseUrl + " timed out.", cause);
        }
    }

    public static final class MalformedResponse extends OllamaException {
        public MalformedResponse(String detail, Throwable cause) {
            super("Ollama returned a response that could not be understood: " + detail, cause);
        }
    }
}
