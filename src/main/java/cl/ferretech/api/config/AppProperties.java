package cl.ferretech.api.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Propiedades propias de la aplicación (prefijo "app" en application.properties). */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cors cors, Seed seed, Recuperacion recuperacion) {

    public record Jwt(String secret, Duration expiracion) {
    }

    public record Cors(List<String> origins) {
    }

    public record Seed(boolean habilitado, String passwordCliente, String passwordAdmin) {
    }

    public record Recuperacion(boolean mostrarCodigo) {
    }
}
