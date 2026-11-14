package cl.ferretech.api.dto;

import cl.ferretech.api.domain.PedidoItem;

public record ItemPedidoResponse(Long productoId, String nombre, int precioUnitario, int cantidad, long subtotal) {

    public static ItemPedidoResponse de(PedidoItem i) {
        return new ItemPedidoResponse(
                i.getProductoId(),
                i.getNombreProducto(),
                i.getPrecioUnitario(),
                i.getCantidad(),
                (long) i.getPrecioUnitario() * i.getCantidad());
    }
}
