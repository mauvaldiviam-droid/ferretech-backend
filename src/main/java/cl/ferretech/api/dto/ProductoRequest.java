package cl.ferretech.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Datos para crear un producto. La imagen y los detalles son opcionales (tienen valores por defecto). */
public record ProductoRequest(
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 120, message = "El nombre no puede superar los 120 caracteres.")
        String nombre,

        @NotNull(message = "El precio es obligatorio.")
        @PositiveOrZero(message = "El precio no puede ser negativo.")
        Integer precio,

        @NotNull(message = "El stock es obligatorio.")
        @PositiveOrZero(message = "El stock no puede ser negativo.")
        Integer stock,

        @Size(max = 500, message = "La ruta de la imagen no puede superar los 500 caracteres.")
        String imagen,

        @Size(max = 1000, message = "Los detalles no pueden superar los 1000 caracteres.")
        String detalles) {
}
