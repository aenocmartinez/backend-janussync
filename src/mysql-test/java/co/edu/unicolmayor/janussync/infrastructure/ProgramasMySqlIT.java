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
class ProgramasMySqlIT {
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
        var r=mvc.perform(post("/api/v1/auth/login").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
            .contentType("application/json").content("{\"correo\":\""+CORREO+"\",\"contrasena\":\""+CONTRASENA+"\"}"))
            .andExpect(status().isOk()).andReturn();
        return JsonPath.read(r.getResponse().getContentAsString(),"$.accessToken");
    }
    @Test void asignacionMultipleRetiradaYRolesRenombrados() {
        var u=crear();String id=u.id().valor().toString();
        jdbc.update("UPDATE roles SET nombre='Responsable renombrado' WHERE id='COORDINADOR'");
        var asignado=usuarios.asignar(actor(),id,Set.of("COORDINADOR","COLABORADOR"),Set.of("001","002"),u.versionAsignacion());
        assertThat(asignado.programas()).containsExactlyInAnyOrder("001","002");
        assertThat(asignado.versionAsignacion()).isGreaterThan(u.versionAsignacion());
        org.mockito.Mockito.clearInvocations(fuente);
        org.mockito.Mockito.when(fuente.leerCatalogo()).thenThrow(new co.edu.unicolmayor.janussync.domain.exception.AcademusoftNoDisponibleException());
        var retirado=usuarios.asignar(actor(),id,Set.of("COORDINADOR"),Set.of("001"),asignado.versionAsignacion());
        assertThat(retirado.programas()).containsExactly("001");
        org.mockito.Mockito.verifyNoInteractions(fuente);
        assertThat(consulta.obtener(UsuarioId.desde(id)).orElseThrow().programas()).containsExactly("001");
        assertThat(jdbc.queryForList("SELECT descripcion FROM auditoria_administrativa WHERE accion='CAMBIO_ROLES'",String.class))
            .anyMatch(d->d.contains("programas")&&d.contains("001"));
        assertThatThrownBy(()->jdbc.update("INSERT INTO usuarios_programas VALUES(?,?)",id,"001")).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }
    @Test void rechazaInexistentesInactivosVaciosYManipuladosSinCambios() {
        var u=crear();String id=u.id().valor().toString();
        for(String rol:List.of("COORDINADOR","COLABORADOR"))
            assertThatThrownBy(()->usuarios.asignar(actor(),id,Set.of(rol),Set.of(),u.versionAsignacion())).hasMessageContaining("al menos");
        for(String programa:List.of("003","inexistente","1' OR '1'='1"))
            assertThatThrownBy(()->usuarios.asignar(actor(),id,Set.of("COORDINADOR"),Set.of(programa),u.versionAsignacion())).hasMessageContaining("no existe, está inactivo o está ausente");
        assertThat(consulta.obtener(UsuarioId.desde(id)).orElseThrow()).isEqualTo(u);
    }
    @Test void falloOracleNoBloqueaAdicionesConCatalogoLocalVigente() {
        var u=crear();String id=u.id().valor().toString();
        org.mockito.Mockito.when(fuente.leerCatalogo()).thenThrow(new co.edu.unicolmayor.janussync.domain.exception.AcademusoftNoDisponibleException());
        var asignado=usuarios.asignar(actor(),id,Set.of("COORDINADOR"),Set.of("001","002"),u.versionAsignacion());
        assertThat(asignado.programas()).containsExactlyInAnyOrder("001","002");
        org.mockito.Mockito.verifyNoInteractions(fuente);
    }
    @Test void guardadoAtomicoYAuditoriaConRollback() {
        var u=crear();String id=u.id().valor().toString();
        org.mockito.Mockito.doThrow(new IllegalStateException("Fallo de auditoría aislado")).when(auditoria).registrar(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("CAMBIO_ROLES"),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString());
        assertThatThrownBy(()->usuarios.asignar(actor(),id,Set.of("COORDINADOR"),Set.of("001"),u.versionAsignacion())).isInstanceOf(IllegalStateException.class);
        assertThat(consulta.obtener(UsuarioId.desde(id)).orElseThrow()).isEqualTo(u);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuarios_programas WHERE usuario_id=?",Integer.class,id)).isZero();
    }
    @Test void concurrenciaNoSobrescribeYReautorizaTrasConsultaExterna() throws Exception {
        var u=crear();String id=u.id().valor().toString();
        var trabajos=List.of("001","002").stream().map(p->java.util.concurrent.CompletableFuture.supplyAsync(()->{
            try {usuarios.asignar(actor(),id,Set.of("COORDINADOR"),Set.of(p),u.versionAsignacion());return "OK";}
            catch(co.edu.unicolmayor.janussync.domain.exception.ReglaRolException e){return e.codigo();}
        })).toList();
        assertThat(List.of(trabajos.get(0).get(),trabajos.get(1).get())).containsExactlyInAnyOrder("OK","CONFLICT");
        var actual=consulta.obtener(UsuarioId.desde(id)).orElseThrow();assertThat(actual.programas()).hasSize(1);
        org.mockito.Mockito.doAnswer(i->{
            jdbc.update("DELETE FROM roles_permisos WHERE rol_id='ADMINISTRADOR' AND permiso_codigo='ROLES_ASIGNAR'");
            return List.of(new ProgramaAcademico(new ProgramaId("003"),"C","Programa",true,null,null,null));
        }).when(programas).resolver(org.mockito.ArgumentMatchers.any());
        assertThatThrownBy(()->usuarios.asignar(actor(),id,Set.of("COORDINADOR"),Set.of("003"),actual.versionAsignacion())).hasMessageContaining("permiso");
        assertThat(consulta.obtener(UsuarioId.desde(id)).orElseThrow()).isEqualTo(actual);
    }
    @Test void protegeUltimoAdministradorYAfectaSesionConMismoJwt() throws Exception {
        String token=ingresar();String id=actor();long version=usuarios.obtener(id).versionAsignacion();
        assertThatThrownBy(()->usuarios.asignar(id,id,Set.of("COORDINADOR"),Set.of("001"),version)).hasMessageContaining("administración");
        usuarios.asignar(id,id,Set.of("ADMINISTRADOR","COORDINADOR"),Set.of("001"),version);
        mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+token)).andExpect(status().isOk())
            .andExpect(jsonPath("$.usuario.programas[0]").value("001"));
        jdbc.update("DELETE FROM roles_permisos WHERE rol_id='ADMINISTRADOR' AND permiso_codigo='ROLES_ASIGNAR'");
        mvc.perform(get("/api/v1/programas").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
    }
    @Test void contratoHttpPermisosDuplicadosVersionYErroresExternos() throws Exception {
        String token=ingresar();var u=crear();String id=u.id().valor().toString();
        mvc.perform(get("/api/v1/programas")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/programas?busqueda=C").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.elementos[0].id").value("001"));
        mvc.perform(get("/api/v1/programas/resolver?ids=001,002").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        for(String json:List.of("{\"roles\":[\"COORDINADOR\"],\"programas\":[\"001\",\"001\"],\"versionAsignacion\":"+u.versionAsignacion()+"}","{\"roles\":[\"COORDINADOR\"],\"programas\":[\"001\"]}"))
            mvc.perform(put("/api/v1/usuarios/"+id+"/roles").header("Authorization","Bearer "+token).contentType("application/json").content(json)).andExpect(status().isBadRequest());
        org.mockito.Mockito.when(fuente.leerCatalogo()).thenThrow(new co.edu.unicolmayor.janussync.domain.exception.AcademusoftNoDisponibleException());
        mvc.perform(get("/api/v1/programas").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        org.mockito.Mockito.verifyNoInteractions(fuente);
        assertThat(consulta.obtener(UsuarioId.desde(id)).orElseThrow()).isEqualTo(u);
    }
}
