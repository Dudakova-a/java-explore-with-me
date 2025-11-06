package ru.practicum.exception;

/**
 * Исключение при ненайденной сущности
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}