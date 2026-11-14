package cl.ferretech.api.dto;

import cl.ferretech.api.domain.Producto;

public record ProductoResponse(Long id, String nombre, int precio, int stock, String imagen, String detalles) {

    public static ProductoResponse de(Producto p) {
        return new ProductoResponse(p.getId(), p.getNombre(), p.getPrecio(), p.getStock(), p.getImagen(), p.getDetalles());
    }
}
