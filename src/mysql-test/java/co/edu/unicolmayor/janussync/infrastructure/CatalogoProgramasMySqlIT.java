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
@TestPropertySource(properties={"MAIL_HOST=sandbox.smtp.mailtrap.io","MAIL_PORT=2525","MAIL_USERNAME=usuario-prueba","MAIL_PASSWORD=clave-prueba","MAIL_CONNECTION_TIMEOUT_MS=3210","MAIL_READ_TIMEOUT_MS=4321","MAIL_WRITE_TIMEOUT_MS=5432","MAIL_ENABLED=false","JANUS_PROGRAMAS_SYNC_ENABLED=false"})
class CatalogoProgramasMySqlIT {
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
    @Autowired co.edu.unicolmayor.janussync.application.usecase.GestionarUsuariosUseCase usuarios;
    @Autowired co.edu.unicolmayor.janussync.domain.port.UsuarioRepository consulta;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    co.edu.unicolmayor.janussync.domain.port.CatalogoProgramasRepository programas;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    co.edu.unicolmayor.janussync.domain.port.FuenteProgramasAcademicos fuente;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    co.edu.unicolmayor.janussync.domain.port.AuditoriaPort auditoria;
    static final String CORREO="programas@pruebas.invalid", CONTRASENA="ContrasenaSoloPrueba2026";
    @BeforeEach void preparar() {
        jdbc.update("DELETE FROM usuarios_programas");jdbc.update("DELETE FROM envios_acceso");jdbc.update("DELETE FROM enlaces_contrasena");
        jdbc.update("DELETE FROM auditoria_administrativa");jdbc.update("DELETE FROM tokens_renovacion");jdbc.update("DELETE FROM sesiones_autenticacion");
        jdbc.update("DELETE FROM credenciales");jdbc.update("DELETE FROM usuarios_roles");jdbc.update("DELETE FROM usuarios");
        jdbc.update("DELETE FROM limites_autenticacion");jdbc.update("UPDATE inicializacion_acceso SET completada=FALSE");
        jdbc.update("UPDATE roles SET estado='ACTIVO'");
        jdbc.update("INSERT IGNORE INTO roles_permisos SELECT 'ADMINISTRADOR',codigo FROM permisos");
        inicializar.ejecutar(new AdministradorInicial(new NombrePersona("Persona"),new NombrePersona("Prueba"),new CorreoElectronico(CORREO)),CONTRASENA);
        jdbc.update("DELETE FROM catalogo_programas");
        jdbc.update("INSERT INTO catalogo_programas VALUES('001','COD','Programa prueba',TRUE,'1','Modalidad','Metodología','Jornada',TRUE),('002','COD','Programa prueba',TRUE,'1','Modalidad','Metodología','Jornada',TRUE),('003','COD','Programa prueba',FALSE,'0','Modalidad','Metodología','Jornada',TRUE)");
        jdbc.update("UPDATE actualizacion_catalogo_programas SET ultima_correcta=UTC_TIMESTAMP(6),ultimo_intento=NULL,resultado='CORRECTO',total=3,reserva_id=NULL,reserva_hasta=NULL,proxima_permitida=NULL,proxima_programada=NULL WHERE id=1");
    }
    String actor(){return jdbc.queryForObject("SELECT id FROM usuarios WHERE correo=?",String.class,CORREO);}
    Usuario crear(){return usuarios.crear(actor(),"Otra","Persona","otra@pruebas.invalid",null,EstadoRegistro.ACTIVO,Set.of("OPERADOR_EV"),Set.of());}
    String ingresar() throws Exception {
        var respuesta=mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
        String tokenCsrf=JsonPath.read(respuesta.getContentAsString(),"$.token");
        var r=mvc.perform(post("/api/v1/auth/login").cookie(new Cookie("JANUS-CSRF",tokenCsrf)).header("X-CSRF-TOKEN",tokenCsrf)
            .contentType("application/json").content("{\"correo\":\""+CORREO+"\",\"contrasena\":\""+CONTRASENA+"\"}"))
            .andExpect(status().isOk()).andReturn();
        return JsonPath.read(r.getResponse().getContentAsString(),"$.accessToken");
    }
    @Autowired co.edu.unicolmayor.janussync.application.usecase.ActualizarCatalogoProgramasUseCase actualizar;
    ProgramaAcademico p(String id,boolean activo){return new ProgramaAcademico(new ProgramaId(id),"COD-"+id,"Programa "+id,activo,"Modalidad","Metodología","Jornada");}
    LecturaProgramas lectura(ProgramaAcademico... programas){return new LecturaProgramas(List.of(programas),programas.length,true);}
    void libre(){jdbc.update("UPDATE actualizacion_catalogo_programas SET proxima_permitida=NULL");}
    @Test void cargaInicialYConsultaMySqlSinInvocarFuente() {
        jdbc.update("DELETE FROM catalogo_programas");jdbc.update("UPDATE actualizacion_catalogo_programas SET ultima_correcta=NULL,total=0,resultado='NUNCA'");
        assertThat(programas.buscar(new ConsultaProgramas("",1,20)).elementos()).isEmpty();
        org.mockito.Mockito.when(fuente.leerCatalogo()).thenAnswer(i->{
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return lectura(p("001",true),p("002",true),p("003",false));
        });
        var estado=actualizar.manual(actor());assertThat(estado.total()).isEqualTo(3);assertThat(estado.ultimaActualizacion()).isNotNull();assertThat(estado.resultado()).isEqualTo("CORRECTO");
        assertThat(programas.buscar(new ConsultaProgramas("COD",1,1)).total()).isEqualTo(2);
        assertThat(programas.buscar(new ConsultaProgramas("COD",2,1)).elementos()).hasSize(1);
        assertThat(programas.resolver(Set.of(new ProgramaId("001"))).getFirst().id().valor()).isEqualTo("001");
        var u=crear();usuarios.asignar(actor(),u.id().valor().toString(),Set.of("COORDINADOR"),Set.of("001"),u.versionAsignacion());
        org.mockito.Mockito.verify(fuente,org.mockito.Mockito.times(1)).leerCatalogo();
        assertThat(jdbc.queryForList("SELECT descripcion FROM auditoria_administrativa WHERE accion='CATALOGO_PROGRAMAS'",String.class)).anyMatch(d->d.contains("correcta"));
    }
    @Test void lecturasFallidasIncompletasVaciasODuplicadasConservanVersionValida() {
        var antes=programas.estado();
        for(var lectura:List.of(new LecturaProgramas(List.of(),0,true),new LecturaProgramas(List.of(p("001",true)),2,true),new LecturaProgramas(List.of(p("001",true)),1,false),new LecturaProgramas(List.of(p("001",true),p("001",true)),2,true))) {
            libre();org.mockito.Mockito.doReturn(lectura).when(fuente).leerCatalogo();
            assertThatThrownBy(()->actualizar.manual(actor())).isInstanceOf(co.edu.unicolmayor.janussync.domain.exception.ActualizacionCatalogoException.class);
            assertThat(programas.estado().ultimaActualizacion()).isEqualTo(antes.ultimaActualizacion());
            assertThat(programas.buscar(new ConsultaProgramas("",1,20)).total()).isEqualTo(2);
            assertThat(programas.estado().resultado()).isEqualTo("LECTURA_RECHAZADA");
        }
        libre();org.mockito.Mockito.doThrow(new co.edu.unicolmayor.janussync.domain.exception.AcademusoftNoDisponibleException()).when(fuente).leerCatalogo();
        assertThatThrownBy(()->actualizar.manual(actor())).isInstanceOf(co.edu.unicolmayor.janussync.domain.exception.ActualizacionCatalogoException.class);
        assertThat(programas.estado().ultimaActualizacion()).isEqualTo(antes.ultimaActualizacion());
        assertThat(programas.estado().resultado()).isEqualTo("FALLO_FUENTE");
    }
    @Test void falloDeAuditoriaReviertePublicacionCompleta() {
        var antes=programas.estado();
        org.mockito.Mockito.when(fuente.leerCatalogo()).thenReturn(lectura(p("999",true)));
        org.mockito.Mockito.doThrow(new IllegalStateException("Fallo aislado de auditoría")).when(auditoria).registrar(
            org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("CATALOGO_PROGRAMAS"),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.startsWith("Actualización correcta"));
        assertThatThrownBy(()->actualizar.manual(actor())).isInstanceOf(co.edu.unicolmayor.janussync.domain.exception.ActualizacionCatalogoException.class);
        assertThat(programas.resolver(Set.of(new ProgramaId("999")))).isEmpty();
        assertThat(programas.buscar(new ConsultaProgramas("",1,20)).total()).isEqualTo(2);
        assertThat(programas.estado().ultimaActualizacion()).isEqualTo(antes.ultimaActualizacion());
        assertThat(programas.estado().resultado()).isEqualTo("FALLO_PUBLICACION");
    }
    @Test void ausentesEInactivosConservanAsignacionesPeroBloqueanAdiciones() {
        var u=crear();String id=u.id().valor().toString();var asignado=usuarios.asignar(actor(),id,Set.of("COORDINADOR"),Set.of("001","002"),u.versionAsignacion());
        org.mockito.Mockito.when(fuente.leerCatalogo()).thenReturn(lectura(p("002",true),p("003",false)));
        actualizar.manual(actor());
        var ausente=programas.resolver(Set.of(new ProgramaId("001"))).getFirst();
        assertThat(ausente.presenteEnFuente()).isFalse();assertThat(ausente.estadoOficial()).isEqualTo("1");assertThat(ausente.elegible()).isFalse();
        assertThat(usuarios.obtener(id).programas()).containsExactlyInAnyOrder("001","002");
        var otro=usuarios.crear(actor(),"Nueva","Persona","tercera@pruebas.invalid",null,EstadoRegistro.ACTIVO,Set.of("OPERADOR_EV"),Set.of());
        for(String codigo:List.of("001","003"))assertThatThrownBy(()->usuarios.asignar(actor(),otro.id().valor().toString(),Set.of("COORDINADOR"),Set.of(codigo),otro.versionAsignacion())).hasMessageContaining("ausente");
        assertThat(usuarios.asignar(actor(),id,Set.of("COORDINADOR"),Set.of("002"),asignado.versionAsignacion()).programas()).containsExactly("002");
    }
    @Test void antiguedadBloqueaAdicionesYPermiteConsultarORetirarExistentes() {
        var u=crear();String id=u.id().valor().toString();var anterior=usuarios.asignar(actor(),id,Set.of("COORDINADOR"),Set.of("001","002"),u.versionAsignacion());
        jdbc.update("UPDATE actualizacion_catalogo_programas SET ultima_correcta=TIMESTAMPADD(HOUR,-49,UTC_TIMESTAMP(6))");
        assertThat(programas.estado().utilizable(java.time.Instant.now(),java.time.Duration.ofHours(48))).isFalse();
        assertThat(programas.resolver(Set.of(new ProgramaId("001")))).hasSize(1);
        var retirado=usuarios.asignar(actor(),id,Set.of("COORDINADOR"),Set.of("001"),anterior.versionAsignacion());
        assertThat(retirado.programas()).containsExactly("001");
        assertThatThrownBy(()->usuarios.asignar(actor(),id,Set.of("COORDINADOR"),Set.of("001","002"),retirado.versionAsignacion())).hasMessageContaining("vigencia");
        jdbc.update("UPDATE actualizacion_catalogo_programas SET ultima_correcta=NULL");
        assertThatThrownBy(()->usuarios.asignar(actor(),id,Set.of("COORDINADOR"),Set.of("001","002"),retirado.versionAsignacion())).hasMessageContaining("catálogo");
        org.mockito.Mockito.verifyNoInteractions(fuente);
    }
    @Test void unaInstanciaActualizaYLasDemasNoRepitenLaEjecucionProgramada() throws Exception {
        var entra=new java.util.concurrent.CountDownLatch(1);var continuar=new java.util.concurrent.CountDownLatch(1);
        org.mockito.Mockito.when(fuente.leerCatalogo()).thenAnswer(i->{entra.countDown();assertThat(continuar.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();return lectura(p("001",true));});
        var siguiente=java.time.Instant.now().plus(java.time.Duration.ofDays(1));
        var trabajo=java.util.concurrent.CompletableFuture.supplyAsync(()->actualizar.sistema(siguiente));
        assertThat(entra.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        try {
            assertThatThrownBy(()->actualizar.manual(actor())).hasMessageContaining("en curso");
            assertThat(actualizar.sistema(siguiente).actualizando()).isTrue();
        } finally {continuar.countDown();}
        trabajo.get(10,java.util.concurrent.TimeUnit.SECONDS);libre();
        actualizar.sistema(siguiente);
        org.mockito.Mockito.verify(fuente,org.mockito.Mockito.times(1)).leerCatalogo();
        assertThat(jdbc.queryForList("SELECT actor_nombre FROM auditoria_administrativa WHERE accion='CATALOGO_PROGRAMAS'",String.class)).contains("Sistema");
    }
    @Test void publicacionAtomicaNoExponeFilasParciales() throws Exception {
        var escrito=new java.util.concurrent.CountDownLatch(1);var continuar=new java.util.concurrent.CountDownLatch(1);
        org.mockito.Mockito.doAnswer(i->{
            if(((String)i.getArgument(5)).startsWith("Actualización correcta")){escrito.countDown();assertThat(continuar.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();}
            return i.callRealMethod();
        }).when(auditoria).registrar(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("CATALOGO_PROGRAMAS"),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString());
        var nuevos=java.util.stream.IntStream.range(100,150).mapToObj(i->p(Integer.toString(i),true)).toList();
        var reserva=programas.reservar(actor(),java.time.Duration.ofSeconds(1),null).orElseThrow();
        var futuro=java.util.concurrent.CompletableFuture.runAsync(()->programas.publicar(reserva,nuevos,actor(),()->{}));
        try {
            assertThat(escrito.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(programas.buscar(new ConsultaProgramas("",1,50)).total()).isEqualTo(2);
            assertThat(programas.resolver(Set.of(new ProgramaId("100")))).isEmpty();
        } finally {continuar.countDown();}
        futuro.get(10,java.util.concurrent.TimeUnit.SECONDS);reserva.close();
        assertThat(programas.buscar(new ConsultaProgramas("",1,50)).total()).isEqualTo(50);
        assertThat(programas.resolver(Set.of(new ProgramaId("001"))).getFirst().presenteEnFuente()).isFalse();
    }
    @Test void revalidaVigenciaDentroDelGuardadoSinOracle() {
        var u=crear();String id=u.id().valor().toString();
        org.mockito.Mockito.doAnswer(i->{
            var estado=i.callRealMethod();
            if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())jdbc.update("UPDATE actualizacion_catalogo_programas SET ultima_correcta=TIMESTAMPADD(HOUR,-49,UTC_TIMESTAMP(6))");
            return estado;
        }).when(programas).estado();
        assertThatThrownBy(()->usuarios.asignar(actor(),id,Set.of("COORDINADOR"),Set.of("001"),u.versionAsignacion())).hasMessageContaining("vigencia");
        assertThat(usuarios.obtener(id)).isEqualTo(u);org.mockito.Mockito.verifyNoInteractions(fuente);
    }
    @Test void permisoExplicitoCsrfAuditoriaYFrecuenciaPorHttp() throws Exception {
        String token=ingresar();
        var respuestaCsrf=mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
        String valorCsrf=new tools.jackson.databind.ObjectMapper().readTree(respuestaCsrf.getContentAsString()).get("token").asText();
        var cookieCsrf=new Cookie("JANUS-CSRF",valorCsrf);
        mvc.perform(post("/api/v1/programas/actualizacion").header("Authorization","Bearer "+token).cookie(cookieCsrf).header("X-CSRF-TOKEN","incorrecto")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/programas/actualizacion")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/programas/estado").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.usable").value(true));
        mvc.perform(post("/api/v1/programas/actualizacion").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        jdbc.update("DELETE FROM roles_permisos WHERE rol_id='ADMINISTRADOR' AND permiso_codigo='PROGRAMAS_ACTUALIZAR'");
        mvc.perform(post("/api/v1/programas/actualizacion").header("Authorization","Bearer "+token).cookie(cookieCsrf).header("X-CSRF-TOKEN",valorCsrf)).andExpect(status().isForbidden());
        org.mockito.Mockito.verifyNoInteractions(fuente);
        jdbc.update("INSERT INTO roles_permisos VALUES('ADMINISTRADOR','PROGRAMAS_ACTUALIZAR')");
        org.mockito.Mockito.when(fuente.leerCatalogo()).thenReturn(lectura(p("001",true)));
        mvc.perform(post("/api/v1/programas/actualizacion").header("Authorization","Bearer "+token).cookie(cookieCsrf).header("X-CSRF-TOKEN",valorCsrf)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(post("/api/v1/programas/actualizacion").header("Authorization","Bearer "+token).cookie(cookieCsrf).header("X-CSRF-TOKEN",valorCsrf)).andExpect(status().isConflict());
        org.mockito.Mockito.verify(fuente,org.mockito.Mockito.times(1)).leerCatalogo();
        assertThat(jdbc.queryForList("SELECT actor_id FROM auditoria_administrativa WHERE accion='CATALOGO_PROGRAMAS'",String.class)).contains(actor());
    }
}
