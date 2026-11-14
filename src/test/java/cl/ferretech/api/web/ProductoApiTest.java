package cl.ferretech.api.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

class ProductoApiTest extends ApiTestBase {

    private static final String NUEVO_PRODUCTO =
            "{\"nombre\":\"Taladro\",\"precio\":39990,\"stock\":8}";

    // ---------------------------------------------------------------- lectura pública

    @Test
    void listar_esPublicoYTraeElCatalogoInicial() throws Exception {
        MvcResult resultado = mvc.perform(get("/api/productos")).andExpect(status().isOk()).andReturn();

        assertThat(cuerpo(resultado))
                .contains("Brocha")
                .contains("Tarro de pintura")
                .contains("Bolsa de clavos");
    }

    @Test
    void obtener_unProductoInexistenteResponde404() throws Exception {
        MvcResult resultado = mvc.perform(get("/api/productos/999999")).andExpect(status().isNotFound()).andReturn();

        assertThat(cuerpo(resultado)).contains("Producto no encontrado.");
    }

    @Test
    void obtener_unIdQueNoEsNumeroResponde400() throws Exception {
        mvc.perform(get("/api/productos/abc")).andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- permisos

    @Test
    void crear_exigeSesion() throws Exception {
        MvcResult resultado = mvc.perform(enviarJson(post("/api/productos"), NUEVO_PRODUCTO))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(cuerpo(resultado)).contains("Debes iniciar sesión.");
    }

    @Test
    void crear_unClienteNoTienePermiso() throws Exception {
        MvcResult resultado = mvc.perform(enviarJson(post("/api/productos"), NUEVO_PRODUCTO)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDeCliente())))
                .andExpect(status().isForbidden())
                .andReturn();

        assertThat(cuerpo(resultado)).contains("No tienes permisos para esta acción.");
    }

    @Test
    void editarYEliminar_unClienteNoTienePermiso() throws Exception {
        String token = bearer(tokenDeCliente());

        mvc.perform(enviarJson(put("/api/productos/1"), "{\"precio\":1}").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/productos/1").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
    }

    @Test
    void crear_unTokenFalsoEsRechazado() throws Exception {
        mvc.perform(enviarJson(post("/api/productos"), NUEVO_PRODUCTO)
                        .header(HttpHeaders.AUTHORIZATION, bearer("esto.no.es-un-jwt")))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- administrador

    @Test
    void administrador_creaActualizaYEliminaUnProducto() throws Exception {
        String token = bearer(tokenDeAdmin());

        // crear: aplica los valores por defecto de imagen y descripción
        MvcResult creado = mvc.perform(enviarJson(post("/api/productos"), NUEVO_PRODUCTO)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isCreated())
                .andReturn();
        String id = extraer(cuerpo(creado), "\"id\":(\\d+)");
        assertThat(cuerpo(creado)).contains("\"nombre\":\"Taladro\"")
                .contains("\"imagen\":\"img/descarga.png\"")
                .contains("\"detalles\":\"Sin descripción.\"");

        // actualizar solo precio y stock
        MvcResult actualizado = mvc.perform(enviarJson(put("/api/productos/" + id), "{\"precio\":1000,\"stock\":50}")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(cuerpo(actualizado)).contains("\"precio\":1000").contains("\"stock\":50")
                .contains("\"nombre\":\"Taladro\"");

        // el cambio es visible para cualquiera
        MvcResult leido = mvc.perform(get("/api/productos/" + id)).andExpect(status().isOk()).andReturn();
        assertThat(cuerpo(leido)).contains("\"stock\":50");

        // eliminar
        mvc.perform(delete("/api/productos/" + id).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/productos/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void administrador_noPuedeEditarNiEliminarUnProductoInexistente() throws Exception {
        String token = bearer(tokenDeAdmin());

        mvc.perform(enviarJson(put("/api/productos/999999"), "{\"precio\":1}").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/productos/999999").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- validaciones

    @Test
    void crear_rechazaUnPrecioNegativo() throws Exception {
        MvcResult resultado = mvc.perform(enviarJson(post("/api/productos"),
                                "{\"nombre\":\"Taladro\",\"precio\":-5,\"stock\":8}")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDeAdmin())))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(cuerpo(resultado)).contains("El precio no puede ser negativo.");
    }

    @Test
    void crear_exigeElStock() throws Exception {
        MvcResult resultado = mvc.perform(enviarJson(post("/api/productos"),
                                "{\"nombre\":\"Taladro\",\"precio\":100}")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDeAdmin())))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(cuerpo(resultado)).contains("El stock es obligatorio.");
    }

    @Test
    void crear_rechazaUnNombreEnBlanco() throws Exception {
        MvcResult resultado = mvc.perform(enviarJson(post("/api/productos"),
                                "{\"nombre\":\"   \",\"precio\":100,\"stock\":1}")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDeAdmin())))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(cuerpo(resultado)).contains("El nombre es obligatorio.");
    }

    @Test
    void actualizar_rechazaUnStockNegativo() throws Exception {
        MvcResult resultado = mvc.perform(enviarJson(put("/api/productos/1"), "{\"stock\":-1}")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDeAdmin())))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(cuerpo(resultado)).contains("El stock no puede ser negativo.");
    }
}
