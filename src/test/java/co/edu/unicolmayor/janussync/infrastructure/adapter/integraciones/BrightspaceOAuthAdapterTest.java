package co.edu.unicolmayor.janussync.infrastructure.adapter.integraciones;
import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.domain.exception.AutorizacionExternaException;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class BrightspaceOAuthAdapterTest {
    final HttpClient cliente=mock(HttpClient.class);
    final Clock reloj=Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"),ZoneOffset.UTC);
    BrightspaceOAuthAdapter adaptador() { return new BrightspaceOAuthAdapter("https://lms.invalid","1.50","cliente","secreto",
        "https://janussync.test/callback","users:own_profile:read",true,Duration.ofSeconds(1),reloj,cliente); }
    @SuppressWarnings("unchecked") void respuesta(int estado,String cuerpo) throws Exception {
        HttpResponse<byte[]> r=mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(estado);when(r.body()).thenReturn(cuerpo.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(r.headers()).thenReturn(HttpHeaders.of(Map.of("Content-Type",List.of("application/json")),(a,b)->true));
        when(cliente.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(r);
    }
    @Test void configuracionEndpointsYSoloBearerSinFirmas() throws Exception {
        var p=adaptador();assertThat(p.configurada()).isTrue();
        assertThat(p.urlAutorizacion("estado")).startsWith("https://auth.brightspace.com/oauth2/auth?")
            .contains("response_type=code","state=estado","scope=users%3Aown_profile%3Aread").doesNotContain("secreto","code_challenge");
        respuesta(200,"{\"Identifier\":\"1\",\"UniqueName\":\"u\",\"FirstName\":\"N\",\"LastName\":\"A\"}");
        assertThat(p.verificar("access").codigo()).isEqualTo(DiagnosticoConexion.OK);
        var captor=org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(cliente).send(captor.capture(),any(HttpResponse.BodyHandler.class));
        assertThat(captor.getValue().uri().toString()).isEqualTo("https://lms.invalid/d2l/api/lp/1.50/users/whoami");
        assertThat(captor.getValue().headers().firstValue("Authorization")).contains("Bearer access");
    }
    @Test void vencimientoRealYRefreshOpcionalSinExponerTokens() throws Exception {
        respuesta(200,"{\"access_token\":\"access\",\"token_type\":\"Bearer\",\"expires_in\":120}");
        var t=adaptador().renovar("refresh");assertThat(t.venceEn()).isEqualTo(reloj.instant().plusSeconds(120));
        assertThat(t.renovacion()).isNull();assertThat(t.toString()).doesNotContain("access");
        var captor=org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(cliente).send(captor.capture(),any(HttpResponse.BodyHandler.class));
        assertThat(captor.getValue().uri().toString()).isEqualTo("https://auth.brightspace.com/core/connect/token");
        assertThat(captor.getValue().headers().firstValue("Authorization")).contains("Basic "+Base64.getEncoder().encodeToString("cliente:secreto".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    @Test void erroresControladosSinReintento() throws Exception {
        respuesta(400,"{\"error\":\"invalid_grant\",\"error_description\":\"secreto-no-publicable\"}");
        assertThatThrownBy(()->adaptador().renovar("refresh")).isInstanceOfSatisfying(AutorizacionExternaException.class,e->{
            assertThat(e.codigo()).isEqualTo(DiagnosticoConexion.AUTORIZACION_REVOCADA);
            assertThat(e.getMessage()).doesNotContain("secreto-no-publicable","refresh");
        });
        verify(cliente,times(1)).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        respuesta(403,"{}");assertThat(adaptador().verificar("access").codigo()).isEqualTo(DiagnosticoConexion.PERMISOS_INSUFICIENTES);
        respuesta(401,"{}");assertThat(adaptador().verificar("access").codigo()).isEqualTo(DiagnosticoConexion.AUTENTICACION_RECHAZADA);
    }
    @Test void redYTimeoutSonDistintosDeAutorizacion() throws Exception {
        when(cliente.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenThrow(new HttpTimeoutException("oculto"));
        assertThat(adaptador().verificar("access").codigo()).isEqualTo(DiagnosticoConexion.TIEMPO_AGOTADO);
        when(cliente.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenThrow(new java.io.IOException("oculto"));
        assertThat(adaptador().verificar("access").codigo()).isEqualTo(DiagnosticoConexion.SIN_COMUNICACION);
    }
}
