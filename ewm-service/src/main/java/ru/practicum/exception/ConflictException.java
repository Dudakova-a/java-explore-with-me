package ru.practicum.exception;

/**
 * Исключение при конфликте данных
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}