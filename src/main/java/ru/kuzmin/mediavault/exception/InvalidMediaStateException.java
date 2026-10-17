package ru.kuzmin.mediavault.exception;

public class InvalidMediaStateException extends RuntimeException {
    public InvalidMediaStateException(String message) {
        super(message);
    }
}
