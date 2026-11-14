package cl.ferretech.api.service;

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
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Duration VIGENCIA_CODIGO = Duration.ofMinutes(10);
    private static final int MAX_INTENTOS = 5;
    private static final String USUARIO_REPETIDO = "Ese usuario ya existe, elige otro.";
    private static final String CREDENCIALES_INVALIDAS = "Usuario o contraseña incorrectos.";

    private final UsuarioRepository usuarios;
    private final CodigoRecuperacionRepository codigos;
    private final PasswordEncoder codificador;
    private final JwtService jwt;
    private final AppProperties props;
    private final SecureRandom azar = new SecureRandom();

    public AuthService(UsuarioRepository usuarios, CodigoRecuperacionRepository codigos,
                       PasswordEncoder codificador, JwtService jwt, AppProperties props) {
        this.usuarios = usuarios;
        this.codigos = codigos;
        this.codificador = codificador;
        this.jwt = jwt;
        this.props = props;
    }

    @Transactional
    public MensajeResponse registrar(RegistroRequest solicitud) {
        String usuario = solicitud.usuario().trim();
        String contrasena = solicitud.contrasena().trim();

        if (usuarios.existsByUsuario(usuario)) {
            throw new ApiException(HttpStatus.CONFLICT, USUARIO_REPETIDO);
        }
        try {
            usuarios.saveAndFlush(new Usuario(usuario, codificador.encode(contrasena), Rol.USER));
        } catch (DataIntegrityViolationException e) {
            // Dos registros simultáneos con el mismo nombre: gana el primero.
            throw new ApiException(HttpStatus.CONFLICT, USUARIO_REPETIDO);
        }
        return new MensajeResponse("Cuenta creada. Ya puedes iniciar sesión.");
    }

    /**
     * Valida las credenciales. El ingreso de clientes y el de administradores son separados:
     * una cuenta de un rol no puede entrar por el ingreso del otro.
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest solicitud, Rol rolEsperado) {
        Usuario usuario = buscarParaIngreso(solicitud);
        if (usuario == null
                || usuario.getRol() != rolEsperado
                || !codificador.matches(solicitud.contrasena().trim(), usuario.getContrasenaHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, CREDENCIALES_INVALIDAS);
        }
        return new LoginResponse(jwt.generarToken(usuario), usuario.getUsuario(), usuario.getRol().name());
    }

    private Usuario buscarParaIngreso(LoginRequest solicitud) {
        if (solicitud == null || solicitud.usuario() == null || solicitud.contrasena() == null) {
            return null;
        }
        String nombre = solicitud.usuario().trim();
        if (nombre.isEmpty() || solicitud.contrasena().trim().isEmpty()) {
            return null;
        }
        return usuarios.findByUsuario(nombre).orElse(null);
    }

    /** Genera un código de 6 dígitos. Solo las cuentas de clientes pueden recuperar su contraseña. */
    @Transactional
    public MensajeResponse solicitarCodigo(RecuperarRequest solicitud) {
        String nombre = solicitud.usuario().trim();
        Usuario usuario = usuarios.findByUsuario(nombre)
                .filter(u -> u.getRol() == Rol.USER)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ese usuario no existe."));

        String codigo = String.valueOf(100000 + azar.nextInt(900000));
        CodigoRecuperacion registro = codigos.findByUsuario(nombre)
                .orElseGet(() -> new CodigoRecuperacion(nombre));
        registro.renovar(codigo, Instant.now().plus(VIGENCIA_CODIGO));
        codigos.save(registro);

        String mensaje = "[Simulación de correo]\n\nHemos enviado un código de verificación al correo asociado a \""
                + usuario.getUsuario() + "\".";
        if (props.recuperacion().mostrarCodigo()) {
            mensaje += "\n\nTu código es: " + codigo;
        }
        return new MensajeResponse(mensaje);
    }

    /**
     * Cambia la contraseña si el código es el vigente. Se permiten {@value #MAX_INTENTOS} intentos
     * fallidos; después el código se invalida. {@code noRollbackFor} hace que el conteo de intentos
     * y la invalidación se guarden aunque la respuesta sea un error.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public MensajeResponse restablecer(RestablecerRequest solicitud) {
        String nombre = solicitud.usuario().trim();

        CodigoRecuperacion registro = codigos.findByUsuario(nombre)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                        "Primero solicita un código de verificación."));

        if (registro.getExpiraEn().isBefore(Instant.now())) {
            codigos.delete(registro);
            throw new ApiException(HttpStatus.BAD_REQUEST, "El código expiró. Solicita uno nuevo.");
        }

        if (!registro.getCodigo().equals(solicitud.codigo().trim())) {
            registro.registrarIntentoFallido();
            if (registro.getIntentos() >= MAX_INTENTOS) {
                codigos.delete(registro);
            } else {
                codigos.save(registro);
            }
            throw new ApiException(HttpStatus.BAD_REQUEST, "El código ingresado no es correcto.");
        }

        Usuario usuario = usuarios.findByUsuario(nombre)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ese usuario ya no existe."));
        usuario.setContrasenaHash(codificador.encode(solicitud.nuevaContrasena().trim()));
        usuarios.save(usuario);
        codigos.delete(registro);
        return new MensajeResponse("Contraseña actualizada. Ya puedes iniciar sesión.");
    }
}
