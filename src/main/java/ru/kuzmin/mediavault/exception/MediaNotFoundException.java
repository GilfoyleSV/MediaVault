package ru.kuzmin.mediavault.exception;

public class MediaNotFoundException extends RuntimeException {
    public MediaNotFoundException(String message) {
        super(message);
    }
}
