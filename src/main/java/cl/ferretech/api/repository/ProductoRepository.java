package cl.ferretech.api.repository;

import cl.ferretech.api.domain.Producto;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    List<Producto> findAllByOrderByIdAsc();

    /**
     * Descuenta stock de forma atómica en la base de datos: la condición {@code stock >= cantidad}
     * evita que dos compras simultáneas dejen el stock negativo.
     *
     * @return 1 si se descontó; 0 si no había stock suficiente (o el producto no existe)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Producto p set p.stock = p.stock - :cantidad where p.id = :id and p.stock >= :cantidad")
    int descontarStock(@Param("id") Long id, @Param("cantidad") int cantidad);
}
