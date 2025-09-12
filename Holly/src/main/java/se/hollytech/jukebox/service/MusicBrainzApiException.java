package se.hollytech.jukebox.service;

public class MusicBrainzApiException extends RuntimeException {
    public MusicBrainzApiException(String message, Throwable cause) {
        super(message, cause);
    }
}

