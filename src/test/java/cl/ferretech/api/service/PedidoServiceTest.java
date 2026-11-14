package cl.ferretech.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cl.ferretech.api.domain.Pedido;
import cl.ferretech.api.domain.Producto;
import cl.ferretech.api.dto.ItemPedidoRequest;
import cl.ferretech.api.dto.ItemPedidoResponse;
import cl.ferretech.api.dto.PedidoRequest;
import cl.ferretech.api.dto.PedidoResponse;
import cl.ferretech.api.error.ApiException;
import cl.ferretech.api.repository.PedidoRepository;
import cl.ferretech.api.repository.ProductoRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PedidoServiceTest {

    @Mock
    private ProductoRepository productos;

    @Mock
    private PedidoRepository pedidos;

    private PedidoService servicio;
    private Producto brocha;
    private Producto tarro;

    @BeforeEach
    void preparar() {
        servicio = new PedidoService(productos, pedidos);

        brocha = producto(1L, "Brocha", 5990, 12);
        tarro = producto(2L, "Tarro de pintura", 45990, 5);
        when(productos.findById(1L)).thenReturn(Optional.of(brocha));
        when(productos.findById(2L)).thenReturn(Optional.of(tarro));
        when(productos.descontarStock(any(Long.class), any(Integer.class))).thenReturn(1);
        when(pedidos.save(any(Pedido.class))).thenAnswer(llamada -> llamada.getArgument(0));
    }

    private Producto producto(Long id, String nombre, int precio, int stock) {
        Producto producto = new Producto(nombre, precio, stock, "img/a.jpg", "detalle");
        ReflectionTestUtils.setField(producto, "id", id);
        return producto;
    }

    private PedidoRequest pedido(ItemPedidoRequest... items) {
        return new PedidoRequest(List.of(items));
    }

    private ApiException errorDe(Runnable accion) {
        try {
            accion.run();
        } catch (ApiException e) {
            return e;
        }
        throw new AssertionError("Se esperaba una ApiException");
    }

    @Test
    void crear_descuentaElStockYCalculaElTotal() {
        PedidoResponse respuesta = servicio.crear("Mauricio",
                pedido(new ItemPedidoRequest(2L, 2), new ItemPedidoRequest(1L, 1)));

        // 2 x 45.990 + 1 x 5.990
        assertThat(respuesta.total()).isEqualTo(97970L);
        assertThat(respuesta.items()).hasSize(2);
        verify(productos).descontarStock(2L, 2);
        verify(productos).descontarStock(1L, 1);
    }

    @Test
    void crear_guardaElPedidoConElUsuarioDelToken() {
        servicio.crear("Mauricio", pedido(new ItemPedidoRequest(1L, 1)));

        org.mockito.ArgumentCaptor<Pedido> guardado = org.mockito.ArgumentCaptor.forClass(Pedido.class);
        verify(pedidos).save(guardado.capture());
        assertThat(guardado.getValue().getUsuario()).isEqualTo("Mauricio");
        assertThat(guardado.getValue().getFecha()).isNotNull();
    }

    @Test
    void crear_guardaElNombreYElPrecioVigentesEnElMomentoDeLaCompra() {
        PedidoResponse respuesta = servicio.crear("Mauricio", pedido(new ItemPedidoRequest(2L, 3)));

        ItemPedidoResponse linea = respuesta.items().get(0);
        assertThat(linea.productoId()).isEqualTo(2L);
        assertThat(linea.nombre()).isEqualTo("Tarro de pintura");
        assertThat(linea.precioUnitario()).isEqualTo(45990);
        assertThat(linea.cantidad()).isEqualTo(3);
        assertThat(linea.subtotal()).isEqualTo(137970L);
    }

    @Test
    void crear_sumaLasCantidadesDeUnMismoProducto() {
        PedidoResponse respuesta = servicio.crear("Mauricio",
                pedido(new ItemPedidoRequest(1L, 1), new ItemPedidoRequest(1L, 2)));

        assertThat(respuesta.items()).hasSize(1);
        assertThat(respuesta.items().get(0).cantidad()).isEqualTo(3);
        verify(productos).descontarStock(1L, 3);
    }

    @Test
    void crear_rechazaSiNoHayStockSuficiente() {
        when(productos.descontarStock(1L, 50)).thenReturn(0);

        ApiException error = errorDe(() -> servicio.crear("Mauricio", pedido(new ItemPedidoRequest(1L, 50))));

        assertThat(error.getEstado()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(error.getMessage()).isEqualTo("Stock insuficiente para \"Brocha\".");
        verify(pedidos, never()).save(any(Pedido.class));
    }

    @Test
    void crear_rechazaUnProductoInexistente() {
        when(productos.findById(999L)).thenReturn(Optional.empty());

        ApiException error = errorDe(() -> servicio.crear("Mauricio", pedido(new ItemPedidoRequest(999L, 1))));

        assertThat(error.getEstado()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(error.getMessage()).isEqualTo("Producto no encontrado: 999.");
        verify(pedidos, never()).save(any(Pedido.class));
    }
}
