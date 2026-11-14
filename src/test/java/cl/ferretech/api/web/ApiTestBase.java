package cl.ferretech.api.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Base de las pruebas de integración: levanta toda la aplicación con la base H2 en memoria
 * (ver src/test/resources/application.properties) y arma un MockMvc con Spring Security activo,
 * así se prueban de verdad los roles y los JWT.
 *
 * Las pruebas NO son transaccionales a propósito: así se verifica también que la transacción
 * del pedido se deshace cuando falla. Cada prueba usa sus propios datos y los limpia.
 */
@SpringBootTest
abstract class ApiTestBase {

    @Autowired
    private WebApplicationContext contexto;

    protected MockMvc mvc;

    @BeforeEach
    void prepararMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
    }

    protected static MockHttpServletRequestBuilder enviarJson(MockHttpServletRequestBuilder peticion, String json) {
        return peticion.contentType(MediaType.APPLICATION_JSON).characterEncoding("UTF-8").content(json);
    }

    protected static String cuerpo(MvcResult resultado) throws Exception {
        return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    protected static String credenciales(String usuario, String contrasena) {
        return "{\"usuario\":\"" + usuario + "\",\"contrasena\":\"" + contrasena + "\"}";
    }

    /** Ingresa de verdad por la API y devuelve el JWT recibido. */
    protected String ingresar(String usuario, String contrasena, boolean comoAdmin) throws Exception {
        String ruta = comoAdmin ? "/api/auth/admin/login" : "/api/auth/login";
        MvcResult resultado = mvc.perform(enviarJson(post(ruta), credenciales(usuario, contrasena)))
                .andExpect(status().isOk())
                .andReturn();
        return extraer(cuerpo(resultado), "\"token\":\"([^\"]+)\"");
    }

    protected String tokenDeCliente() throws Exception {
        return ingresar("Mauricio", "1234", false);
    }

    protected String tokenDeAdmin() throws Exception {
        return ingresar("Admin", "1234", true);
    }

    /** Devuelve el primer grupo de la expresión regular o falla la prueba si no aparece. */
    protected static String extraer(String texto, String expresion) {
        Matcher coincidencia = Pattern.compile(expresion).matcher(texto);
        assertThat(coincidencia.find()).as("no se encontró /%s/ en: %s", expresion, texto).isTrue();
        return coincidencia.group(1);
    }
}
