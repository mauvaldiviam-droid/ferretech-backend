package cl.ferretech.api.dto;

import cl.ferretech.api.domain.Pedido;
import java.util.List;

public record PedidoResponse(Long id, String fecha, long total, List<ItemPedidoResponse> items) {

    public static PedidoResponse de(Pedido p) {
        return new PedidoResponse(
                p.getId(),
                p.getFecha().toString(),
                p.getTotal(),
                p.getItems().stream().map(ItemPedidoResponse::de).toList());
    }
}
