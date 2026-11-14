package cl.ferretech.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RestablecerRequest(
        @NotBlank(message = "Ingresa tu nombre de usuario.")
        String usuario,

        @NotBlank(message = "Ingresa el código que enviamos a tu correo.")
        String codigo,

        @NotBlank(message = "Ingresa tu nueva contraseña.")
        @Size(max = 100, message = "La contraseña no puede superar los 100 caracteres.")
        String nuevaContrasena) {
}
