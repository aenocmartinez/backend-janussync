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
class UsuariosMySqlIT {
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


    @Autowired co.edu.unicolmayor.janussync.application.usecase.GestionarUsuariosUseCase gestionarUsuarios;
    @Autowired co.edu.unicolmayor.janussync.application.usecase.ContrasenasUseCase contrasenas;
    @Autowired co.edu.unicolmayor.janussync.domain.port.GestionUsuariosRepository gestionUsuarios;
    @org.springframework.test.context.bean.override.mockito.MockitoBean co.edu.unicolmayor.janussync.domain.port.CorreoPort correo;
    final java.util.List<String> enlaces=new java.util.concurrent.CopyOnWriteArrayList<>();
    @BeforeEach void correoPreparado(){
      enlaces.clear();org.mockito.Mockito.when(correo.enviar(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any()))
        .thenAnswer(i->{assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();enlaces.add(i.getArgument(3));return co.edu.unicolmayor.janussync.domain.port.CorreoPort.Resultado.ENVIADO;});
    }
    String actor(){return jdbc.queryForObject("SELECT id FROM usuarios WHERE correo=?",String.class,CORREO);}
    Usuario crear(String correo){return gestionarUsuarios.crear(actor(),"Nueva","Persona",correo,"Cargo",EstadoRegistro.ACTIVO,Set.of("OPERADOR_EV"),Set.of());}
    String token(){return enlaces.getLast().split("token=",2)[1];}
    void nuevoEnvio(){jdbc.update("DELETE FROM limites_autenticacion");}
    @Test void creaEditaYRechazaDuplicadosSinModificarRolesDesdeDatos(){
      var u=crear("NUEVA@pruebas.invalid");assertThat(u.correo().valor()).isEqualTo("nueva@pruebas.invalid");assertThat(u.contrasenaActualizada()).isNull();
      assertThatThrownBy(()->crear(" Nueva@pruebas.invalid ")).hasMessageContaining("Ya existe");
      var editado=gestionarUsuarios.editar(actor(),u.id().valor().toString(),"Renombrada","Persona","otra@pruebas.invalid",null);
      assertThat(editado.roles()).containsExactly("OPERADOR_EV");assertThat(editado.correo().valor()).isEqualTo("otra@pruebas.invalid");
    }
    @Test void asignaRetiraRolesYConservaInactivosPeroRechazaNuevosEInventados(){
      var u=crear("roles@otra.invalid");String id=u.id().valor().toString();
      gestionarUsuarios.asignar(actor(),id,Set.of("OPERADOR_EV","ADMINISTRADOR"),Set.of());
      gestionar.estado(actor(),"OPERADOR_EV",EstadoRegistro.INACTIVO);
      assertThat(gestionarUsuarios.asignar(actor(),id,Set.of("OPERADOR_EV"),Set.of()).roles()).containsExactly("OPERADOR_EV");
      var otro=gestionarUsuarios.crear(actor(),"Otra","Persona","sinrol@pruebas.invalid",null,EstadoRegistro.ACTIVO,Set.of("ADMINISTRADOR"),Set.of());
      assertThatThrownBy(()->gestionarUsuarios.asignar(actor(),otro.id().valor().toString(),Set.of("OPERADOR_EV"),Set.of())).hasMessageContaining("activos");
      assertThatThrownBy(()->gestionarUsuarios.asignar(actor(),id,Set.of("INVENTADO"),Set.of())).hasMessageContaining("no existe");
    }
    @Test void noInventaProgramasYConservaAsignacionesAcademicasAnteriores(){
      var u=crear("programas@pruebas.invalid");String id=u.id().valor().toString();
      assertThatThrownBy(()->gestionarUsuarios.asignar(actor(),id,Set.of("COORDINADOR"),Set.of())).hasMessageContaining("Academusoft");
      assertThatThrownBy(()->gestionarUsuarios.asignar(actor(),id,Set.of("ADMINISTRADOR"),Set.of("inventado"))).hasMessageContaining("Academusoft");
      assertThat(gestionarUsuarios.asignar(actor(),actor(),Set.of("ADMINISTRADOR","COORDINADOR"),Set.of()).roles()).contains("COORDINADOR");
    }
    @Test void invitacionSmtpFueraDeTransaccionHashSoloYUsoUnico(){
      var u=crear("invitada@pruebas.invalid");String id=u.id().valor().toString();
      var enviada=contrasenas.enviarAdministrativo(actor(),id,"INVITACION");String token=token();
      assertThat(enviada.ultimoCorreo().estado()).isEqualTo("ENVIADO");
      assertThat(contrasenas.inspeccionar(token,"INVITACION").status()).isEqualTo("VALIDO");
      assertThat(contrasenas.inspeccionar(token,"RESTABLECIMIENTO").status()).isEqualTo("INVALIDO");
      assertThat(jdbc.queryForObject("SELECT hash_token FROM enlaces_contrasena WHERE usuario_id=?",String.class,id)).doesNotContain(token);
      assertThat(contrasenas.inspeccionar(token,"INVITACION").status()).isEqualTo("VALIDO");
      contrasenas.completar(token,"INVITACION","NuevaClave1234","NuevaClave1234");
      assertThat(contrasenas.inspeccionar(token,"INVITACION").status()).isEqualTo("UTILIZADO");
      assertThatThrownBy(()->contrasenas.completar(token,"INVITACION","OtraClave1234","OtraClave1234")).hasMessageContaining("enlace");
      assertThat(gestionarUsuarios.obtener(id).contrasenaActualizada()).isNotNull();
    }
    @Test void reenvioLimitaInvalidaAnteriorYUsaCorreoActual(){
      var u=crear("reenviar@pruebas.invalid");String id=u.id().valor().toString();contrasenas.enviarAdministrativo(actor(),id,"INVITACION");String anterior=token();
      assertThatThrownBy(()->contrasenas.enviarAdministrativo(actor(),id,"INVITACION")).hasMessageContaining("Espera");
      gestionarUsuarios.editar(actor(),id,"Nueva","Persona","nuevo-correo@pruebas.invalid",null);
      assertThat(contrasenas.inspeccionar(anterior,"INVITACION").status()).isEqualTo("INVALIDO");nuevoEnvio();contrasenas.enviarAdministrativo(actor(),id,"INVITACION");
      assertThat(contrasenas.inspeccionar(token(),"INVITACION").email()).isEqualTo("nuevo-correo@pruebas.invalid");
    }
    @Test void expiracionPropositoYConsumoConcurrente() throws Exception {
      var u=crear("concurrente@pruebas.invalid");String id=u.id().valor().toString();contrasenas.enviarAdministrativo(actor(),id,"INVITACION");String anterior=token();
      jdbc.update("UPDATE enlaces_contrasena SET vence_en=DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 1 SECOND) WHERE usuario_id=?",id);
      assertThat(contrasenas.inspeccionar(anterior,"INVITACION").status()).isEqualTo("EXPIRADO");nuevoEnvio();contrasenas.enviarAdministrativo(actor(),id,"INVITACION");String token=token();
      var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var inicio=new java.util.concurrent.CountDownLatch(1);
      try{var tareas=java.util.stream.IntStream.range(0,2).mapToObj(i->pool.submit(()->{inicio.await();try{contrasenas.completar(token,"INVITACION","Concurrente1234","Concurrente1234");return "OK";}catch(co.edu.unicolmayor.janussync.domain.exception.ReglaRolException e){return e.codigo();}})).toList();inicio.countDown();
      assertThat(java.util.List.of(tareas.get(0).get(15,java.util.concurrent.TimeUnit.SECONDS),tareas.get(1).get(15,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder("OK","LINK_USED");}finally{pool.shutdownNow();}
    }
    @Test void fallosSmtpConservanCuentaYNoAfirmanEntrega(@Autowired co.edu.unicolmayor.janussync.domain.port.ConsultaAdministrativaRepository consultas){
      var u=crear("fallo@pruebas.invalid");String id=u.id().valor().toString();
      org.mockito.Mockito.when(correo.enviar(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any())).thenReturn(co.edu.unicolmayor.janussync.domain.port.CorreoPort.Resultado.FALLIDO);
      assertThat(contrasenas.enviarAdministrativo(actor(),id,"INVITACION").ultimoCorreo().estado()).isEqualTo("FALLIDO");assertThat(gestionarUsuarios.obtener(id)).isNotNull();nuevoEnvio();
      org.mockito.Mockito.when(correo.enviar(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any())).thenReturn(co.edu.unicolmayor.janussync.domain.port.CorreoPort.Resultado.INCIERTO);
      assertThat(contrasenas.enviarAdministrativo(actor(),id,"INVITACION").ultimoCorreo().estado()).isEqualTo("INCIERTO");
      assertThat(consultas.incidencias()).noneMatch(e->e.get("userId").equals(id));
    }
    @Test void recuperacionGenericaNoEludeInvitacionNiCuentasInactivas(){
      crear("sinclave@pruebas.invalid");contrasenas.recuperar("sinclave@pruebas.invalid","ip");contrasenas.recuperar("noexiste@pruebas.invalid","ip");contrasenas.recuperar("inactiva@pruebas.invalid","ip");assertThat(enlaces).isEmpty();
      contrasenas.recuperar(CORREO,"ip");assertThat(enlaces).hasSize(1);assertThat(contrasenas.inspeccionar(token(),"RESTABLECIMIENTO").status()).isEqualTo("VALIDO");
    }
    String ingreso(String correo) throws Exception {
      var res=mvc.perform(post("/api/v1/auth/login").cookie(new Cookie("JANUS-CSRF","prueba")).header("X-CSRF-TOKEN","prueba").contentType("application/json").content("{\"correo\":\""+correo+"\",\"contrasena\":\""+CONTRASENA+"\"}")).andExpect(status().isOk()).andReturn();return JsonPath.read(res.getResponse().getContentAsString(),"$.accessToken");
    }
    @Test void cambioYRestablecimientoRevocanJwtPrevios() throws Exception {
      String jwt=ingreso(CORREO);assertThatThrownBy(()->contrasenas.cambiar(actor(),"incorrecta","NuevaClave12345","NuevaClave12345")).hasMessageContaining("actual");
      contrasenas.cambiar(actor(),CONTRASENA,"NuevaClave12345","NuevaClave12345");mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+jwt)).andExpect(status().isUnauthorized());
      // Sesión abierta explícitamente para verificar revocación por restablecimiento.
      var sid=java.util.UUID.randomUUID();gestionUsuarios.enTransaccion(()->{jdbc.update("INSERT INTO sesiones_autenticacion VALUES(?,?,CURRENT_TIMESTAMP,DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 1 DAY),NULL)",sid.toString(),actor());return null;});
      nuevoEnvio();contrasenas.recuperar(CORREO,"ip");contrasenas.completar(token(),"RESTABLECIMIENTO","Restablecida1234","Restablecida1234");
      assertThat(jdbc.queryForObject("SELECT revocada_en IS NOT NULL FROM sesiones_autenticacion WHERE id=?",Boolean.class,sid.toString())).isTrue();
    }
    @Test void inactivarRevocaYReactivarNoResucitaSesionesYProtegeAdministrador() throws Exception {
      assertThatThrownBy(()->gestionarUsuarios.estado(actor(),actor(),EstadoRegistro.INACTIVO)).hasMessageContaining("conservarse");
      var u=gestionarUsuarios.crear(actor(),"Segunda","Persona","segunda@pruebas.invalid",null,EstadoRegistro.ACTIVO,Set.of("ADMINISTRADOR"),Set.of());String id=u.id().valor().toString();
      jdbc.update("INSERT INTO credenciales SELECT ?,hash_contrasena,actualizado_en FROM credenciales LIMIT 1",id);String jwt=ingreso("segunda@pruebas.invalid");
      gestionarUsuarios.estado(actor(),id,EstadoRegistro.INACTIVO);mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+jwt)).andExpect(status().isUnauthorized());
      gestionarUsuarios.estado(actor(),id,EstadoRegistro.ACTIVO);mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+jwt)).andExpect(status().isUnauthorized());
    }
    @Test void auditoriaIncluyeUsuariosYRolesYNoSecretos(){
      var u=crear("audit@pruebas.invalid");String id=u.id().valor().toString();contrasenas.enviarAdministrativo(actor(),id,"INVITACION");String token=token();
      gestionar.editar(actor(),"OPERADOR_EV","Operador renombrado",null,null);
      var textos=jdbc.queryForList("SELECT descripcion FROM auditoria_administrativa",String.class);
      assertThat(textos).anyMatch(t->t.contains("Cuenta creada")).anyMatch(t->t.contains("Datos:"));
      assertThat(String.join(" ",textos)).doesNotContain(token).doesNotContain("hash_contrasena").doesNotContain(CONTRASENA);
    }
    @Test void permisosDeEscrituraYRetiradaConJwtEmitido() throws Exception {
      var u=crear("api@pruebas.invalid");String id=u.id().valor().toString();String jwt=ingreso(CORREO);
      mvc.perform(put("/api/v1/usuarios/"+id+"/roles").header("Authorization","Bearer "+jwt).contentType("application/json").content("{\"roles\":[\"ADMINISTRADOR\"],\"programas\":[]}")).andExpect(status().isOk());
      jdbc.update("DELETE FROM roles_permisos WHERE rol_id='ADMINISTRADOR' AND permiso_codigo IN ('USUARIOS_EDITAR','USUARIOS_ESTADO','USUARIOS_ACCESO','ROLES_ASIGNAR','HISTORIAL_CONSULTAR')");
      mvc.perform(put("/api/v1/usuarios/"+id).header("Authorization","Bearer "+jwt).contentType("application/json").content("{}")).andExpect(status().isForbidden());
      mvc.perform(put("/api/v1/usuarios/"+id+"/estado").header("Authorization","Bearer "+jwt).contentType("application/json").content("{\"estado\":\"INACTIVO\"}")).andExpect(status().isForbidden());
      mvc.perform(put("/api/v1/usuarios/"+id+"/roles").header("Authorization","Bearer "+jwt).contentType("application/json").content("{\"roles\":[\"OPERADOR_EV\"]}")).andExpect(status().isForbidden());
      mvc.perform(post("/api/v1/usuarios/"+id+"/invitacion").header("Authorization","Bearer "+jwt)).andExpect(status().isForbidden());
      mvc.perform(get("/api/v1/auditoria").header("Authorization","Bearer "+jwt)).andExpect(status().isForbidden());
      mvc.perform(post("/api/v1/auth/recuperacion").contentType("application/json").content("{\"correo\":\"nadie@pruebas.invalid\"}")).andExpect(status().isForbidden());
    }
    @Test void mapeaVariablesSmtpATimeoutsYStartTlsSinSslImplicito(@Autowired org.springframework.mail.javamail.JavaMailSender mail){
      var smtp=(org.springframework.mail.javamail.JavaMailSenderImpl)mail;assertThat(smtp.getHost()).isEqualTo("sandbox.smtp.mailtrap.io");assertThat(smtp.getPort()).isEqualTo(2525);assertThat(smtp.getUsername()).isEqualTo("usuario-prueba");
      assertThat(smtp.getJavaMailProperties()).containsEntry("mail.smtp.starttls.required","true").containsEntry("mail.smtp.ssl.enable","false").containsEntry("mail.smtp.connectiontimeout","3210").containsEntry("mail.smtp.timeout","4321").containsEntry("mail.smtp.writetimeout","5432");
    }
    @Test void concurrenciaEntreEstadoDeUsuarioYRolNoPierdeAdministracion() throws Exception {
      String principal=actor();
      var respaldo=gestionar.crear(principal,"Respaldo","",rol("ADMINISTRADOR").permisos());
      gestionarUsuarios.asignar(principal,principal,Set.of(respaldo.id()),Set.of());
      var segundo=gestionarUsuarios.crear(principal,"Segundo","Administrador","respaldo@pruebas.invalid",null,EstadoRegistro.ACTIVO,Set.of("ADMINISTRADOR"),Set.of());
      var limitado=gestionar.crear(principal,"Gestor de estado","",Set.of("ROLES_ESTADO","USUARIOS_ESTADO"));
      var operador=gestionarUsuarios.crear(principal,"Operador","Estado","estado@pruebas.invalid",null,EstadoRegistro.ACTIVO,Set.of(limitado.id()),Set.of());String solicitante=operador.id().valor().toString();
      jdbc.update("INSERT INTO credenciales SELECT ?,hash_contrasena,actualizado_en FROM credenciales WHERE usuario_id=?",segundo.id().valor().toString(),principal);
      var inicio=new java.util.concurrent.CountDownLatch(1);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
      try{
       var a=pool.submit(()->{inicio.await();try{gestionarUsuarios.estado(solicitante,principal,EstadoRegistro.INACTIVO);return "OK";}catch(co.edu.unicolmayor.janussync.domain.exception.ReglaRolException e){return e.codigo();}});
       var b=pool.submit(()->{inicio.await();try{gestionar.estado(solicitante,"ADMINISTRADOR",EstadoRegistro.INACTIVO);return "OK";}catch(co.edu.unicolmayor.janussync.domain.exception.ReglaRolException e){return e.codigo();}});
       inicio.countDown();assertThat(java.util.List.of(a.get(15,java.util.concurrent.TimeUnit.SECONDS),b.get(15,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder("OK","ADMIN_REQUIRED");
      }finally{pool.shutdownNow();}
    }

    @Test void crearConInvitacionExigeAccesoAntesDeCrearYFalloNoDejaAuditoria(){
      String principal=actor();long anteriores=jdbc.queryForObject("SELECT COUNT(*) FROM usuarios",Long.class);
      long eventos=jdbc.queryForObject("SELECT COUNT(*) FROM auditoria_administrativa",Long.class);
      jdbc.update("DELETE FROM roles_permisos WHERE rol_id='ADMINISTRADOR' AND permiso_codigo='USUARIOS_ACCESO'");
      assertThatThrownBy(()->gestionarUsuarios.crear(principal,"Sin","Permiso","sinpermiso@pruebas.invalid",null,EstadoRegistro.ACTIVO,Set.of("OPERADOR_EV"),Set.of(),true)).hasMessageContaining("permiso");
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuarios",Long.class)).isEqualTo(anteriores);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auditoria_administrativa",Long.class)).isEqualTo(eventos);
    }

    @Test void unaInvitacionPendienteNoReemplazaAlUltimoAdministradorConAcceso(){
      String principal=actor();var pendiente=gestionarUsuarios.crear(principal,"Pendiente","Administración","pendiente@pruebas.invalid",null,EstadoRegistro.ACTIVO,Set.of("ADMINISTRADOR"),Set.of());
      assertThatThrownBy(()->gestionarUsuarios.estado(principal,principal,EstadoRegistro.INACTIVO)).hasMessageContaining("contraseña establecida");
      assertThatThrownBy(()->gestionar.estado(principal,"ADMINISTRADOR",EstadoRegistro.INACTIVO)).hasMessageContaining("contraseña establecida");
      jdbc.update("INSERT INTO credenciales SELECT ?,hash_contrasena,actualizado_en FROM credenciales WHERE usuario_id=?",pendiente.id().valor().toString(),principal);
      assertThat(gestionarUsuarios.estado(principal,principal,EstadoRegistro.INACTIVO).estado()).isEqualTo(EstadoRegistro.INACTIVO);
    }

    @Test void ingresoEnCursoNoAbreSesionConContrasenaAnteriorAlCambio(@Autowired co.edu.unicolmayor.janussync.application.usecase.AutenticacionUseCase auth,@Autowired co.edu.unicolmayor.janussync.domain.port.ProtectorCredenciales protector) throws Exception {
      String principal=actor();String hash=protector.proteger("ActualizadaConcurrente1234");
      var bloqueo=new java.util.concurrent.CountDownLatch(1);var liberar=new java.util.concurrent.CountDownLatch(1);var intento=new java.util.concurrent.CountDownLatch(1);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
      try{
       var cambio=pool.submit(()->gestionUsuarios.enTransaccion(()->{bloqueo.countDown();try{if(!liberar.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("Plazo de prueba agotado");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}gestionUsuarios.establecerContrasena(principal,hash,java.time.Instant.now());gestionUsuarios.revocarSesiones(principal,java.time.Instant.now());return null;}));
       assertThat(bloqueo.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
       var ingreso=pool.submit(()->{intento.countDown();return auth.ingresar(CORREO,CONTRASENA);});
       assertThat(intento.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
       assertThatThrownBy(()->ingreso.get(300,java.util.concurrent.TimeUnit.MILLISECONDS)).isInstanceOf(java.util.concurrent.TimeoutException.class);
       liberar.countDown();cambio.get(5,java.util.concurrent.TimeUnit.SECONDS);
       assertThatThrownBy(()->ingreso.get(5,java.util.concurrent.TimeUnit.SECONDS)).isInstanceOf(java.util.concurrent.ExecutionException.class).hasCauseInstanceOf(co.edu.unicolmayor.janussync.domain.exception.AccesoRechazadoException.class);
       assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sesiones_autenticacion WHERE revocada_en IS NULL",Long.class)).isZero();
      }finally{liberar.countDown();pool.shutdownNow();}
    }

}
