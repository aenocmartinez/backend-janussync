package co.edu.unicolmayor.janussync.infrastructure;

import co.edu.unicolmayor.janussync.application.usecase.*;
import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.domain.port.*;
import co.edu.unicolmayor.janussync.domain.valueobject.*;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@ActiveProfiles({ "jwt", "local" })
@Testcontainers
class AutenticacionJwtIT {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
    static final Path PRIVADA, PUBLICA;
    static {
        try {
            var generador = KeyPairGenerator.getInstance("RSA");
            generador.initialize(3072);
            var par = generador.generateKeyPair();
            PRIVADA = Files.createTempFile("janus-test-privada-", ".pem");
            PUBLICA = Files.createTempFile("janus-test-publica-", ".pem");
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
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    InicializarAdministradorUseCase inicializar;
    @Autowired
    ProtectorCredenciales protector;
    @Autowired
    AccesoRepository accesos;
    @Autowired
    JwtEncoder encoder;
    @Autowired
    JwtDecoder decoder;
    static final String CORREO = "admin@pruebas.invalid";
    static final String CONTRASENA = "ContrasenaSoloPruebas_2026!";

    record Tokens(String jwt, String refresh) {
        @Override
        public String toString() {
            return "Tokens[protegidos]";
        }
    }

    @BeforeEach
    void preparar() {
        jdbc.update("DELETE FROM tokens_renovacion");
        jdbc.update("DELETE FROM sesiones_autenticacion");
        jdbc.update("DELETE FROM credenciales");
        jdbc.update("DELETE FROM usuarios_roles");
        jdbc.update("DELETE FROM usuarios");
        jdbc.update("DELETE FROM limites_autenticacion");
        jdbc.update("UPDATE inicializacion_acceso SET completada=FALSE");
        jdbc.update("UPDATE roles SET estado='ACTIVO'");
        jdbc.update("INSERT IGNORE INTO roles_permisos SELECT 'ADMINISTRADOR',codigo FROM permisos");
        inicializar.ejecutar(new AdministradorInicial(new NombrePersona("Persona"), new NombrePersona("Inicial"),
                new CorreoElectronico(CORREO)), CONTRASENA);
    }

    MockHttpServletRequestBuilder protegido(MockHttpServletRequestBuilder request) throws Exception {
        var csrf = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        String token = JsonPath.read(csrf.getResponse().getContentAsString(), "$.token");
        return request.cookie(new Cookie("JANUS-CSRF", token)).header("X-CSRF-TOKEN", token);
    }

    Tokens login() throws Exception {
        var result = mvc.perform(protegido(post("/api/v1/auth/login")).contentType("application/json")
                .content("{\"correo\":\"" + CORREO + "\",\"contrasena\":\"" + CONTRASENA + "\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andReturn();
        assertThat(result.getResponse().getCookie("JANUS-REFRESH").isHttpOnly()).isTrue();
        assertThat(result.getResponse().getCookie("JANUS-REFRESH").getPath()).isEqualTo("/api/v1/auth");
        assertThat(result.getRequest().getSession(false)).isNull();
        return tokens(result);
    }

    Tokens tokens(MvcResult result) throws Exception {
        return new Tokens(JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken"),
                result.getResponse().getCookie("JANUS-REFRESH").getValue());
    }

    MvcResult refresh(String token, int estado) throws Exception {
        return mvc.perform(protegido(post("/api/v1/auth/refresh")).cookie(new Cookie("JANUS-REFRESH", token)))
                .andExpect(status().is(estado)).andReturn();
    }

    void consultar(String token, int estado) throws Exception {
        mvc.perform(get("/api/v1/usuarios").header("Authorization", "Bearer " + token)).andExpect(status().is(estado));
    }

    @Test
    void ingresoRealArgon2ClaimsMinimosYCookies() throws Exception {
        var tokens = login();
        consultar(tokens.jwt(), 200);
        String hash = jdbc.queryForObject("SELECT hash_contrasena FROM credenciales", String.class);
        assertThat(hash).startsWith("$argon2id$");
        assertThat(protector.coincide(CONTRASENA, hash)).isTrue();
        assertThat(jdbc.queryForObject("SELECT hash_token FROM tokens_renovacion", String.class))
                .isEqualTo(protector.hash(tokens.refresh()));
        var claims = decoder.decode(tokens.jwt());
        assertThat(claims.getClaims().keySet()).containsExactlyInAnyOrder("iss", "sub", "aud", "iat", "nbf", "exp",
                "sid");
        assertThat(Duration.between(claims.getIssuedAt(), claims.getExpiresAt())).isEqualTo(Duration.ofMinutes(5));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + tokens.jwt())).andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.correo").value(CORREO));
    }

    @Test
    void rotaDetectaReusoYRevocaTambienJwtAnteriorYNuevo() throws Exception {
        var anterior = login();
        var nuevo = tokens(refresh(anterior.refresh(), 200));
        assertThat(nuevo.refresh()).isNotEqualTo(anterior.refresh());
        consultar(nuevo.jwt(), 200);
        refresh(anterior.refresh(), 401);
        consultar(anterior.jwt(), 401);
        consultar(nuevo.jwt(), 401);
        refresh(nuevo.refresh(), 401);
    }

    @Test
    void logoutRevocaTodosLosTokensAunqueElJwtNoHayaVencido() throws Exception {
        var anterior = login();
        var nuevo = tokens(refresh(anterior.refresh(), 200));
        var result = mvc
                .perform(protegido(post("/api/v1/auth/logout")).cookie(new Cookie("JANUS-REFRESH", nuevo.refresh())))
                .andExpect(status().isNoContent()).andReturn();
        assertThat(result.getResponse().getCookie("JANUS-REFRESH").getMaxAge()).isZero();
        consultar(anterior.jwt(), 401);
        consultar(nuevo.jwt(), 401);
        refresh(nuevo.refresh(), 401);
    }

    @Test
    void cambiosDePermisoYEstadoAfectanElSiguienteRequest() throws Exception {
        var tokens = login();
        jdbc.update("DELETE FROM roles_permisos WHERE rol_id='ADMINISTRADOR' AND permiso_codigo='USUARIOS_CONSULTAR'");
        consultar(tokens.jwt(), 403);
        jdbc.update("INSERT INTO roles_permisos VALUES('ADMINISTRADOR','USUARIOS_CONSULTAR')");
        consultar(tokens.jwt(), 200);
        jdbc.update("UPDATE roles SET estado='INACTIVO' WHERE id='ADMINISTRADOR'");
        consultar(tokens.jwt(), 403);
        jdbc.update("UPDATE roles SET estado='ACTIVO' WHERE id='ADMINISTRADOR'");
        jdbc.update("UPDATE usuarios SET estado='INACTIVO'");
        consultar(tokens.jwt(), 401);
        refresh(tokens.refresh(), 401);
    }

    String firmado(Tokens tokens, String emisor, String audiencia, Instant inicio, Instant fin) {
        var original = decoder.decode(tokens.jwt());
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(),
                JwtClaimsSet.builder().subject(original.getSubject()).claim("sid", original.getClaimAsString("sid"))
                        .issuer(emisor).audience(List.of(audiencia)).issuedAt(inicio).notBefore(inicio).expiresAt(fin)
                        .build()))
                .getTokenValue();
    }

