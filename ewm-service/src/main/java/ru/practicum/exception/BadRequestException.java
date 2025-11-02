package ru.practicum.exception;

/**
 * Исключение при неверных параметрах запроса
 */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}