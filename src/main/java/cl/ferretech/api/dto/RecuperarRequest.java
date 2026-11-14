package cl.ferretech.api.dto;

import jakarta.validation.constraints.NotBlank;

public record RecuperarRequest(
        @NotBlank(message = "Ingresa tu nombre de usuario.")
        String usuario) {
}
