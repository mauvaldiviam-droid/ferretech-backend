package cl.ferretech.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ItemPedidoRequest(
        @NotNull(message = "Falta el producto.")
        Long productoId,

        @NotNull(message = "La cantidad debe ser al menos 1.")
        @Positive(message = "La cantidad debe ser al menos 1.")
        Integer cantidad) {
}