    @Test
    void rechazaExpiracionFirmaAlteradaEmisorAudienciaYAlgoritmo() throws Exception {
        var tokens = login();
        Instant ahora = Instant.now();
        consultar(firmado(tokens, "janussync", "janussync-api", ahora.minusSeconds(120), ahora.minusSeconds(1)), 401);
        consultar(firmado(tokens, "otro", "janussync-api", ahora.minusSeconds(1), ahora.plusSeconds(60)), 401);
        consultar(firmado(tokens, "janussync", "otra", ahora.minusSeconds(1), ahora.plusSeconds(60)), 401);
        consultar(firmado(tokens, "janussync", "janussync-api", ahora.plusSeconds(60), ahora.plusSeconds(120)), 401);
        var partes = tokens.jwt().split("\\.");
        consultar(partes[0] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"alterado\"}".getBytes()) + "."
                + partes[2], 401);
        String cabecera = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes());
        consultar(cabecera + "." + partes[1] + ".", 401);
    }

    @Test
    void csrfExigeTokenEnLoginRefreshYLogoutSinCrearHttpSession() throws Exception {
        var tokens = login();
        for (String ruta : List.of("login", "refresh", "logout")) {
            mvc.perform(post("/api/v1/auth/" + ruta).cookie(new Cookie("JANUS-REFRESH", tokens.refresh()))
                    .contentType("application/json").content("{}")).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/auth/" + ruta).cookie(new Cookie("JANUS-CSRF", "correcto"))
                    .header("X-CSRF-TOKEN", "incorrecto").contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
        }
        consultar(tokens.jwt(), 200);
    }

    @Test
    void credencialesInvalidasEInactivoDanElMismoErrorYLimitaIntentos() throws Exception {
        for (int i = 0; i < 10; i++)
            mvc.perform(protegido(post("/api/v1/auth/login")).contentType("application/json")
                    .content("{\"correo\":\"" + CORREO + "\",\"contrasena\":\"incorrecta\"}"))
                    .andExpect(status().isUnauthorized());
        mvc.perform(protegido(post("/api/v1/auth/login")).contentType("application/json")
                .content("{\"correo\":\"" + CORREO + "\",\"contrasena\":\"" + CONTRASENA + "\"}"))
                .andExpect(status().isTooManyRequests());
        jdbc.update("DELETE FROM limites_autenticacion");
        jdbc.update("UPDATE usuarios SET estado='INACTIVO'");
        mvc.perform(protegido(post("/api/v1/auth/login")).contentType("application/json")
                .content("{\"correo\":\"" + CORREO + "\",\"contrasena\":\"" + CONTRASENA + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshVencidoYRevocacionPersistida() throws Exception {
        var tokens = login();
        jdbc.update("UPDATE sesiones_autenticacion SET vence_en=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 SECOND)");
        consultar(tokens.jwt(), 401);
        refresh(tokens.refresh(), 401);
        assertThat(jdbc.queryForObject("SELECT revocada_en IS NOT NULL FROM sesiones_autenticacion", Boolean.class))
                .isTrue();
    }

    @Test
    void rotacionConcurrenteNoCreaDosSucesoresYRevocaReuso() throws Exception {
        var tokens = login();
        String hash = protector.hash(tokens.refresh());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var inicio = new CountDownLatch(1);
            Callable<Boolean> renovar = () -> {
                inicio.await();
                return accesos.renovar(hash, protector.hash(protector.aleatorio()), Instant.now()).isPresent();
            };
            var a = pool.submit(renovar);
            var b = pool.submit(renovar);
            inicio.countDown();
            assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(true, false);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tokens_renovacion", Integer.class)).isEqualTo(2);
        consultar(tokens.jwt(), 401);
    }

    @Test
    void bootstrapNoPuedeRepetirse() {
        assertThatThrownBy(() -> inicializar.ejecutar(new AdministradorInicial(new NombrePersona("Otra"),
                new NombrePersona("Persona"), new CorreoElectronico("otra@pruebas.invalid")), CONTRASENA))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuarios", Integer.class)).isEqualTo(1);
    }

    @Test
    void bootstrapConcurrenteSoloCreaUnAdministrador() throws Exception {
        jdbc.update("DELETE FROM credenciales");
        jdbc.update("DELETE FROM usuarios_roles");
        jdbc.update("DELETE FROM usuarios");
        jdbc.update("UPDATE inicializacion_acceso SET completada=FALSE");
        try (var pool = Executors.newFixedThreadPool(2)) {
            var inicio = new CountDownLatch(1);
            Callable<Boolean> crear = () -> {
                inicio.await();
                try {
                    inicializar.ejecutar(new AdministradorInicial(new NombrePersona("Persona"),
                            new NombrePersona("Inicial"), new CorreoElectronico(CORREO)), CONTRASENA);
                    return true;
                } catch (IllegalStateException e) {
                    return false;
                }
            };
            var a = pool.submit(crear);
            var b = pool.submit(crear);
            inicio.countDown();
            assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(true, false);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuarios", Integer.class)).isEqualTo(1);
    }

    @Test
    void configuracionInseguraSoloSePermiteEnLocal() {
        var sinLocal = new org.springframework.mock.env.MockEnvironment();
        var insegura = new co.edu.unicolmayor.janussync.infrastructure.config.PropiedadesAutenticacion(
                "janussync", "janussync-api", "privada", "publica", Duration.ofMinutes(5), Duration.ofDays(7), false,
                false);
        assertThatThrownBy(() -> new co.edu.unicolmayor.janussync.infrastructure.config.SeguridadJwtConfiguration()
                .seguridadJwt(null, null, null, insegura, sinLocal)).isInstanceOf(IllegalStateException.class);
        var captcha = new co.edu.unicolmayor.janussync.infrastructure.config.PropiedadesAutenticacion(
                "janussync", "janussync-api", "privada", "publica", Duration.ofMinutes(5), Duration.ofDays(7), true,
                true);
        assertThatThrownBy(() -> new co.edu.unicolmayor.janussync.infrastructure.config.SeguridadJwtConfiguration()
                .seguridadJwt(null, null, null, captcha, sinLocal)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void exigeClaimsDeVigenciaYSesion() throws Exception {
        var tokens = login();
        var original = decoder.decode(tokens.jwt());
        String incompleto = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(),
                JwtClaimsSet.builder().subject(original.getSubject()).issuer("janussync")
                        .audience(List.of("janussync-api"))
                        .issuedAt(Instant.now()).build()))
                .getTokenValue();
        consultar(incompleto, 401);
        mvc.perform(get("/api/v1/roles")).andExpect(status().isUnauthorized());
        mvc.perform(protegido(post("/api/v1/usuarios")).header("Authorization", "Bearer " + tokens.jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void ingresoNormalizaElCorreoConElMismoValueObjectDeInicializacion() throws Exception {
        String correo = new CorreoElectronico("persona@universidadñ.invalid").valor();
        jdbc.update("UPDATE usuarios SET correo=?", correo);
        mvc.perform(protegido(post("/api/v1/auth/login")).contentType("application/json")
                .content("{\"correo\":\"  PERSONA@UNIVERSIDADÑ.INVALID  \",\"contrasena\":\"" + CONTRASENA + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.usuario.correo").value(correo));
    }

    @Test
    void logoutConBearerRevocaAunqueFalteLaCookieRefresh() throws Exception {
        var tokens = login();
        mvc.perform(protegido(post("/api/v1/auth/logout")).header("Authorization", "Bearer " + tokens.jwt()))
                .andExpect(status().isNoContent());
        consultar(tokens.jwt(), 401);
        refresh(tokens.refresh(), 401);
    }

}
