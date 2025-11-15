package ru.practicum.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

@Data
public class NewCommentDto {

    @NotBlank(message = "Text cannot be blank")
    @Size(min = 1, max = 2000, message = "Text must be between 1 and 2000 characters")
    private String text;

    @NotNull(message = "Event ID cannot be null")
    private Long eventId;
}