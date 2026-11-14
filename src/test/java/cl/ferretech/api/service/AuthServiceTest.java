package cl.ferretech.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cl.ferretech.api.config.AppProperties;
import cl.ferretech.api.domain.CodigoRecuperacion;
import cl.ferretech.api.domain.Rol;
import cl.ferretech.api.domain.Usuario;
import cl.ferretech.api.dto.LoginRequest;
import cl.ferretech.api.dto.LoginResponse;
import cl.ferretech.api.dto.MensajeResponse;
import cl.ferretech.api.dto.RecuperarRequest;
import cl.ferretech.api.dto.RegistroRequest;
import cl.ferretech.api.dto.RestablecerRequest;
import cl.ferretech.api.error.ApiException;
import cl.ferretech.api.repository.CodigoRecuperacionRepository;
import cl.ferretech.api.repository.UsuarioRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock
    private UsuarioRepository usuarios;

    @Mock
    private CodigoRecuperacionRepository codigos;

    @Mock
    private JwtService jwt;

    // Costo 4 = el mínimo de BCrypt: hace las pruebas rápidas sin cambiar el comportamiento.
    private final PasswordEncoder codificador = new BCryptPasswordEncoder(4);

    private AuthService servicio;

    @BeforeEach
    void preparar() {
        servicio = crearServicio(true);
        when(jwt.generarToken(any(Usuario.class))).thenReturn("token-falso");
    }

    private AuthService crearServicio(boolean mostrarCodigo) {
        AppProperties props = new AppProperties(
                new AppProperties.Jwt("x".repeat(40), Duration.ofHours(1)),
                new AppProperties.Cors(List.of("*")),
                new AppProperties.Seed(false, "1234", "1234"),
                new AppProperties.Recuperacion(mostrarCodigo));
        return new AuthService(usuarios, codigos, codificador, jwt, props);
    }

    private Usuario cuenta(String nombre, String contrasena, Rol rol) {
        return new Usuario(nombre, codificador.encode(contrasena), rol);
    }

    private ApiException errorDe(Runnable accion) {
        try {
            accion.run();
        } catch (ApiException e) {
            return e;
        }
        throw new AssertionError("Se esperaba una ApiException");
    }

    // ---------------------------------------------------------------- registro

    @Test
    void registrar_creaUnaCuentaDeClienteConLaContrasenaCifrada() {
        when(usuarios.existsByUsuario("Ana")).thenReturn(false);

        MensajeResponse respuesta = servicio.registrar(new RegistroRequest("Ana", "abc"));

        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).saveAndFlush(guardado.capture());
        assertThat(respuesta.mensaje()).isEqualTo("Cuenta creada. Ya puedes iniciar sesión.");
        assertThat(guardado.getValue().getUsuario()).isEqualTo("Ana");
        assertThat(guardado.getValue().getRol()).isEqualTo(Rol.USER);
        assertThat(guardado.getValue().getContrasenaHash()).isNotEqualTo("abc");
        assertThat(codificador.matches("abc", guardado.getValue().getContrasenaHash())).isTrue();
    }

    @Test
    void registrar_ignoraLosEspaciosAlInicioYAlFinal() {
        servicio.registrar(new RegistroRequest("  Ana  ", "  abc  "));

        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).saveAndFlush(guardado.capture());
        assertThat(guardado.getValue().getUsuario()).isEqualTo("Ana");
        assertThat(codificador.matches("abc", guardado.getValue().getContrasenaHash())).isTrue();
    }

    @Test
    void registrar_rechazaUnUsuarioRepetido() {
        when(usuarios.existsByUsuario("Mauricio")).thenReturn(true);

        ApiException error = errorDe(() -> servicio.registrar(new RegistroRequest("Mauricio", "otra")));

        assertThat(error.getEstado()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(error.getMessage()).isEqualTo("Ese usuario ya existe, elige otro.");
        verify(usuarios, never()).saveAndFlush(any(Usuario.class));
    }

    // ---------------------------------------------------------------- ingreso

    @Test
    void login_devuelveElTokenYElRolConCredencialesCorrectas() {
        when(usuarios.findByUsuario("Mauricio")).thenReturn(Optional.of(cuenta("Mauricio", "1234", Rol.USER)));

        LoginResponse respuesta = servicio.login(new LoginRequest("Mauricio", "1234"), Rol.USER);

        assertThat(respuesta.token()).isEqualTo("token-falso");
        assertThat(respuesta.usuario()).isEqualTo("Mauricio");
        assertThat(respuesta.rol()).isEqualTo("USER");
    }

    @Test
    void login_rechazaUnaContrasenaIncorrecta() {
        when(usuarios.findByUsuario("Mauricio")).thenReturn(Optional.of(cuenta("Mauricio", "1234", Rol.USER)));

        ApiException error = errorDe(() -> servicio.login(new LoginRequest("Mauricio", "mala"), Rol.USER));

        assertThat(error.getEstado()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(error.getMessage()).isEqualTo("Usuario o contraseña incorrectos.");
    }

    @Test
    void login_rechazaUnUsuarioInexistente() {
        when(usuarios.findByUsuario("Nadie")).thenReturn(Optional.empty());

        ApiException error = errorDe(() -> servicio.login(new LoginRequest("Nadie", "1234"), Rol.USER));

        assertThat(error.getEstado()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void login_rechazaLosCamposVaciosONulos() {
        assertThat(errorDe(() -> servicio.login(new LoginRequest("", ""), Rol.USER)).getEstado())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(errorDe(() -> servicio.login(new LoginRequest(null, null), Rol.USER)).getEstado())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(errorDe(() -> servicio.login(null, Rol.USER)).getEstado())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void login_unClienteNoPuedeEntrarPorElIngresoDeAdministrador() {
        when(usuarios.findByUsuario("Mauricio")).thenReturn(Optional.of(cuenta("Mauricio", "1234", Rol.USER)));

        ApiException error = errorDe(() -> servicio.login(new LoginRequest("Mauricio", "1234"), Rol.ADMIN));

        assertThat(error.getEstado()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void login_unAdministradorNoPuedeEntrarPorElIngresoDeClientes() {
        when(usuarios.findByUsuario("Admin")).thenReturn(Optional.of(cuenta("Admin", "1234", Rol.ADMIN)));

        ApiException error = errorDe(() -> servicio.login(new LoginRequest("Admin", "1234"), Rol.USER));

        assertThat(error.getEstado()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---------------------------------------------------------------- recuperar contraseña

    @Test
    void solicitarCodigo_exigeUnUsuarioExistente() {
        when(usuarios.findByUsuario("Nadie")).thenReturn(Optional.empty());

        ApiException error = errorDe(() -> servicio.solicitarCodigo(new RecuperarRequest("Nadie")));

        assertThat(error.getEstado()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(error.getMessage()).isEqualTo("Ese usuario no existe.");
    }

    @Test
    void solicitarCodigo_noPermiteRecuperarCuentasDeAdministrador() {
        when(usuarios.findByUsuario("Admin")).thenReturn(Optional.of(cuenta("Admin", "1234", Rol.ADMIN)));

        ApiException error = errorDe(() -> servicio.solicitarCodigo(new RecuperarRequest("Admin")));

        assertThat(error.getEstado()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(codigos, never()).save(any(CodigoRecuperacion.class));
    }

    @Test
    void solicitarCodigo_guardaUnCodigoDeSeisDigitosYLoMuestra() {
        when(usuarios.findByUsuario("Mauricio")).thenReturn(Optional.of(cuenta("Mauricio", "1234", Rol.USER)));
        when(codigos.findByUsuario("Mauricio")).thenReturn(Optional.empty());

        MensajeResponse respuesta = servicio.solicitarCodigo(new RecuperarRequest("Mauricio"));

        ArgumentCaptor<CodigoRecuperacion> guardado = ArgumentCaptor.forClass(CodigoRecuperacion.class);
        verify(codigos).save(guardado.capture());
        String codigo = guardado.getValue().getCodigo();
        assertThat(codigo).matches("[1-9][0-9]{5}");
        assertThat(guardado.getValue().getExpiraEn()).isAfter(Instant.now());
        assertThat(guardado.getValue().getIntentos()).isZero();
        assertThat(respuesta.mensaje()).contains("[Simulación de correo]").contains("Tu código es: " + codigo);
    }

    @Test
    void solicitarCodigo_noMuestraElCodigoSiLaOpcionEstaApagada() {
        AuthService sinCodigoVisible = crearServicio(false);
        when(usuarios.findByUsuario("Mauricio")).thenReturn(Optional.of(cuenta("Mauricio", "1234", Rol.USER)));
        when(codigos.findByUsuario("Mauricio")).thenReturn(Optional.empty());

        MensajeResponse respuesta = sinCodigoVisible.solicitarCodigo(new RecuperarRequest("Mauricio"));

        assertThat(respuesta.mensaje()).doesNotContain("Tu código es");
    }

    @Test
    void solicitarCodigo_reemplazaElCodigoAnteriorYReiniciaLosIntentos() {
        CodigoRecuperacion anterior = new CodigoRecuperacion("Mauricio");
        anterior.renovar("111111", Instant.now().plusSeconds(60));
        anterior.registrarIntentoFallido();
        when(usuarios.findByUsuario("Mauricio")).thenReturn(Optional.of(cuenta("Mauricio", "1234", Rol.USER)));
        when(codigos.findByUsuario("Mauricio")).thenReturn(Optional.of(anterior));

        servicio.solicitarCodigo(new RecuperarRequest("Mauricio"));

        assertThat(anterior.getIntentos()).isZero();
        verify(codigos).save(anterior);
    }

    // ---------------------------------------------------------------- restablecer

    private CodigoRecuperacion codigoVigente(String codigo) {
        CodigoRecuperacion registro = new CodigoRecuperacion("Mauricio");
        registro.renovar(codigo, Instant.now().plusSeconds(300));
        return registro;
    }

    @Test
    void restablecer_exigeHaberPedidoUnCodigo() {
        when(codigos.findByUsuario("Mauricio")).thenReturn(Optional.empty());

        ApiException error = errorDe(() ->
                servicio.restablecer(new RestablecerRequest("Mauricio", "123456", "nueva")));

        assertThat(error.getEstado()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(error.getMessage()).isEqualTo("Primero solicita un código de verificación.");
    }

    @Test
    void restablecer_cambiaLaContrasenaConElCodigoCorrecto() {
        CodigoRecuperacion registro = codigoVigente("123456");
        Usuario mauricio = cuenta("Mauricio", "1234", Rol.USER);
        when(codigos.findByUsuario("Mauricio")).thenReturn(Optional.of(registro));
        when(usuarios.findByUsuario("Mauricio")).thenReturn(Optional.of(mauricio));

        MensajeResponse respuesta = servicio.restablecer(new RestablecerRequest("Mauricio", "123456", "nueva"));

        assertThat(respuesta.mensaje()).isEqualTo("Contraseña actualizada. Ya puedes iniciar sesión.");
        assertThat(codificador.matches("nueva", mauricio.getContrasenaHash())).isTrue();
        assertThat(codificador.matches("1234", mauricio.getContrasenaHash())).isFalse();
        verify(codigos).delete(registro);
    }

    @Test
    void restablecer_rechazaUnCodigoIncorrectoYCuentaElIntento() {
        CodigoRecuperacion registro = codigoVigente("123456");
        Usuario mauricio = cuenta("Mauricio", "1234", Rol.USER);
        when(codigos.findByUsuario("Mauricio")).thenReturn(Optional.of(registro));
        when(usuarios.findByUsuario("Mauricio")).thenReturn(Optional.of(mauricio));

        ApiException error = errorDe(() ->
                servicio.restablecer(new RestablecerRequest("Mauricio", "999999", "nueva")));

        assertThat(error.getEstado()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(error.getMessage()).isEqualTo("El código ingresado no es correcto.");
        assertThat(registro.getIntentos()).isEqualTo(1);
        assertThat(codificador.matches("1234", mauricio.getContrasenaHash())).isTrue();
        verify(codigos).save(registro);
    }

    @Test
    void restablecer_invalidaElCodigoTrasCincoIntentosFallidos() {
        CodigoRecuperacion registro = codigoVigente("123456");
        for (int i = 0; i < 4; i++) {
            registro.registrarIntentoFallido();
        }
        when(codigos.findByUsuario("Mauricio")).thenReturn(Optional.of(registro));

        errorDe(() -> servicio.restablecer(new RestablecerRequest("Mauricio", "999999", "nueva")));

        verify(codigos).delete(registro);
    }

    @Test
    void restablecer_rechazaUnCodigoExpirado() {
        CodigoRecuperacion registro = new CodigoRecuperacion("Mauricio");
        registro.renovar("123456", Instant.now().minusSeconds(1));
        when(codigos.findByUsuario("Mauricio")).thenReturn(Optional.of(registro));

        ApiException error = errorDe(() ->
                servicio.restablecer(new RestablecerRequest("Mauricio", "123456", "nueva")));

        assertThat(error.getEstado()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(error.getMessage()).isEqualTo("El código expiró. Solicita uno nuevo.");
        verify(codigos).delete(registro);
    }

    @Test
    void restablecer_avisaSiElUsuarioYaNoExiste() {
        when(codigos.findByUsuario("Mauricio")).thenReturn(Optional.of(codigoVigente("123456")));
        when(usuarios.findByUsuario("Mauricio")).thenReturn(Optional.empty());

        ApiException error = errorDe(() ->
                servicio.restablecer(new RestablecerRequest("Mauricio", "123456", "nueva")));

        assertThat(error.getEstado()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(error.getMessage()).isEqualTo("Ese usuario ya no existe.");
    }
}
