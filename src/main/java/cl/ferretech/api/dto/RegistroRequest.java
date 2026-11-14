package cl.ferretech.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistroRequest(
        @NotBlank(message = "Completa usuario y contraseña.")
        @Size(max = 50, message = "El usuario no puede superar los 50 caracteres.")
        String usuario,

        @NotBlank(message = "Completa usuario y contraseña.")
        @Size(max = 100, message = "La contraseña no puede superar los 100 caracteres.")
        String contrasena) {
}
