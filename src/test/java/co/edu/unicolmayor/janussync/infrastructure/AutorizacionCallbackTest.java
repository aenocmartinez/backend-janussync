package co.edu.unicolmayor.janussync.infrastructure;
import co.edu.unicolmayor.janussync.application.usecase.AutorizarConexionUseCase;
import co.edu.unicolmayor.janussync.infrastructure.adapter.web.AutorizacionBrightspaceController;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.UUID;
import jakarta.servlet.http.Cookie;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class AutorizacionCallbackTest {
    @Test void cookiePropiaLaxSecureSinRelajarCookiesJwtYRespuestaSoloUrl() {
        var caso=mock(AutorizarConexionUseCase.class);
        var usuario=UUID.randomUUID();var sesion=UUID.randomUUID();
        when(caso.iniciar(usuario,sesion)).thenReturn(new AutorizarConexionUseCase.Inicio("https://auth.brightspace.com/oauth2/auth?state=estado","n".repeat(43)));
        var jwt=Jwt.withTokenValue("prueba").header("alg","RS256").subject(usuario.toString()).claim("sid",sesion.toString()).build();
        var r=new AutorizacionBrightspaceController(caso).iniciar(new JwtAuthenticationToken(jwt));
        assertThat(r.getHeaders().getFirst("Set-Cookie")).contains("__Secure-JANUS-OAUTH=","Secure","HttpOnly","SameSite=Lax","Path=/callback","Max-Age=300").doesNotContain("Domain=");
        assertThat(r.getBody()).isInstanceOf(AutorizacionBrightspaceController.InicioDTO.class);
    }
    @Test void callbackRecibeCookiePropiaYNoNecesitaSesionJwtNiReflejaCodigo() {
        var caso=mock(AutorizarConexionUseCase.class);var req=new MockHttpServletRequest("GET","/callback");
        req.setParameter("state","s".repeat(43));req.setParameter("code","codigo-secreto");
        req.setCookies(new Cookie("__Secure-JANUS-OAUTH","n".repeat(43)));
        var r=new AutorizacionBrightspaceController(caso).callback(req);
        verify(caso).completar("s".repeat(43),"n".repeat(43),"codigo-secreto",null);
        assertThat(r.getStatusCode().value()).isEqualTo(303);
        assertThat(r.getHeaders().getFirst("Location")).isEqualTo("/inicio?plataforma=brightspace");
        assertThat(r.getBody()).isNull();assertThat(r.getHeaders().toString()).doesNotContain("codigo-secreto");
        assertThat(r.getHeaders().getFirst("Set-Cookie")).contains("Max-Age=0","Secure","HttpOnly","SameSite=Lax");
    }
}
