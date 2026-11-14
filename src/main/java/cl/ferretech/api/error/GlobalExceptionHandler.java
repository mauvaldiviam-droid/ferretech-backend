package cl.ferretech.api.error;

import cl.ferretech.api.dto.MensajeResponse;
import java.util.Comparator;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Todos los errores salen con el mismo formato: {"mensaje": "..."}. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<MensajeResponse> alErrorDeNegocio(ApiException e) {
        return ResponseEntity.status(e.getEstado()).body(new MensajeResponse(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<MensajeResponse> alFallarLaValidacion(MethodArgumentNotValidException e) {
        // Se ordena por campo para que el mensaje sea siempre el mismo si hay varios errores.
        String mensaje = e.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .map(FieldError::getDefaultMessage)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("Solicitud inválida.");
        return ResponseEntity.badRequest().body(new MensajeResponse(mensaje));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<MensajeResponse> alLeerUnCuerpoInvalido(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(new MensajeResponse("Solicitud inválida."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<MensajeResponse> alRecibirUnIdentificadorInvalido(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(new MensajeResponse("Identificador inválido."));
    }
}
