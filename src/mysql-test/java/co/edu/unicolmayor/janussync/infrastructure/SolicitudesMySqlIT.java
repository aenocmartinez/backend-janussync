package co.edu.unicolmayor.janussync.infrastructure;

import co.edu.unicolmayor.janussync.application.usecase.InicializarAdministradorUseCase;
import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.domain.port.RolRepository;
import co.edu.unicolmayor.janussync.domain.valueobject.*;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import co.edu.unicolmayor.janussync.domain.model.solicitudes.*;
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
class SolicitudesMySqlIT {
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
    @Autowired co.edu.unicolmayor.janussync.application.usecase.solicitudes.GestionarSolicitudesUseCase caso;
    @Autowired co.edu.unicolmayor.janussync.domain.port.SolicitudesRepository repo;
    @Autowired co.edu.unicolmayor.janussync.domain.port.ProtectorCredenciales protector;
    @org.springframework.test.context.bean.override.mockito.MockitoBean co.edu.unicolmayor.janussync.domain.port.AcademicosSolicitudPort fuente;
    @org.springframework.test.context.bean.override.mockito.MockitoBean co.edu.unicolmayor.janussync.domain.port.CorreoPort correo;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean co.edu.unicolmayor.janussync.domain.port.AuditoriaPort auditoria;
    String admin,coordinador,colaborador,operador,ajeno;
    static final String PASSWORD="ContrasenaAislada2026";
    @BeforeEach void preparar(){
        for(String tabla:List.of("avisos_solicitudes","comandos_solicitudes","seguimiento_solicitudes","solicitudes_integracion","usuarios_programas","envios_acceso","enlaces_contrasena","auditoria_administrativa","tokens_renovacion","sesiones_autenticacion","credenciales","usuarios_roles","usuarios","limites_autenticacion"))jdbc.update("DELETE FROM "+tabla);
        jdbc.update("UPDATE inicializacion_acceso SET completada=FALSE");jdbc.update("UPDATE roles SET estado='ACTIVO'");
        jdbc.update("INSERT IGNORE INTO roles_permisos SELECT 'ADMINISTRADOR',codigo FROM permisos WHERE modulo<>'SOLICITUDES'");
        jdbc.update("DELETE FROM roles_permisos WHERE rol_id='ADMINISTRADOR' AND permiso_codigo LIKE 'SOLICITUDES_%'");
        jdbc.update("INSERT IGNORE INTO roles_permisos VALUES('COORDINADOR','SOLICITUDES_PREPARAR'),('COORDINADOR','SOLICITUDES_ENVIAR'),('COLABORADOR','SOLICITUDES_PREPARAR'),('OPERADOR_EV','SOLICITUDES_ATENDER')");
        inicializar.ejecutar(new AdministradorInicial(new NombrePersona("Admin"),new NombrePersona("Prueba"),new CorreoElectronico("admin@pruebas.invalid")),PASSWORD);
        admin=jdbc.queryForObject("SELECT id FROM usuarios WHERE correo='admin@pruebas.invalid'",String.class);
        jdbc.update("DELETE FROM catalogo_programas");
        jdbc.update("INSERT INTO catalogo_programas VALUES('001','C1','Programa Uno',TRUE,'1',NULL,NULL,NULL,TRUE),('002','C2','Programa Dos',TRUE,'1',NULL,NULL,NULL,TRUE)");
        jdbc.update("UPDATE actualizacion_catalogo_programas SET ultima_correcta=UTC_TIMESTAMP(6),resultado='CORRECTO',total=2");
        coordinador=crear("coordinador",Set.of("COORDINADOR"),Set.of("001"));
        colaborador=crear("colaborador",Set.of("COLABORADOR"),Set.of("001"));
        operador=crear("operador",Set.of("OPERADOR_EV"),Set.of());
        ajeno=crear("ajeno",Set.of("COORDINADOR"),Set.of("002"));
        org.mockito.Mockito.when(fuente.periodos(org.mockito.ArgumentMatchers.anyString())).thenReturn(List.of(new OpcionAcademica("P1","2026-2")));
        org.mockito.Mockito.when(fuente.grupo(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyBoolean())).thenAnswer(i->grupo(i.getArgument(0),i.getArgument(1),i.getArgument(2),i.getArgument(3)));
        org.mockito.Mockito.when(correo.avisarSolicitud(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenAnswer(i->{assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return co.edu.unicolmayor.janussync.domain.port.CorreoPort.Resultado.ENVIADO;});
    }
    String crear(String nombre,Set<String> roles,Set<String> programas){return usuarios.crear(admin,nombre,"Prueba",nombre+"@pruebas.invalid",null,EstadoRegistro.ACTIVO,roles,programas).id().valor().toString();}
    GrupoAcademico grupo(String programa,String periodo,String id,boolean incluir){return new GrupoAcademico(id,"M01","Asignatura real del doble","Grupo A","Sede de prueba",programa,periodo,incluir?List.of(new PersonaAcademica("D01","Docente Prueba")):List.of(),incluir?List.of(new PersonaAcademica("E01","Estudiante Prueba")):List.of());}
    CambioSolicitud cambio(Long version,String...grupos){return new CambioSolicitud(UUID.randomUUID().toString(),version,"001","P1",coordinador,List.of(grupos),"Nota de preparación");}
    Solicitud borrador(){return caso.guardar(colaborador,null,cambio(null,"G1"));}
    String token(String id)throws Exception{
        jdbc.update("INSERT INTO credenciales(usuario_id,hash_contrasena,actualizado_en) VALUES(?,?,UTC_TIMESTAMP(6))",id,protector.proteger(PASSWORD));
        String email=jdbc.queryForObject("SELECT correo FROM usuarios WHERE id=?",String.class,id);
        String csrf=JsonPath.read(mvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse().getContentAsString(),"$.token");
        return JsonPath.read(mvc.perform(post("/api/v1/auth/login").cookie(new Cookie("JANUS-CSRF",csrf)).header("X-CSRF-TOKEN",csrf).contentType("application/json").content("{\"correo\":\""+email+"\",\"contrasena\":\""+PASSWORD+"\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),"$.accessToken");
    }
    @Test void recorridoCompletoContadoresInstantaneasYDestinatarios(){
        var s=borrador();assertThat(caso.listar(operador,"","",1).total()).isZero();
        assertThatThrownBy(()->caso.obtener(operador,s.id())).hasMessageContaining("No se encontró");
        assertThatThrownBy(()->caso.transicion(colaborador,s.id(),UUID.randomUUID().toString(),s.version(),false,null)).hasMessageContaining("No puedes");
        var enviada=caso.transicion(coordinador,s.id(),UUID.randomUUID().toString(),s.version(),false,null);
        assertThat(caso.pendientes(operador)).isEqualTo(1);assertThat(caso.pendientes(coordinador)).isZero();
        caso.obtener(operador,s.id());assertThat(caso.pendientes(operador)).isEqualTo(1);
        assertThatThrownBy(()->caso.guardar(colaborador,s.id(),cambio(enviada.version(),"G2"))).hasMessageContaining("no puede editarse");
        assertThatThrownBy(()->caso.transicion(operador,s.id(),UUID.randomUUID().toString(),enviada.version(),true,"ok")).hasMessageContaining("Explica");
        var devuelta=caso.transicion(operador,s.id(),UUID.randomUUID().toString(),enviada.version(),true,"Corrige la selección del grupo antes de reenviar.");
        assertThat(caso.pendientes(operador)).isZero();assertThat(caso.pendientes(coordinador)).isEqualTo(1);assertThat(caso.pendientes(colaborador)).isEqualTo(1);
        var corregida=caso.guardar(colaborador,s.id(),cambio(devuelta.version(),"G2"));
        assertThat(caso.seguimiento(coordinador,s.id()).stream().filter(e->e.accion().equals("ENVIAR")).findFirst().orElseThrow().contenido().grupos().getFirst().id()).isEqualTo("G1");
        var reenviada=caso.transicion(coordinador,s.id(),UUID.randomUUID().toString(),corregida.version(),false,null);
        assertThat(reenviada.contenido().grupos().getFirst().id()).isEqualTo("G2");assertThat(caso.pendientes(operador)).isEqualTo(1);assertThat(caso.pendientes(coordinador)).isZero();
        org.mockito.Mockito.verify(correo,org.mockito.Mockito.times(4)).avisarSolicitud(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString());
        assertThat(caso.avisos(coordinador,s.id())).allMatch(a->a.get("resultado").equals("ACEPTADO_SMTP"));
    }
    @Test void alcanceActualResponsablePermisosYRolesCombinados(){
        var s=borrador();assertThatThrownBy(()->caso.obtener(admin,s.id())).hasMessageContaining("acceso");
        assertThatThrownBy(()->caso.obtener(ajeno,s.id())).hasMessageContaining("No se encontró");
        assertThatThrownBy(()->caso.periodos(ajeno,"001")).hasMessageContaining("No puedes");
        assertThatThrownBy(()->caso.guardar(colaborador,null,new CambioSolicitud(UUID.randomUUID().toString(),null,"001","P1",ajeno,List.of(),""))).hasMessageContaining("coordinador activo");
        var u=usuarios.obtener(coordinador);usuarios.asignar(admin,coordinador,Set.of("COORDINADOR","OPERADOR_EV"),Set.of("001"),u.versionAsignacion());
        var enviada=caso.transicion(coordinador,s.id(),UUID.randomUUID().toString(),s.version(),false,null);assertThat(caso.pendientes(coordinador)).isEqualTo(1);
        var devuelta=caso.transicion(operador,s.id(),UUID.randomUUID().toString(),enviada.version(),true,"Revisa el grupo y confirma la selección académica.");assertThat(caso.pendientes(coordinador)).isEqualTo(1);
        jdbc.update("DELETE FROM roles_permisos WHERE rol_id='COORDINADOR' AND permiso_codigo='SOLICITUDES_ENVIAR'");
        assertThatThrownBy(()->caso.transicion(coordinador,s.id(),UUID.randomUUID().toString(),devuelta.version(),false,null)).hasMessageContaining("No puedes");
        jdbc.update("UPDATE usuarios SET estado='INACTIVO' WHERE id=?",colaborador);assertThatThrownBy(()->caso.obtener(colaborador,s.id())).hasMessageContaining("acceso");
    }
    @Test void dobleEnvioFalloSmtpYSinDestinatariosConservanTransicion(){
        var s=borrador();String op=UUID.randomUUID().toString();
        org.mockito.Mockito.when(correo.avisarSolicitud(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenReturn(co.edu.unicolmayor.janussync.domain.port.CorreoPort.Resultado.FALLIDO);
        var uno=caso.transicion(coordinador,s.id(),op,s.version(),false,null);var dos=caso.transicion(coordinador,s.id(),op,s.version(),false,null);
        assertThat(uno).isEqualTo(dos);assertThat(caso.avisos(coordinador,s.id())).singleElement().satisfies(a->assertThat(a.get("resultado")).isEqualTo("FALLIDO"));
        org.mockito.Mockito.verify(correo,org.mockito.Mockito.times(1)).avisarSolicitud(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString());
        jdbc.update("UPDATE usuarios SET estado='INACTIVO' WHERE id=?",operador);var siguiente=borrador();caso.transicion(coordinador,siguiente.id(),UUID.randomUUID().toString(),siguiente.version(),false,null);
        assertThat(caso.avisos(coordinador,siguiente.id())).singleElement().satisfies(a->assertThat(a.get("resultado")).isEqualTo("SIN_DESTINATARIOS"));
    }
    @Test void concurrenciaYEdicionesVersionadas()throws Exception{
        var s=borrador();var listos=new java.util.concurrent.CountDownLatch(2);var continuar=new java.util.concurrent.CountDownLatch(1);
        org.mockito.Mockito.when(fuente.grupo("001","P1","G1",true)).thenAnswer(i->{assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();listos.countDown();continuar.await(10,java.util.concurrent.TimeUnit.SECONDS);return grupo("001","P1","G1",true);});
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){
          var trabajos=new ArrayList<java.util.concurrent.Future<Boolean>>();
          for(int i=0;i<2;i++)trabajos.add(executor.submit(()->{try{caso.transicion(coordinador,s.id(),UUID.randomUUID().toString(),s.version(),false,null);return true;}catch(co.edu.unicolmayor.janussync.domain.exception.ReglaRolException e){assertThat(e.codigo()).isEqualTo("CONFLICT");return false;}}));
          assertThat(listos.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();continuar.countDown();assertThat(trabajos.get(0).get()).isNotEqualTo(trabajos.get(1).get());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM seguimiento_solicitudes WHERE accion='ENVIAR'",Integer.class)).isEqualTo(1);
        assertThatThrownBy(()->caso.guardar(colaborador,s.id(),cambio(s.version(),"G2"))).hasMessageContaining("cambió");
    }
    @Test void fuenteManipuladaYFalloNoPierdenBorradorNiAuditoriaAtomica(){
        var s=borrador();org.mockito.Mockito.when(fuente.grupo("001","P1","G2",false)).thenReturn(grupo("002","P1","G2",false));
        assertThatThrownBy(()->caso.guardar(colaborador,s.id(),cambio(s.version(),"G2"))).hasMessageContaining("no pertenecen");
        org.mockito.Mockito.when(fuente.grupo("001","P1","G1",true)).thenThrow(new co.edu.unicolmayor.janussync.domain.exception.AcademusoftNoDisponibleException());
        assertThatThrownBy(()->caso.transicion(coordinador,s.id(),UUID.randomUUID().toString(),s.version(),false,null)).isInstanceOf(co.edu.unicolmayor.janussync.domain.exception.AcademusoftNoDisponibleException.class);
        assertThat(caso.obtener(coordinador,s.id())).isEqualTo(s);
        org.mockito.Mockito.doReturn(grupo("001","P1","G1",true)).when(fuente).grupo("001","P1","G1",true);
        org.mockito.Mockito.doThrow(new IllegalStateException("Fallo de prueba")).when(auditoria).registrar(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("SOLICITUD"),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString());
        assertThatThrownBy(()->caso.transicion(coordinador,s.id(),UUID.randomUUID().toString(),s.version(),false,null)).isInstanceOf(IllegalStateException.class);assertThat(caso.obtener(coordinador,s.id())).isEqualTo(s);
    }
    @Test void contadorHttpNoDependeDePaginaYJwtYaEmitidoAplicaRevocacion()throws Exception{
        String jwt=token(coordinador);var s=borrador();
        mvc.perform(get("/api/v1/solicitudes/pendientes").header("Authorization","Bearer "+jwt)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(post("/api/v1/solicitudes/"+s.id()+"/envio").header("Authorization","Bearer "+jwt).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        jdbc.update("DELETE FROM roles_permisos WHERE rol_id='COORDINADOR' AND permiso_codigo LIKE 'SOLICITUDES_%'");
        mvc.perform(get("/api/v1/solicitudes/pendientes").header("Authorization","Bearer "+jwt)).andExpect(status().isForbidden());
    }
    @Test void totalPendienteEsIndependienteDePaginacionYDestinatariosInactivos(){
        for(int i=0;i<21;i++){var s=borrador();caso.transicion(coordinador,s.id(),UUID.randomUUID().toString(),s.version(),false,null);}
        assertThat(caso.listar(operador,"","ENVIADA",1).elementos()).hasSize(20);
        assertThat(caso.listar(operador,"","ENVIADA",2).elementos()).hasSize(1);
        assertThat(caso.pendientes(operador)).isEqualTo(21);
        assertThat(caso.listar(operador,"programa uno","",1).total()).isEqualTo(21);
        var enviada=caso.listar(operador,"","",2).elementos().getFirst();
        jdbc.update("UPDATE usuarios SET estado='INACTIVO' WHERE id=?",colaborador);
        caso.transicion(operador,enviada.id(),UUID.randomUUID().toString(),enviada.version(),true,"Corrige los grupos seleccionados para el programa.");
        assertThat(caso.pendientes(operador)).isEqualTo(20);
        var avisos=caso.avisos(coordinador,enviada.id());assertThat(avisos).hasSize(2);
    }
    @Test void endpointsConCsrfPermisosPorProgramaYCreacionIdempotente()throws Exception{
        var cambio=cambio(null,"G1");var creado=caso.guardar(colaborador,null,cambio);assertThat(caso.guardar(colaborador,null,cambio).id()).isEqualTo(creado.id());
        String tokenColaborador=token(colaborador),tokenOperador=token(operador),tokenAjeno=token(ajeno);
        mvc.perform(get("/api/v1/solicitudes/"+creado.id()).header("Authorization","Bearer "+tokenOperador)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/solicitudes/"+creado.id()).header("Authorization","Bearer "+tokenColaborador)).andExpect(status().isOk()).andExpect(jsonPath("$.puedeEnviar").value(false));
        mvc.perform(get("/api/v1/solicitudes/academicos/periodos").param("programa","001").header("Authorization","Bearer "+tokenAjeno)).andExpect(status().isForbidden());
        String csrf=JsonPath.read(mvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse().getContentAsString(),"$.token");
        var contenido="{\"operacion\":\""+UUID.randomUUID()+"\",\"version\":"+creado.version()+"}";
        mvc.perform(post("/api/v1/solicitudes/"+creado.id()+"/envio").cookie(new Cookie("JANUS-CSRF",csrf)).header("X-CSRF-TOKEN",csrf).header("Authorization","Bearer "+tokenColaborador).contentType("application/json").content(contenido)).andExpect(status().isForbidden());
    }

    @Test void rolDinamicoAutorizaPorPermisosYNoPorNombre(){
        jdbc.update("INSERT INTO roles(id,nombre,descripcion,estado,inicial) VALUES('academico-dinamico','Otro nombre académico','Prueba','ACTIVO',FALSE) ON DUPLICATE KEY UPDATE estado='ACTIVO'");
        jdbc.update("INSERT IGNORE INTO roles_permisos VALUES('academico-dinamico','SOLICITUDES_PREPARAR'),('academico-dinamico','SOLICITUDES_ENVIAR')");
        String dinamico=crear("dinamico",Set.of("academico-dinamico"),Set.of("001"));
        var borrador=caso.guardar(dinamico,null,new CambioSolicitud(UUID.randomUUID().toString(),null,"001","P1",dinamico,List.of("G1"),""));
        assertThat(borrador.responsableId()).isEqualTo(dinamico);
        assertThat(caso.transicion(dinamico,borrador.id(),UUID.randomUUID().toString(),borrador.version(),false,null).estado()).isEqualTo(EstadoSolicitud.ENVIADA);
    }

}
