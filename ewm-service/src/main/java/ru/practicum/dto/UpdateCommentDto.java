package ru.practicum.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
public class UpdateCommentDto {

    @NotBlank(message = "Text cannot be blank")
    @Size(min = 1, max = 2000, message = "Text must be between 1 and 2000 characters")
    private String text;
}