package co.edu.unicolmayor.janussync.infrastructure;

import co.edu.unicolmayor.janussync.application.usecase.InicializarAdministradorUseCase;
import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.domain.port.RolRepository;
import co.edu.unicolmayor.janussync.domain.valueobject.*;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles({ "local", "jwt" })
@Testcontainers
class RolesMySqlIT {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
    static final Path PRIVADA, PUBLICA;
    static {
        try {
            var generador = KeyPairGenerator.getInstance("RSA");
            generador.initialize(3072);
            var par = generador.generateKeyPair();
            PRIVADA = Files.createTempFile("janus-roles-privada-", ".pem");
            PUBLICA = Files.createTempFile("janus-roles-publica-", ".pem");
            Files.writeString(PRIVADA, pem("PRIVATE KEY", par.getPrivate().getEncoded()));
            Files.writeString(PUBLICA, pem("PUBLIC KEY", par.getPublic().getEncoded()));
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    static String pem(String tipo, byte[] bytes) {
        return "-----BEGIN " + tipo + "-----\n" + Base64.getEncoder().encodeToString(bytes) + "\n-----END " + tipo
                + "-----\n";
    }

    @AfterAll
    static void limpiarClaves() throws Exception {
        Files.deleteIfExists(PRIVADA);
        Files.deleteIfExists(PUBLICA);
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", () -> MYSQL.getJdbcUrl() + (MYSQL.getJdbcUrl().contains("?") ? "&" : "?")
                + "connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
        p.add("spring.datasource.username", MYSQL::getUsername);
        p.add("spring.datasource.password", MYSQL::getPassword);
        p.add("janussync.auth.clave-privada", () -> PRIVADA.toString());
        p.add("janussync.auth.clave-publica", () -> PUBLICA.toString());
        p.add("janussync.auth.cookie-segura", () -> false);
        p.add("janussync.auth.permitir-sin-captcha-local", () -> true);
    }

    @Autowired
    RolRepository roles;
    @Autowired
    co.edu.unicolmayor.janussync.application.usecase.GestionarRolesUseCase gestionar;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    MockMvc mvc;
    @Autowired
    InicializarAdministradorUseCase inicializar;
    static final String CORREO = "roles@pruebas.invalid";
    static final String CONTRASENA = "ContrasenaRolesSoloPrueba_2026";

    @BeforeEach
    void preparar() {
        jdbc.update("DELETE FROM envios_acceso");jdbc.update("DELETE FROM enlaces_contrasena");jdbc.update("DELETE FROM auditoria_administrativa");
        jdbc.update("DELETE FROM tokens_renovacion");
        jdbc.update("DELETE FROM sesiones_autenticacion");
        jdbc.update("DELETE FROM credenciales");
        jdbc.update("DELETE FROM usuarios_roles");
        jdbc.update("DELETE FROM usuarios");
        jdbc.update("DELETE FROM limites_autenticacion");
        jdbc.update("UPDATE inicializacion_acceso SET completada=FALSE");
        jdbc.update("DELETE FROM roles_permisos");
        jdbc.update(
                "INSERT INTO roles_permisos VALUES('COORDINADOR','PERFIL_CONSULTAR'),('COLABORADOR','PERFIL_CONSULTAR'),('OPERADOR_EV','PERFIL_CONSULTAR')");
        jdbc.update("DELETE FROM roles WHERE inicial=FALSE");
        jdbc.update("UPDATE roles SET estado='ACTIVO'");
        jdbc.update("INSERT IGNORE INTO roles_permisos SELECT 'ADMINISTRADOR',codigo FROM permisos");
        inicializar.ejecutar(new AdministradorInicial(new NombrePersona("Persona"), new NombrePersona("Prueba"),
                new CorreoElectronico(CORREO)), CONTRASENA);
        jdbc.update("INSERT INTO usuarios_roles SELECT id,'COORDINADOR' FROM usuarios");
        jdbc.update("INSERT INTO usuarios_roles SELECT id,'COLABORADOR' FROM usuarios");
        jdbc.update(
                "INSERT INTO usuarios(id,nombres,apellidos,correo,estado) VALUES('00000000-0000-0000-0000-000000000002','Persona','Inactiva','inactiva@pruebas.invalid','INACTIVO')");
        jdbc.update(
                "INSERT INTO usuarios_roles VALUES('00000000-0000-0000-0000-000000000002','ADMINISTRADOR'),('00000000-0000-0000-0000-000000000002','COORDINADOR')");
    }

    Rol rol(String id) {
        return roles.listar().stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void cuentaPersonasSinMultiplicarPorPermisosOVariosRolesYConservaFechas() {
        assertThat(roles.listar()).hasSize(4);
        assertThat(rol("ADMINISTRADOR").totalUsuarios()).isEqualTo(2);
        assertThat(rol("ADMINISTRADOR").usuariosActivos()).isEqualTo(1);
        assertThat(rol("ADMINISTRADOR").permisos()).hasSize(16);
        assertThat(rol("COORDINADOR").totalUsuarios()).isEqualTo(2);
        assertThat(rol("COLABORADOR").totalUsuarios()).isEqualTo(1);
        assertThat(rol("OPERADOR_EV").totalUsuarios()).isZero();
        assertThat(rol("OPERADOR_EV").usuariosActivos()).isZero();
        assertThat(rol("ADMINISTRADOR").creadoEn()).isNotNull();
        assertThat(rol("ADMINISTRADOR").actualizadoEn()).isNotNull();
    }

    @Test
    void conservaAsignacionesDeUnRolInactivo() {
        jdbc.update("DELETE FROM roles_permisos");
        jdbc.update(
                "INSERT INTO roles_permisos VALUES('COORDINADOR','PERFIL_CONSULTAR'),('COLABORADOR','PERFIL_CONSULTAR'),('OPERADOR_EV','PERFIL_CONSULTAR')");
        jdbc.update("DELETE FROM roles WHERE inicial=FALSE");
        jdbc.update("UPDATE roles SET estado='INACTIVO' WHERE id='COORDINADOR'");
        var rol = rol("COORDINADOR");
        assertThat(rol.estado()).isEqualTo(EstadoRegistro.INACTIVO);
        assertThat(rol.totalUsuarios()).isEqualTo(2);
        assertThat(rol.usuariosActivos()).isEqualTo(1);
    }

    @Test
    void contratoHttpRealYPermisoActualSinConfiarEnNombreAdministrador() throws Exception {
        mvc.perform(get("/api/v1/roles")).andExpect(status().isUnauthorized());
        var csrf = mvc.perform(get("/api/v1/auth/csrf")).andReturn();
        String tokenCsrf = JsonPath.read(csrf.getResponse().getContentAsString(), "$.token");
        var ingreso = mvc
                .perform(post("/api/v1/auth/login").cookie(new Cookie("JANUS-CSRF", tokenCsrf))
                        .header("X-CSRF-TOKEN", tokenCsrf)
                        .contentType("application/json")
                        .content("{\"correo\":\"" + CORREO + "\",\"contrasena\":\"" + CONTRASENA + "\"}"))
                .andExpect(status().isOk()).andReturn();
        String jwt = JsonPath.read(ingreso.getResponse().getContentAsString(), "$.accessToken");
        var listado = mvc.perform(get("/api/v1/roles").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk()).andReturn();
        List<Map<String, Object>> datos = JsonPath.read(listado.getResponse().getContentAsString(), "$");
        assertThat(datos).hasSize(4);
        var admin = datos.stream().filter(r -> r.get("id").equals("ADMINISTRADOR")).findFirst().orElseThrow();
        assertThat(admin.get("totalUsuarios")).isEqualTo(2);
        assertThat(admin.get("usuariosActivos")).isEqualTo(1);
        assertThat((List<?>) admin.get("permisos")).hasSize(16);
        assertThat(admin.get("creadoEn").toString()).endsWith("Z");
        jdbc.update("DELETE FROM roles_permisos WHERE rol_id='ADMINISTRADOR' AND permiso_codigo='ROLES_CONSULTAR'");
        mvc.perform(get("/api/v1/roles").header("Authorization", "Bearer " + jwt)).andExpect(status().isForbidden());
    }

    String actor() {
        return jdbc.queryForObject("SELECT id FROM usuarios WHERE correo=?", String.class, CORREO);
    }

    @Test
    void escriturasNormalizanNombresUnicosSegunMySqlYValidanCatalogo() {
        var nuevo = gestionar.crear(actor(), "  Educación   especial  ", " Descripción ", Set.of("PERFIL_CONSULTAR"));
        assertThat(nuevo.nombre().valor()).isEqualTo("Educación especial");
        assertThat(nuevo.id()).isNotEqualTo(nuevo.nombre().valor());
        assertThat(nuevo.inicial()).isFalse();
        assertThat(nuevo.totalUsuarios()).isZero();
        assertThatThrownBy(() -> gestionar.crear(actor(), "EDUCACION especial", "", Set.of("PERFIL_CONSULTAR")))
                .isInstanceOf(co.edu.unicolmayor.janussync.domain.exception.ReglaRolException.class)
                .hasMessageContaining("Ya existe");
        assertThatThrownBy(() -> gestionar.crear(actor(), "Otro", "", Set.of("INVENTADO")))
                .hasMessageContaining("válido");
        assertThatThrownBy(() -> gestionar.crear(actor(), "   ", "", Set.of("PERFIL_CONSULTAR")))
                .hasMessageContaining("nombre");
        var editado = gestionar.editar(actor(), nuevo.id(), "Renombrado", "Nueva descripción",
                Set.of("ROLES_CONSULTAR"));
        assertThat(editado.id()).isEqualTo(nuevo.id());
        assertThat(editado.permisos()).containsExactly("ROLES_CONSULTAR");
        gestionar.estado(actor(), nuevo.id(), EstadoRegistro.INACTIVO);
        assertThat(rol(nuevo.id()).estado()).isEqualTo(EstadoRegistro.INACTIVO);
        gestionar.estado(actor(), nuevo.id(), EstadoRegistro.ACTIVO);
        assertThat(rol(nuevo.id()).estado()).isEqualTo(EstadoRegistro.ACTIVO);
    }

    @Test
    void protegeUltimoAdministradorYRestauraTodaLaTransaccion() {
        assertThatThrownBy(() -> gestionar.estado(actor(), "ADMINISTRADOR", EstadoRegistro.INACTIVO))
                .hasMessageContaining("conservar");
        assertThat(rol("ADMINISTRADOR").estado()).isEqualTo(EstadoRegistro.ACTIVO);
        assertThatThrownBy(() -> gestionar.editar(actor(), "ADMINISTRADOR", "Cambio que debe revertirse", null,
                Set.of("PERFIL_CONSULTAR"))).hasMessageContaining("conservar");
        assertThat(rol("ADMINISTRADOR").nombre().valor()).isEqualTo("Administrador");
        assertThat(rol("ADMINISTRADOR").permisos()).hasSize(16);
    }

    @Test
    void evaluaUnionDeRolesDeUnaMismaPersonaYNoSumaPersonasNiInactivos() {
        var esenciales = co.edu.unicolmayor.janussync.application.usecase.GestionarRolesUseCase.ESENCIALES;
        var a = new HashSet<>(esenciales);
        a.remove("ROLES_ESTADO");
        var b = Set.of("ROLES_ESTADO");
        gestionar.editar(actor(), "COORDINADOR", null, null, a);
        gestionar.editar(actor(), "COLABORADOR", null, null, b);
        gestionar.estado(actor(), "ADMINISTRADOR", EstadoRegistro.INACTIVO);
        assertThat(rol("ADMINISTRADOR").totalUsuarios()).isEqualTo(2);
        assertThatThrownBy(() -> gestionar.estado(actor(), "COLABORADOR", EstadoRegistro.INACTIVO))
                .hasMessageContaining("conservar");
        gestionar.estado(actor(), "ADMINISTRADOR", EstadoRegistro.ACTIVO);
        assertThat(rol("ADMINISTRADOR").totalUsuarios()).isEqualTo(2);
    }

    @Test
    void dosInactivacionesConcurrentesNoEliminanAmbosAdministradores() throws Exception {
        var respaldo = gestionar.crear(actor(), "Respaldo", "", rol("ADMINISTRADOR").permisos());
        jdbc.update("INSERT INTO usuarios_roles VALUES(?,?)", actor(), respaldo.id());
        var inicio = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        String usuario = actor();
        try {
            var tareas = java.util.stream.Stream.of("ADMINISTRADOR", respaldo.id()).map(id -> pool.submit(() -> {
                inicio.await();
                try {
                    gestionar.estado(usuario, id, EstadoRegistro.INACTIVO);
                    return "OK";
                } catch (co.edu.unicolmayor.janussync.domain.exception.ReglaRolException e) {
                    return e.codigo();
                }
            })).toList();
            inicio.countDown();
            var resultados = new ArrayList<String>();
            for (var tarea : tareas)
                resultados.add(tarea.get(15, java.util.concurrent.TimeUnit.SECONDS));
            assertThat(resultados).containsExactlyInAnyOrder("OK", "ADMIN_REQUIRED");
            assertThat(
                    roles.listar().stream().filter(r -> r.id().equals("ADMINISTRADOR") || r.id().equals(respaldo.id()))
                            .filter(r -> r.estado() == EstadoRegistro.ACTIVO).count())
                    .isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    String ingreso() throws Exception {
        var resultado = mvc
                .perform(post("/api/v1/auth/login").cookie(new Cookie("JANUS-CSRF", "csrf-prueba"))
                        .header("X-CSRF-TOKEN", "csrf-prueba")
                        .contentType("application/json")
                        .content("{\"correo\":\"" + CORREO + "\",\"contrasena\":\"" + CONTRASENA + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return JsonPath.read(resultado.getResponse().getContentAsString(), "$.accessToken");
    }

    @Test
    void endpointsAutorizanCadaCampoCsrfYAplicanRetiradaConElMismoJwt() throws Exception {
        String jwt = ingreso();
        String datos = "{\"nombre\":\"Rol HTTP\",\"descripcion\":\"Desde API\",\"permisos\":[\"PERFIL_CONSULTAR\"]}";
        mvc.perform(post("/api/v1/roles").contentType("application/json").content(datos))
                .andExpect(status().isForbidden());
        var respuesta = mvc
                .perform(post("/api/v1/roles").header("Authorization", "Bearer " + jwt)
                        .cookie(new Cookie("JANUS-CSRF", "csrf-prueba")).header("X-CSRF-TOKEN", "csrf-prueba")
                        .contentType("application/json").content(datos))
                .andExpect(status().isCreated()).andReturn();
        String id = JsonPath.read(respuesta.getResponse().getContentAsString(), "$.id");
        for (String permiso : List.of("ROLES_CREAR", "ROLES_EDITAR", "ROLES_PERMISOS", "ROLES_ESTADO")) {
            jdbc.update("DELETE FROM roles_permisos WHERE rol_id='ADMINISTRADOR' AND permiso_codigo=?", permiso);
        }
        mvc.perform(post("/api/v1/roles").header("Authorization", "Bearer " + jwt)
                .cookie(new Cookie("JANUS-CSRF", "csrf-prueba")).header("X-CSRF-TOKEN", "csrf-prueba")
                .contentType("application/json").content(datos)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/roles/" + id).header("Authorization", "Bearer " + jwt)
                .cookie(new Cookie("JANUS-CSRF", "csrf-prueba")).header("X-CSRF-TOKEN", "csrf-prueba")
                .contentType("application/json").content("{\"nombre\":\"No permitido\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/roles/" + id + "/estado").header("Authorization", "Bearer " + jwt)
                .cookie(new Cookie("JANUS-CSRF", "csrf-prueba")).header("X-CSRF-TOKEN", "csrf-prueba")
                .contentType("application/json").content("{\"estado\":\"INACTIVO\"}"))
                .andExpect(status().isForbidden());
        jdbc.update("INSERT INTO roles_permisos VALUES('ADMINISTRADOR','ROLES_EDITAR')");
        mvc.perform(patch("/api/v1/roles/" + id).header("Authorization", "Bearer " + jwt)
                .cookie(new Cookie("JANUS-CSRF", "csrf-prueba")).header("X-CSRF-TOKEN", "csrf-prueba")
                .contentType("application/json").content("{\"permisos\":[\"ROLES_CONSULTAR\"]}"))
                .andExpect(status().isForbidden());
        jdbc.update("INSERT INTO roles_permisos VALUES('ADMINISTRADOR','ROLES_PERMISOS')");
        jdbc.update("DELETE FROM roles_permisos WHERE rol_id='ADMINISTRADOR' AND permiso_codigo='ROLES_EDITAR'");
        mvc.perform(patch("/api/v1/roles/" + id).header("Authorization", "Bearer " + jwt)
                .cookie(new Cookie("JANUS-CSRF", "csrf-prueba")).header("X-CSRF-TOKEN", "csrf-prueba")
                .contentType("application/json").content("{\"nombre\":\"No permitido\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void inactivarYReactivarAfectaAlJwtEmitidoSinEliminarAsignaciones() throws Exception {
        var adicional = gestionar.crear(actor(), "Operación temporal", "", Set.of("ROLES_CREAR"));
        String segundo = "00000000-0000-0000-0000-000000000002";
        jdbc.update("UPDATE usuarios SET estado='ACTIVO' WHERE id=?", segundo);
        jdbc.update("DELETE FROM usuarios_roles WHERE usuario_id=?", segundo);
        jdbc.update("INSERT INTO usuarios_roles VALUES(?,?)", segundo, adicional.id());
        jdbc.update("INSERT INTO credenciales SELECT ?,hash_contrasena,actualizado_en FROM credenciales LIMIT 1",
                segundo);
        var ingreso = mvc
                .perform(post("/api/v1/auth/login").cookie(new Cookie("JANUS-CSRF", "csrf-prueba"))
                        .header("X-CSRF-TOKEN", "csrf-prueba")
                        .contentType("application/json")
                        .content("{\"correo\":\"inactiva@pruebas.invalid\",\"contrasena\":\"" + CONTRASENA + "\"}"))
                .andExpect(status().isOk()).andReturn();
        String jwt = JsonPath.read(ingreso.getResponse().getContentAsString(), "$.accessToken");
        for (int paso = 0; paso < 3; paso++) {
            if (paso == 1)
                gestionar.estado(actor(), adicional.id(), EstadoRegistro.INACTIVO);
            if (paso == 2)
                gestionar.estado(actor(), adicional.id(), EstadoRegistro.ACTIVO);
            mvc.perform(post("/api/v1/roles").header("Authorization", "Bearer " + jwt)
                    .cookie(new Cookie("JANUS-CSRF", "csrf-prueba")).header("X-CSRF-TOKEN", "csrf-prueba")
                    .contentType("application/json")
                    .content("{\"nombre\":\"Prueba JWT " + paso + "\",\"permisos\":[\"PERFIL_CONSULTAR\"]}"))
                    .andExpect(status().is(paso == 1 ? 403 : 201));
            assertThat(rol(adicional.id()).totalUsuarios()).isEqualTo(1);
        }
    }

    @Test
    void nombresDuplicadosConcurrentesSoloCreanUnRol() throws Exception {
        String usuario = actor();
        var inicio = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var tareas = java.util.stream.Stream.of("Educación concurrente", "EDUCACION concurrente")
                    .map(nombre -> pool.submit(() -> {
                        inicio.await();
                        try {
                            gestionar.crear(usuario, nombre, "", Set.of("PERFIL_CONSULTAR"));
                            return "OK";
                        } catch (co.edu.unicolmayor.janussync.domain.exception.ReglaRolException e) {
                            return e.codigo();
                        }
                    })).toList();
            inicio.countDown();
            var resultados = new ArrayList<String>();
            for (var tarea : tareas)
                resultados.add(tarea.get(15, java.util.concurrent.TimeUnit.SECONDS));
            assertThat(resultados).containsExactlyInAnyOrder("OK", "DUPLICATE_NAME");
        } finally {
            pool.shutdownNow();
        }
    }

}
