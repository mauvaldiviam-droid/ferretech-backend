package cl.ferretech.api.config;

import cl.ferretech.api.domain.Producto;
import cl.ferretech.api.domain.Rol;
import cl.ferretech.api.domain.Usuario;
import cl.ferretech.api.repository.ProductoRepository;
import cl.ferretech.api.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Al iniciar crea las cuentas de prueba (cliente y administrador) y los 3 productos iniciales
 * si todavía no existen. Se puede apagar con app.seed.habilitado=false.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private final UsuarioRepository usuarios;
    private final ProductoRepository productos;
    private final PasswordEncoder codificador;
    private final AppProperties props;

    public DataInitializer(UsuarioRepository usuarios, ProductoRepository productos,
                           PasswordEncoder codificador, AppProperties props) {
        this.usuarios = usuarios;
        this.productos = productos;
        this.codificador = codificador;
        this.props = props;
    }

    @Override
    public void run(String... args) {
        if (!props.seed().habilitado()) {
            return;
        }
        crearUsuarioSiNoExiste("Mauricio", props.seed().passwordCliente(), Rol.USER);
        crearUsuarioSiNoExiste("Admin", props.seed().passwordAdmin(), Rol.ADMIN);

        if (productos.count() == 0) {
            productos.save(new Producto("Brocha", 5990, 12, "img/images.jpg",
                    "Brocha de cerdas sintéticas de 2 pulgadas. Ideal para pintura al agua y esmalte, "
                            + "mango de madera ergonómico y virola resistente a la corrosión."));
            productos.save(new Producto("Tarro de pintura", 45990, 5, "img/images (1).jpg",
                    "Pintura látex interior/exterior, tarro de 1 galón. Alto poder cubritivo, "
                            + "secado al tacto en 4 horas y bajo olor."));
            productos.save(new Producto("Bolsa de clavos", 6990, 30, "img/images (2).jpg",
                    "Bolsa de 1 kg de clavos de acero galvanizado de 2 pulgadas, resistentes a la corrosión, "
                            + "ideales para estructuras de madera."));
        }
    }

    private void crearUsuarioSiNoExiste(String nombre, String contrasena, Rol rol) {
        if (!usuarios.existsByUsuario(nombre)) {
            usuarios.save(new Usuario(nombre, codificador.encode(contrasena), rol));
        }
    }
}
