package se.hollytech.jukebox.controller;

import se.hollytech.jukebox.service.ArtistNotFoundException;
import se.hollytech.jukebox.service.MusicBrainzApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        logger.warn("Invalid request: error={}", ex.getMessage());
        ErrorResponse error = new ErrorResponse("Bad Request", ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ArtistNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleArtistNotFoundException(ArtistNotFoundException ex) {
        logger.warn("Artist not found: error={}", ex.getMessage());
        ErrorResponse error = new ErrorResponse("Not Found", ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(MusicBrainzApiException.class)
    public ResponseEntity<ErrorResponse> handleMusicBrainzApiException(MusicBrainzApiException ex) {
        String message = ex.getMessage();
        HttpStatus status = message.contains("Rate limit exceeded") ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.INTERNAL_SERVER_ERROR;
        logger.error("MusicBrainz API error: error={}, status={}", message, status);
        ErrorResponse error = new ErrorResponse(status.getReasonPhrase(), message);
        return new ResponseEntity<>(error, status);
    }

    private static class ErrorResponse {
        private final String error;
        private final String message;

        public ErrorResponse(String error, String message) {
            this.error = error;
            this.message = message;
        }

        public String getError() {
            return error;
        }

        public String getMessage() {
            return message;
        }
    }
}