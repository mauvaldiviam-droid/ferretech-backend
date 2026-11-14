package cl.ferretech.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Código de 6 dígitos para recuperar la contraseña. Hay a lo más uno vigente por usuario. */
@Entity
@Table(name = "codigos_recuperacion")
public class CodigoRecuperacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String usuario;

    @Column(nullable = false, length = 6)
    private String codigo;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Column(nullable = false)
    private int intentos;

    protected CodigoRecuperacion() {
    }

    public CodigoRecuperacion(String usuario) {
        this.usuario = usuario;
    }

    /** Reemplaza el código y reinicia el contador de intentos fallidos. */
    public void renovar(String codigo, Instant expiraEn) {
        this.codigo = codigo;
        this.expiraEn = expiraEn;
        this.intentos = 0;
    }

    public void registrarIntentoFallido() {
        this.intentos++;
    }

    public Long getId() {
        return id;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getCodigo() {
        return codigo;
    }

    public Instant getExpiraEn() {
        return expiraEn;
    }

    public int getIntentos() {
        return intentos;
    }
}
