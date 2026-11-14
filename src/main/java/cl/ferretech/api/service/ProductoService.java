package cl.ferretech.api.service;

import cl.ferretech.api.domain.Producto;
import cl.ferretech.api.dto.ProductoActualizacion;
import cl.ferretech.api.dto.ProductoRequest;
import cl.ferretech.api.dto.ProductoResponse;
import cl.ferretech.api.error.ApiException;
import cl.ferretech.api.repository.ProductoRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProductoService {

    static final String IMAGEN_POR_DEFECTO = "img/descarga.png";
    static final String DETALLES_POR_DEFECTO = "Sin descripción.";

    private final ProductoRepository productos;

    public ProductoService(ProductoRepository productos) {
        this.productos = productos;
    }

    @Transactional(readOnly = true)
    public List<ProductoResponse> listar() {
        return productos.findAllByOrderByIdAsc().stream().map(ProductoResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public ProductoResponse obtener(Long id) {
        return ProductoResponse.de(buscar(id));
    }

    public ProductoResponse crear(ProductoRequest solicitud) {
        Producto nuevo = new Producto(
                solicitud.nombre().trim(),
                solicitud.precio(),
                solicitud.stock(),
                valorOPorDefecto(solicitud.imagen(), IMAGEN_POR_DEFECTO),
                valorOPorDefecto(solicitud.detalles(), DETALLES_POR_DEFECTO));
        return ProductoResponse.de(productos.save(nuevo));
    }

    /** Solo cambia los campos informados; el resto queda igual. */
    public ProductoResponse actualizar(Long id, ProductoActualizacion cambios) {
        Producto producto = buscar(id);
        if (cambios.nombre() != null) {
            producto.setNombre(cambios.nombre().trim());
        }
        if (cambios.precio() != null) {
            producto.setPrecio(cambios.precio());
        }
        if (cambios.stock() != null) {
            producto.setStock(cambios.stock());
        }
        if (cambios.imagen() != null) {
            producto.setImagen(valorOPorDefecto(cambios.imagen(), IMAGEN_POR_DEFECTO));
        }
        if (cambios.detalles() != null) {
            producto.setDetalles(valorOPorDefecto(cambios.detalles(), DETALLES_POR_DEFECTO));
        }
        return ProductoResponse.de(productos.save(producto));
    }

    public void eliminar(Long id) {
        if (!productos.existsById(id)) {
            throw productoNoEncontrado();
        }
        productos.deleteById(id);
    }

    private Producto buscar(Long id) {
        return productos.findById(id).orElseThrow(ProductoService::productoNoEncontrado);
    }

    private static ApiException productoNoEncontrado() {
        return new ApiException(HttpStatus.NOT_FOUND, "Producto no encontrado.");
    }

    private static String valorOPorDefecto(String valor, String porDefecto) {
        return valor == null || valor.isBlank() ? porDefecto : valor.trim();
    }
}
