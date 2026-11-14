package cl.ferretech.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.ferretech.api.domain.Rol;
import cl.ferretech.api.domain.Usuario;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtServiceTest {

    private static final SecretKey CLAVE =
            new SecretKeySpec("una-clave-de-prueba-de-32-bytes-o-mas".getBytes(StandardCharsets.UTF_8), "HmacSHA256");

    private final JwtEncoder codificador = new NimbusJwtEncoder(new ImmutableSecret<>(CLAVE));
    private final JwtDecoder decodificador =
            NimbusJwtDecoder.withSecretKey(CLAVE).macAlgorithm(MacAlgorithm.HS256).build();

    @Test
    void elTokenLlevaElUsuarioYElRol() {
        JwtService servicio = new JwtService(codificador, Duration.ofHours(1));

        String token = servicio.generarToken(new Usuario("Admin", "hash", Rol.ADMIN));
        Jwt jwt = decodificador.decode(token);

        assertThat(jwt.getSubject()).isEqualTo("Admin");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN");
        assertThat(jwt.getExpiresAt()).isAfter(jwt.getIssuedAt());
    }
    @Test
    void unTokenExpiradoEsRechazado() {
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("ferretech")
                .subject("Mauricio")
                .issuedAt(ahora.minus(Duration.ofHours(2)))
                .expiresAt(ahora.minus(Duration.ofHours(1)))
                .claim("roles", List.of("USER"))
                .build();
        String token = codificador
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        assertThatThrownBy(() -> decodificador.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void unTokenFirmadoConOtraClaveEsRechazado() {
        SecretKey otraClave =
                new SecretKeySpec("otra-clave-distinta-de-32-bytes-o-mas!".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtService ajeno = new JwtService(new NimbusJwtEncoder(new ImmutableSecret<>(otraClave)), Duration.ofHours(1));

        String token = ajeno.generarToken(new Usuario("Admin", "hash", Rol.ADMIN));

        assertThatThrownBy(() -> decodificador.decode(token)).isInstanceOf(JwtException.class);
    }
}
