package cl.ferretech.api.error;

import org.springframework.http.HttpStatus;

/** Error de negocio con el código HTTP y el mensaje (en español) que verá el usuario. */
public class ApiException extends RuntimeException {

    private final HttpStatus estado;

    public ApiException(HttpStatus estado, String mensaje) {
        super(mensaje);
        this.estado = estado;
    }

    public HttpStatus getEstado() {
        return estado;
    }
}
