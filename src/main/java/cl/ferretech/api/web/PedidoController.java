package cl.ferretech.api.web;

import cl.ferretech.api.dto.PedidoRequest;
import cl.ferretech.api.dto.PedidoResponse;
import cl.ferretech.api.service.PedidoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService pedidos;

    public PedidoController(PedidoService pedidos) {
        this.pedidos = pedidos;
    }

    /** El usuario del pedido sale del JWT (subject), no del cuerpo de la petición. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PedidoResponse crear(Authentication autenticacion, @Valid @RequestBody PedidoRequest solicitud) {
        return pedidos.crear(autenticacion.getName(), solicitud);
    }
}
