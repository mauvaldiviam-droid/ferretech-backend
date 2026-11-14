package cl.ferretech.api.web;

import cl.ferretech.api.domain.Rol;
import cl.ferretech.api.dto.LoginRequest;
import cl.ferretech.api.dto.LoginResponse;
import cl.ferretech.api.dto.MensajeResponse;
import cl.ferretech.api.dto.RecuperarRequest;
import cl.ferretech.api.dto.RegistroRequest;
import cl.ferretech.api.dto.RestablecerRequest;
import cl.ferretech.api.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public MensajeResponse registro(@Valid @RequestBody RegistroRequest solicitud) {
        return auth.registrar(solicitud);
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest solicitud) {
        return auth.login(solicitud, Rol.USER);
    }

    @PostMapping("/admin/login")
    public LoginResponse loginAdmin(@RequestBody LoginRequest solicitud) {
        return auth.login(solicitud, Rol.ADMIN);
    }

    @PostMapping("/recuperar")
    public MensajeResponse recuperar(@Valid @RequestBody RecuperarRequest solicitud) {
        return auth.solicitarCodigo(solicitud);
    }

    @PostMapping("/restablecer")
    public MensajeResponse restablecer(@Valid @RequestBody RestablecerRequest solicitud) {
        return auth.restablecer(solicitud);
    }
}
