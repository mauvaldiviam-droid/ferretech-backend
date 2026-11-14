package cl.ferretech.api.web;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Para comprobar que la API está viva (curl http://localhost:8080/api/estado). */
@RestController
@RequestMapping("/api/estado")
public class EstadoController {

    @GetMapping
    public Map<String, String> estado() {
        return Map.of(
                "estado", "ok",
                "servicio", "Ferretech API",
                "horaServidor", Instant.now().toString());
    }
}
