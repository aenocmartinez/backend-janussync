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
@TestPropertySource(properties={"MAIL_HOST=sandbox.smtp.mailtrap.io","MAIL_PORT=2525","MAIL_USERNAME=usuario-prueba","MAIL_PASSWORD=clave-prueba","MAIL_CONNECTION_TIMEOUT_MS=3210","MAIL_READ_TIMEOUT_MS=4321","MAIL_WRITE_TIMEOUT_MS=5432","MAIL_ENABLED=false"})
class IntegracionesMySqlIT {
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

    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired InicializarAdministradorUseCase inicializar;
    @Autowired co.edu.unicolmayor.janussync.domain.port.IntegracionesRepository repositorio;
    @Autowired co.edu.unicolmayor.janussync.application.usecase.ComprobarIntegracionesUseCase caso;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean(name="conexionAcademusoft")
    co.edu.unicolmayor.janussync.infrastructure.adapter.integraciones.ConexionConTiempoLimite oracle;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean(name="conexionBrightspace")
    co.edu.unicolmayor.janussync.infrastructure.adapter.integraciones.ConexionConTiempoLimite brightspace;
    static final String CORREO="integraciones@pruebas.invalid", CONTRASENA="ContrasenaSoloPrueba2026";
    @BeforeEach void preparar() {
        jdbc.update("DELETE FROM auditoria_administrativa");
        jdbc.update("DELETE FROM tokens_renovacion");jdbc.update("DELETE FROM sesiones_autenticacion");
        jdbc.update("DELETE FROM credenciales");jdbc.update("DELETE FROM usuarios_roles");jdbc.update("DELETE FROM usuarios");
        jdbc.update("DELETE FROM limites_autenticacion");jdbc.update("UPDATE inicializacion_acceso SET completada=FALSE");
        jdbc.update("INSERT IGNORE INTO roles_permisos SELECT 'ADMINISTRADOR',codigo FROM permisos");
        jdbc.update("UPDATE estados_integraciones SET reserva_id=NULL,comprobando_desde=NULL,reserva_hasta=NULL,proxima_permitida=NULL,comprobacion_id=NULL,inicio=NULL,fin=NULL,codigo=NULL,duracion_ms=NULL");
        inicializar.ejecutar(new AdministradorInicial(new NombrePersona("Persona"),new NombrePersona("Prueba"),new CorreoElectronico(CORREO)),CONTRASENA);
    }
    String actor(){return jdbc.queryForObject("SELECT id FROM usuarios WHERE correo=?",String.class,CORREO);}
    String ingresar() throws Exception {
        var r=mvc.perform(post("/api/v1/auth/login").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
            .contentType("application/json").content("{\"correo\":\""+CORREO+"\",\"contrasena\":\""+CONTRASENA+"\"}"))
            .andExpect(status().isOk()).andReturn();
        return JsonPath.read(r.getResponse().getContentAsString(),"$.accessToken");
    }
    @Test void configuracionAusenteYAdministracionDisponibleSinServiciosExternos() throws Exception {
        String token=ingresar();
        mvc.perform(get("/api/v1/integraciones").header("Authorization","Bearer "+token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].configured").value(false)).andExpect(jsonPath("$[0].lastCheck").doesNotExist());
        mvc.perform(get("/api/v1/usuarios").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/integraciones/ACADEMUSOFT/comprobaciones").header("Authorization","Bearer "+token)
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.lastCheck.result").value("SIN_CONFIGURAR"));
        org.mockito.Mockito.verify(oracle,org.mockito.Mockito.times(1)).comprobar();
        mvc.perform(get("/api/v1/integraciones").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        org.mockito.Mockito.verify(oracle,org.mockito.Mockito.times(1)).comprobar();
    }
    @Test void resultadosIndependientesPersistidosYAuditoriaSegura() {
        org.mockito.Mockito.doReturn(new ResultadoConexion(DiagnosticoConexion.AUTENTICACION_RECHAZADA)).when(oracle).comprobar();
        org.mockito.Mockito.doAnswer(i->{assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return new ResultadoConexion(DiagnosticoConexion.OK);}).when(brightspace).comprobar();
        caso.comprobar(Plataforma.ACADEMUSOFT,actor());caso.comprobar(Plataforma.BRIGHTSPACE,actor());
        var estados=caso.listar();
        assertThat(estados.get(0).ultima().resultado().codigo()).isEqualTo(DiagnosticoConexion.AUTENTICACION_RECHAZADA);
        assertThat(estados.get(1).ultima().resultado().codigo()).isEqualTo(DiagnosticoConexion.OK);
        assertThat(repositorio.consultar(Plataforma.BRIGHTSPACE,true,15).ultima()).isEqualTo(estados.get(1).ultima());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auditoria_administrativa WHERE accion='INTEGRACION_COMPROBACION'",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT descripcion FROM auditoria_administrativa WHERE accion='INTEGRACION_COMPROBACION'",String.class).toString())
            .doesNotContain("jdbc:","x_a=","clave","password","Exception");
    }
    @Test void resultadoAnteriorVisibleYBloqueoEntreConexionesInclusoSiVenceIndicador() throws Exception {
        var anterior=new ComprobacionConexion(java.util.UUID.randomUUID().toString(),java.time.Instant.now(),java.time.Instant.now(),new ResultadoConexion(DiagnosticoConexion.OK),1);
        try(var primera=repositorio.reservar(Plataforma.ACADEMUSOFT,java.time.Duration.ofSeconds(1),java.time.Duration.ofSeconds(10))){
            repositorio.completar(Plataforma.ACADEMUSOFT,new ComprobacionConexion(primera.id(),primera.inicio(),anterior.fin(),anterior.resultado(),1),actor());
        }
        jdbc.update("UPDATE estados_integraciones SET proxima_permitida=NULL");
        try(var reserva=repositorio.reservar(Plataforma.ACADEMUSOFT,java.time.Duration.ofSeconds(1),java.time.Duration.ofSeconds(10))){
            var estado=repositorio.consultar(Plataforma.ACADEMUSOFT,true,15);
            assertThat(estado.ultima().resultado().codigo()).isEqualTo(DiagnosticoConexion.OK);assertThat(estado.comprobandoDesde()).isNotNull();
            jdbc.update("UPDATE estados_integraciones SET proxima_permitida=NULL,reserva_hasta=TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(6))");
            var otra=java.util.concurrent.CompletableFuture.supplyAsync(()->{
                try(var ignorada=repositorio.reservar(Plataforma.ACADEMUSOFT,java.time.Duration.ofSeconds(1),java.time.Duration.ofSeconds(10))){return false;}
                catch(co.edu.unicolmayor.janussync.domain.exception.ReglaRolException e){return true;}
            });
            assertThat(otra.get(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            try(var independiente=repositorio.reservar(Plataforma.BRIGHTSPACE,java.time.Duration.ofSeconds(1),java.time.Duration.ofSeconds(10))){assertThat(independiente.id()).isNotNull();}
        }
    }
    @Test void limitePersistidoYRecuperacionTrasLiberarReserva() {
        try(var reserva=repositorio.reservar(Plataforma.ACADEMUSOFT,java.time.Duration.ofMinutes(1),java.time.Duration.ofSeconds(10))){assertThat(reserva.id()).isNotNull();}
        assertThatThrownBy(()->repositorio.reservar(Plataforma.ACADEMUSOFT,java.time.Duration.ofMinutes(1),java.time.Duration.ofSeconds(10))).hasMessageContaining("Espera");
        jdbc.update("UPDATE estados_integraciones SET proxima_permitida=NULL");
        try(var recuperada=repositorio.reservar(Plataforma.ACADEMUSOFT,java.time.Duration.ofMinutes(1),java.time.Duration.ofSeconds(10))){assertThat(recuperada.id()).isNotNull();}
    }
    @Test void permisosPorEndpointCsrfYCambiosEnJwtYaEmitido() throws Exception {
        String token=ingresar();
        mvc.perform(get("/api/v1/integraciones")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/integraciones/ACADEMUSOFT/comprobaciones")).andExpect(status().isForbidden());
        jdbc.update("DELETE FROM roles_permisos WHERE rol_id='ADMINISTRADOR' AND permiso_codigo='INTEGRACIONES_COMPROBAR'");
        mvc.perform(get("/api/v1/integraciones").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/integraciones/ACADEMUSOFT/comprobaciones").header("Authorization","Bearer "+token)
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())).andExpect(status().isForbidden());
        jdbc.update("DELETE FROM roles_permisos WHERE rol_id='ADMINISTRADOR' AND permiso_codigo='INTEGRACIONES_CONSULTAR'");
        mvc.perform(get("/api/v1/integraciones").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        org.mockito.Mockito.verify(oracle,org.mockito.Mockito.never()).comprobar();
    }
    @Test void simulacionSeparadaConservaDiagnosticoRealAuditoriaFrecuenciaYPermisos() throws Exception {
        var p = Plataforma.BRIGHTSPACE;
        // Resultado real previo: sigue siendo recuperable al volver a real.
        try (var reserva = repositorio.reservar(p,java.time.Duration.ofMinutes(1),java.time.Duration.ofSeconds(10))) {
            repositorio.completar(p,new ComprobacionConexion(reserva.id(),reserva.inicio(),java.time.Instant.now(),
                new ResultadoConexion(DiagnosticoConexion.AUTENTICACION_RECHAZADA),7,p.alcance()),actor());
        }
        var anterior = repositorio.consultar(p,true,15);
        var simulado = new co.edu.unicolmayor.janussync.infrastructure.adapter.integraciones.BrightspaceSimuladoAdapter();
        org.mockito.Mockito.doReturn(simulado.origen()).when(brightspace).origen();
        org.mockito.Mockito.doReturn(simulado.alcance()).when(brightspace).alcance();
        org.mockito.Mockito.doReturn(simulado.comprobar()).when(brightspace).comprobar();
        String token = ingresar();
        mvc.perform(get("/api/v1/integraciones").header("Authorization","Bearer "+token))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].simulated").value(false))
            .andExpect(jsonPath("$[1].simulated").value(true)).andExpect(jsonPath("$[1].lastCheck").doesNotExist());
        mvc.perform(post("/api/v1/integraciones/BRIGHTSPACE/comprobaciones").header("Authorization","Bearer "+token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.simulated").value(true))
            .andExpect(jsonPath("$.lastCheck.simulated").value(true)).andExpect(jsonPath("$.lastCheck.result").value("SIMULADO"))
            .andExpect(jsonPath("$.lastCheck.incidentId").doesNotExist());
        assertThat(repositorio.consultar(p,true,15)).isEqualTo(anterior);
        var persistido = repositorio.consultar(p,OrigenComprobacion.SIMULADO,true,15);
        assertThat(persistido.ultima().resultado().codigo()).isEqualTo(DiagnosticoConexion.SIMULADO);
        assertThat(persistido.fallandoDesde()).isNull();
        assertThat(persistido.ultima().alcance()).contains("no se contactó al proveedor");
        assertThat(jdbc.queryForList("SELECT descripcion FROM auditoria_administrativa WHERE accion='INTEGRACION_COMPROBACION'",String.class))
            .anyMatch(d -> d.contains("(SIMULADO)"));
        mvc.perform(post("/api/v1/integraciones/BRIGHTSPACE/comprobaciones").header("Authorization","Bearer "+token))
            .andExpect(status().isConflict());
        jdbc.update("DELETE FROM roles_permisos WHERE rol_id='ADMINISTRADOR' AND permiso_codigo='INTEGRACIONES_COMPROBAR'");
        mvc.perform(post("/api/v1/integraciones/BRIGHTSPACE/comprobaciones").header("Authorization","Bearer "+token))
            .andExpect(status().isForbidden());
        org.mockito.Mockito.verify(brightspace,org.mockito.Mockito.times(1)).comprobar();
        org.mockito.Mockito.verify(oracle,org.mockito.Mockito.never()).comprobar();
    }
    @Test void falloDePersistenciaNoSeConvierteEnListaVacia() {
        // Las lecturas propagan fallos MySQL al handler HTTP; no se transforma en ausencia de plataformas.
        var repo=org.mockito.Mockito.mock(co.edu.unicolmayor.janussync.domain.port.IntegracionesRepository.class);
        org.mockito.Mockito.when(repo.consultar(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyBoolean(),org.mockito.ArgumentMatchers.anyInt()))
            .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("prueba"));
        var servicio=new co.edu.unicolmayor.janussync.application.usecase.ComprobarIntegracionesUseCase(repo,List.of(oracle,brightspace),
            new TiemposConexion(java.time.Duration.ofSeconds(1),java.time.Duration.ofSeconds(2),java.time.Duration.ofSeconds(1),15),java.time.Clock.systemUTC());
        assertThatThrownBy(servicio::listar).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
}
