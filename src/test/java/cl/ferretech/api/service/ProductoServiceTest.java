package cl.ferretech.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cl.ferretech.api.domain.Producto;
import cl.ferretech.api.dto.ProductoActualizacion;
import cl.ferretech.api.dto.ProductoRequest;
import cl.ferretech.api.dto.ProductoResponse;
import cl.ferretech.api.error.ApiException;
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
class ProductoServiceTest {

    @Mock
    private ProductoRepository productos;

    private ProductoService servicio;

    @BeforeEach
    void preparar() {
        servicio = new ProductoService(productos);
        // save() devuelve lo mismo que recibe (la base real además asigna el id).
        when(productos.save(any(Producto.class))).thenAnswer(llamada -> llamada.getArgument(0));
    }

    private Producto brocha() {
        Producto producto = new Producto("Brocha", 5990, 12, "img/images.jpg", "Brocha de cerdas");
        ReflectionTestUtils.setField(producto, "id", 1L);
        return producto;
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
    void listar_entregaLosProductosOrdenadosComoVienenDeLaBase() {
        Producto tarro = new Producto("Tarro de pintura", 45990, 5, "img/a.jpg", "Pintura");
        when(productos.findAllByOrderByIdAsc()).thenReturn(List.of(brocha(), tarro));

        List<ProductoResponse> lista = servicio.listar();

        assertThat(lista).extracting(ProductoResponse::nombre).containsExactly("Brocha", "Tarro de pintura");
    }

    @Test
    void obtener_fallaSiElProductoNoExiste() {
        when(productos.findById(99L)).thenReturn(Optional.empty());

        ApiException error = errorDe(() -> servicio.obtener(99L));

        assertThat(error.getEstado()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(error.getMessage()).isEqualTo("Producto no encontrado.");
    }

    @Test
    void crear_guardaLosDatosEntregados() {
        ProductoResponse creado = servicio.crear(
                new ProductoRequest("  Taladro  ", 39990, 8, "img/taladro.jpg", "Potente"));

        assertThat(creado.nombre()).isEqualTo("Taladro");
        assertThat(creado.precio()).isEqualTo(39990);
        assertThat(creado.stock()).isEqualTo(8);
        assertThat(creado.imagen()).isEqualTo("img/taladro.jpg");
        assertThat(creado.detalles()).isEqualTo("Potente");
    }

    @Test
    void crear_aplicaFotoYDescripcionPorDefectoSiVienenVaciasONulas() {
        ProductoResponse sinDatos = servicio.crear(new ProductoRequest("Taladro", 39990, 8, null, null));
        ProductoResponse enBlanco = servicio.crear(new ProductoRequest("Taladro", 39990, 8, "  ", ""));

        assertThat(sinDatos.imagen()).isEqualTo("img/descarga.png");
        assertThat(sinDatos.detalles()).isEqualTo("Sin descripción.");
        assertThat(enBlanco.imagen()).isEqualTo("img/descarga.png");
        assertThat(enBlanco.detalles()).isEqualTo("Sin descripción.");
    }

    @Test
    void actualizar_cambiaSoloLosCamposIndicados() {
        when(productos.findById(1L)).thenReturn(Optional.of(brocha()));

        ProductoResponse actualizado = servicio.actualizar(1L, new ProductoActualizacion(null, 1000, null, null, null));

        assertThat(actualizado.precio()).isEqualTo(1000);
        assertThat(actualizado.nombre()).isEqualTo("Brocha");
        assertThat(actualizado.stock()).isEqualTo(12);
        assertThat(actualizado.imagen()).isEqualTo("img/images.jpg");
    }

    @Test
    void actualizar_permiteDejarElStockEnCero() {
        when(productos.findById(1L)).thenReturn(Optional.of(brocha()));

        ProductoResponse actualizado = servicio.actualizar(1L, new ProductoActualizacion(null, null, 0, null, null));

        assertThat(actualizado.stock()).isZero();
    }

    @Test
    void actualizar_fallaSiElProductoNoExiste() {
        when(productos.findById(99L)).thenReturn(Optional.empty());

        ApiException error = errorDe(() ->
                servicio.actualizar(99L, new ProductoActualizacion(null, 1, null, null, null)));

        assertThat(error.getEstado()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(productos, never()).save(any(Producto.class));
    }

    @Test
    void eliminar_quitaElProducto() {
        when(productos.existsById(2L)).thenReturn(true);

        servicio.eliminar(2L);

        verify(productos).deleteById(2L);
    }

    @Test
    void eliminar_fallaSiElProductoNoExiste() {
        when(productos.existsById(99L)).thenReturn(false);

        ApiException error = errorDe(() -> servicio.eliminar(99L));

        assertThat(error.getEstado()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(productos, never()).deleteById(any(Long.class));
    }
}
