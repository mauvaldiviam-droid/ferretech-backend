package cl.ferretech.api.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class AuthApiTest extends ApiTestBase {

    private static String usuarioNuevo() {
        return "cliente" + System.nanoTime();
    }

    private void registrar(String usuario, String contrasena) throws Exception {
        mvc.perform(enviarJson(post("/api/auth/registro"), credenciales(usuario, contrasena)))
                .andExpect(status().isCreated());
    }

    // ---------------------------------------------------------------- registro

    @Test
    void registro_creaLaCuentaYPermiteIngresar() throws Exception {
        String usuario = usuarioNuevo();

        MvcResult registro = mvc.perform(enviarJson(post("/api/auth/registro"), credenciales(usuario, "abc")))
                .andExpect(status().isCreated())
                .andReturn();
        MvcResult ingreso = mvc.perform(enviarJson(post("/api/auth/login"), credenciales(usuario, "abc")))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(cuerpo(registro)).contains("Cuenta creada. Ya puedes iniciar sesión.");
        assertThat(cuerpo(ingreso)).contains("\"usuario\":\"" + usuario + "\"").contains("\"rol\":\"USER\"");
        assertThat(extraer(cuerpo(ingreso), "\"token\":\"([^\"]+)\"")).isNotBlank();
    }

    @Test
    void registro_rechazaUnUsuarioQueYaExiste() throws Exception {
        MvcResult resultado = mvc.perform(enviarJson(post("/api/auth/registro"), credenciales("Mauricio", "otra")))
                .andExpect(status().isConflict())
                .andReturn();

        assertThat(cuerpo(resultado)).contains("Ese usuario ya existe, elige otro.");
    }

    @Test
    void registro_rechazaUsuarioOContrasenaVacios() throws Exception {
        MvcResult resultado = mvc.perform(enviarJson(post("/api/auth/registro"), credenciales("", "")))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(cuerpo(resultado)).contains("Completa usuario y contraseña.");
    }

    @Test
    void registro_rechazaUnCuerpoQueNoEsJson() throws Exception {
        mvc.perform(enviarJson(post("/api/auth/registro"), "esto no es json")).andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- ingreso

    @Test
    void login_conCredencialesInvalidasResponde401() throws Exception {
        MvcResult resultado = mvc.perform(enviarJson(post("/api/auth/login"), credenciales("Mauricio", "mala")))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(cuerpo(resultado)).contains("Usuario o contraseña incorrectos.");
    }

    @Test
    void login_conCamposVaciosResponde401() throws Exception {
        mvc.perform(enviarJson(post("/api/auth/login"), credenciales("", ""))).andExpect(status().isUnauthorized());
    }

    @Test
    void login_dosIngresosEntreganTokensValidosParaElMismoUsuario() throws Exception {
        assertThat(ingresar("Mauricio", "1234", false)).isNotBlank();
        assertThat(ingresar("Mauricio", "1234", false)).isNotBlank();
    }

    @Test
    void loginAdmin_soloAceptaAdministradores() throws Exception {
        MvcResult admin = mvc.perform(enviarJson(post("/api/auth/admin/login"), credenciales("Admin", "1234")))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(cuerpo(admin)).contains("\"rol\":\"ADMIN\"");
        mvc.perform(enviarJson(post("/api/auth/admin/login"), credenciales("Mauricio", "1234")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_unAdministradorNoEntraPorElIngresoDeClientes() throws Exception {
        mvc.perform(enviarJson(post("/api/auth/login"), credenciales("Admin", "1234")))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- recuperación de contraseña

    private String pedirCodigo(String usuario) throws Exception {
        MvcResult resultado = mvc.perform(enviarJson(post("/api/auth/recuperar"), "{\"usuario\":\"" + usuario + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return extraer(cuerpo(resultado), "Tu código es: (\\d{6})");
    }

    private MvcResult restablecer(String usuario, String codigo, String nueva, int estadoEsperado) throws Exception {
        String json = "{\"usuario\":\"" + usuario + "\",\"codigo\":\"" + codigo + "\",\"nuevaContrasena\":\"" + nueva + "\"}";
        return mvc.perform(enviarJson(post("/api/auth/restablecer"), json))
                .andExpect(status().is(estadoEsperado))
                .andReturn();
    }

    @Test
    void recuperacion_completaCambiaLaContrasena() throws Exception {
        String usuario = usuarioNuevo();
        registrar(usuario, "vieja");

        String codigo = pedirCodigo(usuario);
        MvcResult resultado = restablecer(usuario, codigo, "nueva", 200);

        assertThat(cuerpo(resultado)).contains("Contraseña actualizada. Ya puedes iniciar sesión.");
        mvc.perform(enviarJson(post("/api/auth/login"), credenciales(usuario, "nueva"))).andExpect(status().isOk());
        mvc.perform(enviarJson(post("/api/auth/login"), credenciales(usuario, "vieja")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void recuperacion_elCodigoSoloSirveUnaVez() throws Exception {
        String usuario = usuarioNuevo();
        registrar(usuario, "vieja");
        String codigo = pedirCodigo(usuario);
        restablecer(usuario, codigo, "nueva", 200);

        MvcResult segundoIntento = restablecer(usuario, codigo, "otra", 400);

        assertThat(cuerpo(segundoIntento)).contains("Primero solicita un código de verificación.");
    }

    @Test
    void recuperacion_unCodigoIncorrectoNoCambiaLaContrasena() throws Exception {
        String usuario = usuarioNuevo();
        registrar(usuario, "vieja");
        pedirCodigo(usuario);

        // El código real nunca empieza con 0, así que "000000" siempre es incorrecto.
        MvcResult resultado = restablecer(usuario, "000000", "nueva", 400);

        assertThat(cuerpo(resultado)).contains("El código ingresado no es correcto.");
        mvc.perform(enviarJson(post("/api/auth/login"), credenciales(usuario, "vieja"))).andExpect(status().isOk());
    }

    @Test
    void recuperacion_elCodigoSeInvalidaTrasCincoIntentosFallidos() throws Exception {
        String usuario = usuarioNuevo();
        registrar(usuario, "vieja");
        String codigoReal = pedirCodigo(usuario);

        for (int i = 0; i < 5; i++) {
            restablecer(usuario, "000000", "nueva", 400);
        }
        MvcResult conElCodigoReal = restablecer(usuario, codigoReal, "nueva", 400);

        assertThat(cuerpo(conElCodigoReal)).contains("Primero solicita un código de verificación.");
        mvc.perform(enviarJson(post("/api/auth/login"), credenciales(usuario, "vieja"))).andExpect(status().isOk());
    }

    @Test
    void recuperacion_avisaSiElUsuarioNoExiste() throws Exception {
        MvcResult resultado = mvc.perform(enviarJson(post("/api/auth/recuperar"), "{\"usuario\":\"NoExiste\"}"))
                .andExpect(status().isNotFound())
                .andReturn();

        assertThat(cuerpo(resultado)).contains("Ese usuario no existe.");
    }

    @Test
    void recuperacion_nuncaPermiteRecuperarLaCuentaDeAdministrador() throws Exception {
        mvc.perform(enviarJson(post("/api/auth/recuperar"), "{\"usuario\":\"Admin\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void recuperacion_exigeElNombreDeUsuario() throws Exception {
        MvcResult resultado = mvc.perform(enviarJson(post("/api/auth/recuperar"), "{\"usuario\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(cuerpo(resultado)).contains("Ingresa tu nombre de usuario.");
    }
}
