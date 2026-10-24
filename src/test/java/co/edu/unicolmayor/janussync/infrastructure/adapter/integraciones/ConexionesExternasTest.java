package co.edu.unicolmayor.janussync.infrastructure.adapter.integraciones;

import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.domain.port.ComprobarConexionPort;
import co.edu.unicolmayor.janussync.domain.valueobject.TiemposConexion;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConexionesExternasTest {
    static final Clock RELOJ=Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"),ZoneOffset.UTC);
    static final TiemposConexion TIEMPOS=new TiemposConexion(Duration.ofMillis(100),Duration.ofSeconds(1),Duration.ofSeconds(1),15);
    final HttpClient cliente=mock(HttpClient.class);
    BrightspaceHttpAdapter adaptador(String base,String version) {
        return new BrightspaceHttpAdapter(base,version,"aaaaaaaaaaaaaaaaaaaaaa","bbbbbbbbbbbbbbbbbbbbbb","cccccccccccccccccccccc","dddddddddddddddddddddd",TIEMPOS,RELOJ,cliente);
    }
    @SuppressWarnings("unchecked")
    HttpResponse<byte[]> respuesta(int codigo,String tipo,String cuerpo) throws Exception {
        HttpResponse<byte[]> respuesta=mock(HttpResponse.class);
        when(respuesta.statusCode()).thenReturn(codigo);
        when(respuesta.headers()).thenReturn(HttpHeaders.of(Map.of("Content-Type",List.of(tipo)),(a,b)->true));
        when(respuesta.body()).thenReturn(cuerpo.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(cliente.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(respuesta);
        return respuesta;
    }
    @Test void configuracionAusenteNoHaceLlamadas() {
        var b=adaptador("","");assertThat(b.configurada()).isFalse();
        assertThat(b.comprobar().codigo()).isEqualTo(DiagnosticoConexion.SIN_CONFIGURAR);verifyNoInteractions(cliente);
        assertThat(new AcademusoftOracleAdapter("ORACLE","","","",TIEMPOS).comprobar().estado()).isEqualTo("SIN_CONFIGURAR");
    }
    @Test void clavesConFormatoInvalidoNoSeEnvianAlProveedor() {
        var b=new BrightspaceHttpAdapter("https://externo.invalid","1.49","id-mal-formado","bbbbbbbbbbbbbbbbbbbbbb",
            "cccccccccccccccccccccc","dddddddddddddddddddddd",TIEMPOS,RELOJ,cliente);
        assertThat(b.comprobar().codigo()).isEqualTo(DiagnosticoConexion.CONFIGURACION_INVALIDA);verifyNoInteractions(cliente);
    }
    @Test void exigeHttpsSinCredencialesNiParametrosEnBase() {
        for(String url:List.of("http://externo.invalid","https://user:pass@externo.invalid","https://externo.invalid/?token=secreto","https://externo.invalid/login")) {
            var b=adaptador(url,"1.49");assertThat(b.configurada()).isFalse();
            assertThat(b.comprobar().codigo()).isEqualTo(DiagnosticoConexion.CONFIGURACION_INVALIDA);
        }
        verifyNoInteractions(cliente);
    }
    @Test void validaIdentidadYContratoNoSolo200() throws Exception {
        respuesta(200,"application/json","{\"Identifier\":\"123\",\"FirstName\":\"Nombre\",\"LastName\":\"Apellido\",\"UniqueName\":\"externo\",\"ProfileIdentifier\":\"perfil\"}");
        assertThat(adaptador("https://externo.invalid","1.49").comprobar().codigo()).isEqualTo(DiagnosticoConexion.OK);
        respuesta(200,"text/html","<html>Login</html>");
        assertThat(adaptador("https://externo.invalid","1.49").comprobar().codigo()).isEqualTo(DiagnosticoConexion.RESPUESTA_INESPERADA);
        respuesta(200,"application/json","{\"Identifier\":\"123\"}");
        assertThat(adaptador("https://externo.invalid","1.49").comprobar().codigo()).isEqualTo(DiagnosticoConexion.RESPUESTA_INESPERADA);
        respuesta(200,"application/json","{incorrecto");
        assertThat(adaptador("https://externo.invalid","1.49").comprobar().codigo()).isEqualTo(DiagnosticoConexion.RESPUESTA_INESPERADA);
    }
    @Test void clasificaRechazosRecursosYRespuestaExcesiva() throws Exception {
        for(int codigo:List.of(401,403)) {
            respuesta(codigo,"text/html","<html>Invalid Token</html>");
            assertThat(adaptador("https://externo.invalid","1.49").comprobar().codigo()).isEqualTo(DiagnosticoConexion.AUTENTICACION_RECHAZADA);
        }
        respuesta(404,"application/json","{}");
        assertThat(adaptador("https://externo.invalid","1.49").comprobar().codigo()).isEqualTo(DiagnosticoConexion.RECURSO_NO_DISPONIBLE);
        respuesta(302,"text/html","");
        assertThat(adaptador("https://externo.invalid","1.49").comprobar().codigo()).isEqualTo(DiagnosticoConexion.RESPUESTA_INESPERADA);
        respuesta(200,"application/json"," ".repeat(16385));
        assertThat(adaptador("https://externo.invalid","1.49").comprobar().codigo()).isEqualTo(DiagnosticoConexion.RESPUESTA_INESPERADA);
    }
    @Test void distinguePermisosRelojYTokensSinExponerRespuesta() throws Exception {
        var b=adaptador("https://externo.invalid","1.49");
        respuesta(403,"text/html","<html>Timestamp out of range: 1792756800</html>");
        assertThat(b.comprobar().codigo()).isEqualTo(DiagnosticoConexion.RELOJ_DESAJUSTADO);
        respuesta(403,"application/json","{\"Message\":\"Insufficient permissions\"}");
        assertThat(b.comprobar().codigo()).isEqualTo(DiagnosticoConexion.PERMISOS_INSUFICIENTES);
        respuesta(403,"text/html","<html>Forbidden</html>");
        assertThat(b.comprobar().codigo()).isEqualTo(DiagnosticoConexion.ACCESO_DENEGADO);
        respuesta(403,"text/html","<html>Invalid Token: secreto-no-exponer</html>");
        assertThat(b.comprobar().codigo()).isEqualTo(DiagnosticoConexion.AUTENTICACION_RECHAZADA);
        assertThat(b.comprobar().toString()).doesNotContain("secreto-no-exponer");
    }
    @Test void tiemposYRedNoExponenExcepciones() throws Exception {
        when(cliente.send(any(),any(HttpResponse.BodyHandler.class))).thenThrow(new HttpTimeoutException("url-secreta"));
        var b=adaptador("https://externo.invalid","1.49");
        assertThat(b.comprobar().codigo()).isEqualTo(DiagnosticoConexion.TIEMPO_AGOTADO);
        when(cliente.send(any(),any(HttpResponse.BodyHandler.class))).thenThrow(new IOException("credenciales-secretas"));
        assertThat(b.comprobar().codigo()).isEqualTo(DiagnosticoConexion.SIN_COMUNICACION);
        assertThat(b.comprobar().toString()).doesNotContain("credenciales");
    }
    @Test void firmaValenceConMetodoRutaTimestampYBase64UrlSinPadding() {
        var uri=adaptador("https://externo.invalid","1.49").firmar("/d2l/api/lp/1.49/users/whoami");
        assertThat(uri.getQuery()).contains("x_t=1792756800");
        assertThat(uri.getQuery()).contains("x_c="+BrightspaceHttpAdapter.firma("bbbbbbbbbbbbbbbbbbbbbb","GET&/d2l/api/lp/1.49/users/whoami&1792756800"));
        assertThat(BrightspaceHttpAdapter.firma("key","The quick brown fox jumps over the lazy dog"))
            .isEqualTo("97yD9DBThCSxMpjmqm-xQ-9NWaFJRhdZl0edvC0aPNg");
    }
    @Test void firmaCoincideConSdkPhpExactoDelLaravelAnterior() throws Exception {
        try (var recurso=getClass().getResourceAsStream("/valence-1.6.1-vector.json")) {
            var v=tools.jackson.databind.json.JsonMapper.builder().build().readTree(recurso);
            String path=v.path("path").asString();String base="GET&"+path+"&"+v.path("timestamp").asLong();
            assertThat(BrightspaceHttpAdapter.firma(v.path("appKey").asString(),base)).isEqualTo(v.path("appSignature").asString());
            assertThat(BrightspaceHttpAdapter.firma(v.path("userKey").asString(),base)).isEqualTo(v.path("userSignature").asString());
            var query=adaptador("https://externo.invalid","1.49").firmar(path).getQuery();
            assertThat(query).contains("x_c="+v.path("appSignature").asString(),"x_d="+v.path("userSignature").asString());
        }
    }
    @Test void clasificaOracleSinMostrarMensajes() {
        assertThat(AcademusoftOracleAdapter.diagnostico(new SQLException("secreto","",1017))).isEqualTo(DiagnosticoConexion.AUTENTICACION_RECHAZADA);
        assertThat(AcademusoftOracleAdapter.diagnostico(new SQLTimeoutException("secreto"))).isEqualTo(DiagnosticoConexion.TIEMPO_AGOTADO);
        assertThat(AcademusoftOracleAdapter.diagnostico(new SQLException("secreto","",942))).isEqualTo(DiagnosticoConexion.RECURSO_NO_DISPONIBLE);
        assertThat(AcademusoftOracleAdapter.diagnostico(new SQLException("secreto","",12541))).isEqualTo(DiagnosticoConexion.SIN_COMUNICACION);
    }
    @Test void limitaTiempoTotalSinAcumularTareas() {
        var lento=new ComprobarConexionPort() {
            public Plataforma plataforma(){return Plataforma.ACADEMUSOFT;}
            public boolean configurada(){return true;}
            public ResultadoConexion comprobar(){try{Thread.sleep(10000);}catch(InterruptedException e){Thread.currentThread().interrupt();}return new ResultadoConexion(DiagnosticoConexion.OK);}
        };
        try(var limitado=new ConexionConTiempoLimite(lento,Duration.ofMillis(100))) {
            long antes=System.nanoTime();
            assertThat(limitado.comprobar().codigo()).isEqualTo(DiagnosticoConexion.TIEMPO_AGOTADO);
            assertThat(Duration.ofNanos(System.nanoTime()-antes)).isLessThan(Duration.ofSeconds(2));
        }
    }
    @Test void oracleConsultaMinimaAutenticadaSinDatasourcePrincipal() throws Exception {
        var conexion=mock(Connection.class);var consulta=mock(PreparedStatement.class);var filas=mock(ResultSet.class);
        when(conexion.prepareStatement("SELECT 1 FROM DUAL")).thenReturn(consulta);
        when(consulta.executeQuery()).thenReturn(filas);when(filas.next()).thenReturn(true);when(filas.getInt(1)).thenReturn(1);
        try(var fuentes=mockConstruction(oracle.jdbc.datasource.impl.OracleDataSource.class,(fuente,contexto)->when(fuente.getConnection()).thenReturn(conexion))) {
            var oracle=new AcademusoftOracleAdapter("ORACLE","jdbc:oracle:thin:@//pruebas.invalid:1521/servicio","prueba","solo-prueba",TIEMPOS);
            assertThat(oracle.comprobar().codigo()).isEqualTo(DiagnosticoConexion.OK);
            verify(conexion).setReadOnly(true);verify(consulta).setMaxRows(1);verify(conexion).close();
            verify(fuentes.constructed().getFirst()).setUser("prueba");
        }
    }
    @Test void oracleCredencialesRechazadas() throws Exception {
        try(var fuentes=mockConstruction(oracle.jdbc.datasource.impl.OracleDataSource.class,(fuente,contexto)->when(fuente.getConnection()).thenThrow(new SQLException("secreto","",1017)))) {
            assertThat(new AcademusoftOracleAdapter("ORACLE","jdbc:oracle:thin:@//pruebas.invalid:1521/servicio","prueba","solo-prueba",TIEMPOS).comprobar().codigo())
                .isEqualTo(DiagnosticoConexion.AUTENTICACION_RECHAZADA);
        }
    }
    @Test void tiemposSonValueObjectsValidos() {
        assertThatThrownBy(()->new TiemposConexion(Duration.ofSeconds(5),Duration.ofSeconds(1),Duration.ZERO,0)).isInstanceOf(IllegalArgumentException.class);
    }
}
