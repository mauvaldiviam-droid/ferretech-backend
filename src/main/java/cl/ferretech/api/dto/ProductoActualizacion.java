package cl.ferretech.api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Cambios parciales: solo se modifican los campos que vienen informados (no nulos). */
public record ProductoActualizacion(
        @Pattern(regexp = ".*\\S.*", message = "El nombre no puede estar vacío.")
        @Size(max = 120, message = "El nombre no puede superar los 120 caracteres.")
        String nombre,

        @PositiveOrZero(message = "El precio no puede ser negativo.")
        Integer precio,

        @PositiveOrZero(message = "El stock no puede ser negativo.")
        Integer stock,

        @Size(max = 500, message = "La ruta de la imagen no puede superar los 500 caracteres.")
        String imagen,

        @Size(max = 1000, message = "Los detalles no pueden superar los 1000 caracteres.")
        String detalles) {
}
