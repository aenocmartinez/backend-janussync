package co.edu.unicolmayor.janussync.application;
import co.edu.unicolmayor.janussync.application.usecase.AutorizarConexionUseCase;
import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.domain.port.*;
import co.edu.unicolmayor.janussync.domain.exception.AutorizacionExternaException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AutorizarConexionTest {
    final Instant ahora=Instant.parse("2026-10-30T12:00:00Z");
    final UUID usuario=UUID.randomUUID(), sesion=UUID.randomUUID();
    final AutorizacionExternaPort proveedor=mock(AutorizacionExternaPort.class);
    final AccesoRepository acceso=mock(AccesoRepository.class);
    final Memoria repo=new Memoria();
    final AutorizarConexionUseCase caso=new AutorizarConexionUseCase(proveedor,repo,acceso,Clock.fixed(ahora,ZoneOffset.UTC));
    static final String STATE="s".repeat(43), NAVEGADOR="n".repeat(43);
    @BeforeEach void preparar() {
        when(proveedor.configurada()).thenReturn(true);
        when(proveedor.configuracion()).thenReturn("config");
        when(proveedor.urlAutorizacion(anyString())).thenAnswer(a->"https://autorizacion.invalid/?state="+a.getArgument(0));
        when(acceso.permisosActuales(eq(sesion),eq(usuario),any())).thenReturn(Optional.of(Set.of(AutorizarConexionUseCase.PERMISO)));
        when(proveedor.verificar(anyString())).thenReturn(new ResultadoConexion(DiagnosticoConexion.OK));
    }
    void intento(Instant fin) {
        repo.guardarIntento(AutorizarConexionUseCase.hash(STATE),AutorizarConexionUseCase.hash(NAVEGADOR),new IntentoAutorizacion(usuario,sesion,fin,"config"));
    }
    CredencialesExternas tokens(Instant vence,String refresh) { return new CredencialesExternas("access-privado",refresh,vence,"users:own_profile:read"); }
    @Test void inicioAutorizadoConEntropiaYCaducidadSinGuardarStateCrudo() {
        var inicio=caso.iniciar(usuario,sesion);
        assertThat(inicio.navegador()).hasSize(43);
        assertThat(repo.intento.venceEn()).isEqualTo(ahora.plusSeconds(300));
        assertThat(repo.hash).hasSize(64).isNotEqualTo(inicio.url().substring(inicio.url().indexOf("state=")+6));
        assertThat(inicio.toString()).doesNotContain(inicio.navegador());
    }
    @Test void stateInvalidoAusenteVencidoYCookieDeOtroIntentoNoIntercambianCodigo() {
        intento(ahora.plusSeconds(1));
        assertThatThrownBy(()->caso.completar(null,NAVEGADOR,"codigo",null)).isInstanceOf(AutorizacionExternaException.class);
        assertThatThrownBy(()->caso.completar(STATE,"x".repeat(43),"codigo",null)).isInstanceOf(AutorizacionExternaException.class);
        intento(ahora);
        assertThatThrownBy(()->caso.completar(STATE,NAVEGADOR,"codigo",null)).isInstanceOf(AutorizacionExternaException.class);
        verify(proveedor,never()).intercambiar(anyString());
    }
    @Test void rechazoDelProveedorConsumeStateYNoActivaConexion() {
        intento(ahora.plusSeconds(30));
        assertThatThrownBy(()->caso.completar(STATE,NAVEGADOR,null,"access_denied")).isInstanceOf(AutorizacionExternaException.class);
        assertThatThrownBy(()->caso.completar(STATE,NAVEGADOR,"codigo",null)).isInstanceOf(AutorizacionExternaException.class);
        assertThat(repo.conexion).isNull();verify(proveedor,never()).intercambiar(anyString());
    }
    @Test void noIniciaSinPermisoYRevalidaDespuesDeIdentidadAntesDeActivar() {
        when(acceso.permisosActuales(any(),any(),any())).thenReturn(Optional.empty());
        assertThatThrownBy(()->caso.iniciar(usuario,sesion)).isInstanceOf(AutorizacionExternaException.class);
        intento(ahora.plusSeconds(30));
        when(acceso.permisosActuales(any(),any(),any())).thenReturn(Optional.of(Set.of(AutorizarConexionUseCase.PERMISO)),Optional.empty());
        when(proveedor.intercambiar("codigo")).thenReturn(tokens(ahora.plusSeconds(3600),"refresh"));
        assertThatThrownBy(()->caso.completar(STATE,NAVEGADOR,"codigo",null)).isInstanceOf(AutorizacionExternaException.class);
        assertThat(repo.conexion).isNull();
    }
    @Test void verificaIdentidadYRechazaStateReutilizado() {
        intento(ahora.plusSeconds(30));when(proveedor.intercambiar("codigo")).thenReturn(tokens(ahora.plusSeconds(3600),"refresh"));
        caso.completar(STATE,NAVEGADOR,"codigo",null);
        assertThat(caso.estado().estado()).isEqualTo("AUTORIZADA");
        assertThatThrownBy(()->caso.completar(STATE,NAVEGADOR,"codigo",null)).isInstanceOf(AutorizacionExternaException.class);
        verify(proveedor,times(1)).intercambiar("codigo");
    }
    @Test void identidadSinPermisosNoSustituyeConexionAnterior() {
        intento(ahora.plusSeconds(30));when(proveedor.intercambiar(anyString())).thenReturn(tokens(ahora.plusSeconds(3600),"refresh"));
        when(proveedor.verificar(anyString())).thenReturn(new ResultadoConexion(DiagnosticoConexion.PERMISOS_INSUFICIENTES));
        assertThatThrownBy(()->caso.completar(STATE,NAVEGADOR,"codigo",null)).isInstanceOf(AutorizacionExternaException.class);
        assertThat(repo.conexion).isNull();
    }
    @Test void renovacionConservaRefreshOmitidoYRemplazaElDevuelto() {
        for (String siguiente:new String[]{null,"refresh-nuevo"}) {
            repo.conexion=new ConexionAutorizada(tokens(ahora,"refresh-anterior"),"AUTORIZADA","config",ahora,DiagnosticoConexion.OK);
            doAnswer(a->{
                assertThat(repo.conexion.estado()).isEqualTo("RENOVANDO");
                return tokens(ahora.plusSeconds(3600),siguiente);
            }).when(proveedor).renovar("refresh-anterior");
            assertThat(caso.comprobar().codigo()).isEqualTo(DiagnosticoConexion.OK);
            assertThat(repo.conexion.credenciales().renovacion()).isEqualTo(siguiente==null?"refresh-anterior":siguiente);
        }
    }
    @Test void renovacionAmbiguaOInvalidGrantNoSeReintenta() {
        for(var codigo:List.of(DiagnosticoConexion.SIN_COMUNICACION,DiagnosticoConexion.AUTORIZACION_REVOCADA)) {
            reset(proveedor);when(proveedor.configurada()).thenReturn(true);when(proveedor.configuracion()).thenReturn("config");
            repo.conexion=new ConexionAutorizada(tokens(ahora,"refresh"),"AUTORIZADA","config",ahora,DiagnosticoConexion.OK);
            when(proveedor.renovar("refresh")).thenThrow(new AutorizacionExternaException(codigo));
            assertThat(caso.comprobar().codigo()).isEqualTo(codigo);caso.comprobar();
            verify(proveedor,times(1)).renovar("refresh");assertThat(repo.conexion.credenciales()).isNull();
        }
    }
    @Test void marcaDurableTrasCaidaImpideReutilizarRefresh() {
        repo.conexion=new ConexionAutorizada(tokens(ahora,"refresh"),"RENOVANDO","config",ahora,DiagnosticoConexion.OK);
        assertThat(caso.comprobar().codigo()).isEqualTo(DiagnosticoConexion.AUTORIZACION_REVOCADA);
        verify(proveedor,never()).renovar(anyString());
    }
    @Test void dosInstanciasCoordinadasSoloRenuevanUnaVez() throws Exception {
        repo.conexion=new ConexionAutorizada(tokens(ahora,"refresh"),"AUTORIZADA","config",ahora,DiagnosticoConexion.OK);
        when(proveedor.renovar("refresh")).thenReturn(tokens(ahora.plusSeconds(3600),"rotado"));
        var otro=new AutorizarConexionUseCase(proveedor,repo,acceso,Clock.fixed(ahora,ZoneOffset.UTC));
        try(var executor=Executors.newFixedThreadPool(2)) {
            var a=executor.submit(caso::comprobar);var b=executor.submit(otro::comprobar);
            assertThat(a.get().codigo()).isEqualTo(DiagnosticoConexion.OK);assertThat(b.get().codigo()).isEqualTo(DiagnosticoConexion.OK);
        }
        verify(proveedor,times(1)).renovar("refresh");
    }
    @Test void unaAutorizacionNuevaNoHeredaExitosDeCredencialesAnteriores() {
        var anterior=new ComprobacionConexion("id",ahora.minusSeconds(60),ahora.minusSeconds(30),
            new ResultadoConexion(DiagnosticoConexion.OK),10,"Consulta de identidad");
        assertThat(caso.vigente(anterior)).isFalse();
        repo.conexion=new ConexionAutorizada(tokens(ahora.plusSeconds(3600),"refresh"),"AUTORIZADA","config",ahora,DiagnosticoConexion.OK);
        assertThat(caso.vigente(anterior)).isFalse();
        assertThat(caso.vigente(new ComprobacionConexion("nuevo",ahora,ahora.plusSeconds(1),
            new ResultadoConexion(DiagnosticoConexion.OK),10,"Consulta de identidad"))).isTrue();
    }
    static class Memoria implements AutorizacionExternaRepository {
        IntentoAutorizacion intento;String hash, navegador;ConexionAutorizada conexion;
        public void guardarIntento(String h,String n,IntentoAutorizacion i){ hash=h;navegador=n;intento=i; }
        public synchronized Optional<IntentoAutorizacion> consumirIntento(String h,String n) {
            if (!Objects.equals(hash,h) || !Objects.equals(navegador,n)) return Optional.empty();
            var i=intento;intento=null;return Optional.ofNullable(i);
        }
        public void resultadoIntento(String h,DiagnosticoConexion d) {}
        public Optional<DiagnosticoConexion> ultimoResultado(UUID u){return Optional.empty();}
        public Optional<ConexionAutorizada> consultar(){return Optional.ofNullable(conexion);}
        public void guardar(ConexionAutorizada c){conexion=c;}
        public synchronized <T> T exclusivo(Supplier<T> s){return s.get();}
    }
}
