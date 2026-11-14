package cl.ferretech.api.service;

import cl.ferretech.api.domain.Usuario;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/** Genera los JWT que el frontend envía en el header Authorization. */
public class JwtService {

    private final JwtEncoder codificador;
    private final Duration expiracion;

    public JwtService(JwtEncoder codificador, Duration expiracion) {
        this.codificador = codificador;
        this.expiracion = expiracion;
    }

    public String generarToken(Usuario usuario) {
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("ferretech")
                .subject(usuario.getUsuario())
                .issuedAt(ahora)
                .expiresAt(ahora.plus(expiracion))
                .claim("roles", List.of(usuario.getRol().name()))
                .build();
        JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();
        return codificador.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
    }
}
