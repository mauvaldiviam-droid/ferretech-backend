package cl.ferretech.api.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class EstadoApiTest extends ApiTestBase {

    @Test
    void estado_esPublicoYResponde200() throws Exception {
        MvcResult resultado = mvc.perform(get("/api/estado")).andExpect(status().isOk()).andReturn();

        assertThat(cuerpo(resultado)).contains("\"estado\":\"ok\"").contains("Ferretech API");
    }

    @Test
    void unaRutaQueNoExisteNoEsAccesibleSinSesion() throws Exception {
        mvc.perform(get("/api/ruta-inexistente")).andExpect(status().isUnauthorized());
    }

    @Test
    void cors_permiteLaPeticionPreviaDelFrontendEnDesarrollo() throws Exception {
        mvc.perform(options("/api/productos")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "DELETE")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Access-Control-Allow-Origin"));
    }
}
