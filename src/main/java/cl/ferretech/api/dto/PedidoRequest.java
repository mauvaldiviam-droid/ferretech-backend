package cl.ferretech.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record PedidoRequest(
        @NotEmpty(message = "El pedido no tiene productos.")
        List<@Valid ItemPedidoRequest> items) {
}
