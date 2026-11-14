package cl.ferretech.api.dto;

/** Sin validaciones: un ingreso con datos vacíos o incorrectos responde siempre 401. */
public record LoginRequest(String usuario, String contrasena) {
}
