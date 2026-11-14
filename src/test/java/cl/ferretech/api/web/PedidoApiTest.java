package cl.ferretech.api.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.ferretech.api.domain.Producto;
import cl.ferretech.api.repository.PedidoRepository;
import cl.ferretech.api.repository.ProductoRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

class PedidoApiTest extends ApiTestBase {

    @Autowired
    private ProductoRepository productos;

    @Autowired
    private PedidoRepository pedidos;

    private final List<Long> productosCreados = new ArrayList<>();

    /** Cada prueba crea sus propios productos para no alterar el stock del catálogo inicial. */
    private Producto crearProducto(String nombre, int precio, int stock) {
        Producto guardado = productos.save(new Producto(nombre, precio, stock, "img/a.jpg", "detalle de prueba"));
        productosCreados.add(guardado.getId());
        return guardado;
    }

    private int stockDe(Producto producto) {
        return productos.findById(producto.getId()).orElseThrow().getStock();
    }

    private static String item(Long productoId, int cantidad) {
        return "{\"productoId\":" + productoId + ",\"cantidad\":" + cantidad + "}";
    }

    private static String pedido(String... items) {
        return "{\"items\":[" + String.join(",", items) + "]}";
    }

    @AfterEach
    void limpiar() {
        pedidos.deleteAll();
        productosCreados.forEach(productos::deleteById);
        productosCreados.clear();
    }

    @Test
    void crear_exigeSesion() throws Exception {
        mvc.perform(enviarJson(post("/api/pedidos"), pedido(item(1L, 1)))).andExpect(status().isUnauthorized());
    }

    @Test
    void crear_descuentaElStockYDevuelveElTotal() throws Exception {
        Producto martillo = crearProducto("Martillo de prueba", 1000, 5);

        MvcResult resultado = mvc.perform(enviarJson(post("/api/pedidos"), pedido(item(martillo.getId(), 2)))
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDeCliente())))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(cuerpo(resultado)).contains("\"total\":2000").contains("\"cantidad\":2")
                .contains("Martillo de prueba");
        assertThat(stockDe(martillo)).isEqualTo(3);
        assertThat(pedidos.findAll()).hasSize(1);
        assertThat(pedidos.findAll().get(0).getUsuario()).isEqualTo("Mauricio");
    }

    @Test
    void crear_tambienLoPuedeHacerUnAdministrador() throws Exception {
        Producto serrucho = crearProducto("Serrucho de prueba", 500, 2);

        mvc.perform(enviarJson(post("/api/pedidos"), pedido(item(serrucho.getId(), 1)))
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDeAdmin())))
                .andExpect(status().isCreated());

        assertThat(stockDe(serrucho)).isEqualTo(1);
    }

    @Test
    void crear_sePuedeComprarTodoElStockDisponible() throws Exception {
        Producto ultimo = crearProducto("Ultima unidad de prueba", 700, 1);

        mvc.perform(enviarJson(post("/api/pedidos"), pedido(item(ultimo.getId(), 1)))
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDeCliente())))
                .andExpect(status().isCreated());

        assertThat(stockDe(ultimo)).isZero();
    }

    @Test
    void crear_rechazaSiNoHayStockSuficiente() throws Exception {
        Producto escaso = crearProducto("Producto escaso de prueba", 1000, 1);

        MvcResult resultado = mvc.perform(enviarJson(post("/api/pedidos"), pedido(item(escaso.getId(), 2)))
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDeCliente())))
                .andExpect(status().isConflict())
                .andReturn();

        assertThat(cuerpo(resultado)).contains("Stock insuficiente para \\\"Producto escaso de prueba\\\".");
        assertThat(stockDe(escaso)).isEqualTo(1);
        assertThat(pedidos.findAll()).isEmpty();
    }

    @Test
    void crear_esTodoONada_siUnProductoFallaSeDeshaceElDescuentoDeLosAnteriores() throws Exception {
        Producto abundante = crearProducto("Producto abundante de prueba", 100, 5);
        Producto escaso = crearProducto("Producto escaso 2 de prueba", 100, 1);

        // El primer producto alcanza y se descuenta; el segundo no alcanza: debe revertirse todo.
        mvc.perform(enviarJson(post("/api/pedidos"), pedido(item(abundante.getId(), 2), item(escaso.getId(), 3)))
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDeCliente())))
                .andExpect(status().isConflict());

        assertThat(stockDe(abundante)).isEqualTo(5);
        assertThat(stockDe(escaso)).isEqualTo(1);
        assertThat(pedidos.findAll()).isEmpty();
    }

    @Test
    void crear_unProductoInexistenteResponde404() throws Exception {
        MvcResult resultado = mvc.perform(enviarJson(post("/api/pedidos"), pedido(item(999999L, 1)))
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDeCliente())))
                .andExpect(status().isNotFound())
                .andReturn();

        assertThat(cuerpo(resultado)).contains("Producto no encontrado: 999999.");
    }

    @Test
    void crear_rechazaUnPedidoSinProductos() throws Exception {
        MvcResult resultado = mvc.perform(enviarJson(post("/api/pedidos"), pedido())
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDeCliente())))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(cuerpo(resultado)).contains("El pedido no tiene productos.");
    }

    @Test
    void crear_rechazaUnaCantidadMenorAUno() throws Exception {
        MvcResult resultado = mvc.perform(enviarJson(post("/api/pedidos"), pedido(item(1L, 0)))
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDeCliente())))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(cuerpo(resultado)).contains("La cantidad debe ser al menos 1.");
    }
}
