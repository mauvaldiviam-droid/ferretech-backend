package cl.ferretech.api.repository;

import cl.ferretech.api.domain.CodigoRecuperacion;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CodigoRecuperacionRepository extends JpaRepository<CodigoRecuperacion, Long> {

    Optional<CodigoRecuperacion> findByUsuario(String usuario);
}
