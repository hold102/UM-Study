package my.edu.um.study.auth;

import jakarta.validation.constraints.NotBlank;

public record SpectrumLoginRequest(@NotBlank String token) {
}
