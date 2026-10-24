package co.edu.unicolmayor.janussync.infrastructure;

import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.domain.port.*;
import co.edu.unicolmayor.janussync.domain.valueobject.UsuarioId;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import static org.assertj.core.api.Assertions.*;

/**
 * Solo se incorpora al compilar con -Pmysql-it. Requiere Docker y NO se omite
 * silenciosamente.
 */
@SpringBootTest
@Testcontainers
class PersistenciaMySqlIT {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @DynamicPropertySource
    static void baseTemporal(DynamicPropertyRegistry propiedades) {
        propiedades.add("spring.datasource.url", () -> MYSQL.getJdbcUrl()
                + (MYSQL.getJdbcUrl().contains("?") ? "&" : "?")
                + "connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
        propiedades.add("spring.datasource.username", MYSQL::getUsername);
        propiedades.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    UsuarioRepository usuarios;
    @Autowired
    RolRepository roles;
    private static final String ID = "00000000-0000-0000-0000-000000000001";
    private static final Instant FECHA = Instant.parse("2026-10-23T14:00:00Z");

    @BeforeEach
    void cargarDatosDePrueba() {
        jdbc.update("DELETE FROM usuarios_roles");
        jdbc.update("DELETE FROM usuarios");
        jdbc.update("""
                INSERT INTO usuarios(id,nombres,apellidos,correo,estado,creado_en,actualizado_en)
                VALUES(?,?,?,?,?,?,?)
                """, ID, "Ángela", "Ramírez", "angela@demo.unicolmayor.edu.co", "ACTIVO",
                Timestamp.from(FECHA), Timestamp.from(FECHA));
        jdbc.update("INSERT INTO usuarios_roles(usuario_id,rol_id) VALUES (?,?), (?,?)",
                ID, "COORDINADOR", ID, "COLABORADOR");
    }

    @Test
    void migracionesCarganCuatroRolesSinCredenciales() {
        assertThat(roles.listar()).hasSize(4);
        assertThat(roles.listar().stream().filter(r -> r.id().equals("ADMINISTRADOR")).findFirst().orElseThrow()
                .permisos())
                .hasSize(16);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1", Integer.class))
                .isEqualTo(9);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bloqueo_administracion WHERE id=1", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void filtraSinTildesYPaginaSinDuplicarUsuariosConVariosRoles() {
        var pagina = usuarios.buscar(new ConsultaUsuarios("angela ramirez", EstadoRegistro.ACTIVO, null, 1, 1));
        assertThat(pagina.total()).isEqualTo(1);
        assertThat(pagina.elementos()).hasSize(1);
        assertThat(pagina.elementos().getFirst().roles()).containsExactlyInAnyOrder("COORDINADOR", "COLABORADOR");
        assertThat(usuarios.buscar(new ConsultaUsuarios(null, null, "COORDINADOR", 2, 1)).elementos()).isEmpty();
        assertThat(usuarios.buscar(new ConsultaUsuarios(null, EstadoRegistro.INACTIVO, null, 1, 10)).total()).isZero();
    }

    @Test
    void conservaInstantesYRelacionDeRolesEnElDetalle() {
        var usuario = usuarios.obtener(UsuarioId.desde(ID)).orElseThrow();
        assertThat(usuario.creadoEn()).isEqualTo(FECHA);
        assertThat(usuario.roles()).hasSize(2);
    }

    @Test
    void trataLosFiltrosComoDatosYNoComoSqlNiComodines() {
        assertThat(usuarios.buscar(new ConsultaUsuarios("' OR 1=1 --", null, null, 1, 10)).total()).isZero();
        assertThat(usuarios.buscar(new ConsultaUsuarios("%", null, null, 1, 10)).total()).isZero();
        assertThat(usuarios.buscar(new ConsultaUsuarios("_", null, null, 1, 10)).total()).isZero();
        assertThat(usuarios.buscar(new ConsultaUsuarios("!", null, null, 1, 10)).total()).isZero();
    }

    @Test
    void baseGarantizaCorreoUnicoAnteConcurrencia() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO usuarios(id,nombres,apellidos,correo,estado) VALUES(?,?,?,?,?)
                """, "00000000-0000-0000-0000-000000000002", "Otra", "Persona", "ANGELA@demo.unicolmayor.edu.co",
                "ACTIVO"))
                .isInstanceOf(DuplicateKeyException.class);
    }
}
