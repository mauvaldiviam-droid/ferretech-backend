package cl.ferretech.api.service;

import cl.ferretech.api.domain.Pedido;
import cl.ferretech.api.domain.PedidoItem;
import cl.ferretech.api.domain.Producto;
import cl.ferretech.api.dto.ItemPedidoRequest;
import cl.ferretech.api.dto.PedidoRequest;
import cl.ferretech.api.dto.PedidoResponse;
import cl.ferretech.api.error.ApiException;
import cl.ferretech.api.repository.PedidoRepository;
import cl.ferretech.api.repository.ProductoRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PedidoService {

    private final ProductoRepository productos;
    private final PedidoRepository pedidos;

    public PedidoService(ProductoRepository productos, PedidoRepository pedidos) {
        this.productos = productos;
        this.pedidos = pedidos;
    }

    /**
     * Registra la compra y descuenta el stock. Es todo o nada: si un producto no tiene stock
     * suficiente se lanza una excepción y la transacción deshace los descuentos ya hechos.
     */
    @Transactional
    public PedidoResponse crear(String usuario, PedidoRequest solicitud) {
        // Si el mismo producto viene repetido, se suman las cantidades.
        Map<Long, Integer> cantidades = new LinkedHashMap<>();
        for (ItemPedidoRequest item : solicitud.items()) {
            cantidades.merge(item.productoId(), item.cantidad(), Integer::sum);
        }

        Pedido pedido = new Pedido(usuario, Instant.now());
        long total = 0;
        for (Map.Entry<Long, Integer> linea : cantidades.entrySet()) {
            Long productoId = linea.getKey();
            int cantidad = linea.getValue();

            Producto producto = productos.findById(productoId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                            "Producto no encontrado: " + productoId + "."));

            if (productos.descontarStock(productoId, cantidad) == 0) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Stock insuficiente para \"" + producto.getNombre() + "\".");
            }

            pedido.agregarItem(new PedidoItem(productoId, producto.getNombre(), producto.getPrecio(), cantidad));
            total += (long) producto.getPrecio() * cantidad;
        }
        pedido.setTotal(total);

        return PedidoResponse.de(pedidos.save(pedido));
    }
}
