package co.edu.unicolmayor.janussync.infrastructure.adapter.integraciones;
import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.domain.port.AutorizacionExternaPort;
import co.edu.unicolmayor.janussync.domain.exception.AutorizacionExternaException;
import co.edu.unicolmayor.janussync.application.usecase.AutorizarConexionUseCase;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;

/** OAuth Authorization Code confidencial; no reutiliza firmas Valence. */
public final class BrightspaceOAuthAdapter implements AutorizacionExternaPort {
    private static final URI AUTH=URI.create("https://auth.brightspace.com/oauth2/auth");
    private static final URI TOKEN=URI.create("https://auth.brightspace.com/core/connect/token");
    private final String base, version, id, secreto, redirect, scopes;
    private final boolean claveDisponible;
    private final Duration timeout;
    private final Clock reloj;
    private final HttpClient cliente;
    public BrightspaceOAuthAdapter(String base,String version,String id,String secreto,String redirect,String scopes,
            boolean claveDisponible,Duration conexion,Duration timeout,Clock reloj) {
        this(base,version,id,secreto,redirect,scopes,claveDisponible,timeout,reloj,
            HttpClient.newBuilder().connectTimeout(conexion).followRedirects(HttpClient.Redirect.NEVER).build());
    }
    public BrightspaceOAuthAdapter(String base,String version,String id,String secreto,String redirect,String scopes,
            boolean claveDisponible,Duration timeout,Clock reloj,HttpClient cliente) {
        this.base=base;this.version=version;this.id=id;this.secreto=secreto;this.redirect=redirect;
        this.scopes=scopes;this.claveDisponible=claveDisponible;this.timeout=timeout;this.reloj=reloj;this.cliente=cliente;
    }
    public boolean configurada() {
        try {
            var b=URI.create(base);var r=URI.create(redirect);
            return claveDisponible && !id.isBlank() && !secreto.isBlank() && !scopes.isBlank()
                && version.matches("[1-9][0-9]*\\.[0-9]+") && https(b) && (b.getPath().isEmpty() || b.getPath().equals("/"))
                && https(r) && r.getPath().equals("/callback") && !r.getHost().isBlank();
        } catch (IllegalArgumentException e) { return false; }
    }
    private static boolean https(URI u) { return "https".equals(u.getScheme()) && u.getHost()!=null && u.getUserInfo()==null && u.getQuery()==null && u.getFragment()==null; }
    public String configuracion() { return AutorizarConexionUseCase.hash(base+"|"+version+"|"+id+"|"+secreto+"|"+redirect+"|"+scopes); }
    public String urlAutorizacion(String state) {
        return AUTH+"?response_type=code&client_id="+enc(id)+"&redirect_uri="+enc(redirect)+"&scope="+enc(scopes)+"&state="+enc(state);
    }
    public CredencialesExternas intercambiar(String codigo) {
        return tokens("grant_type=authorization_code&code="+enc(codigo)+"&redirect_uri="+enc(redirect),false);
    }
    public CredencialesExternas renovar(String refresh) { return tokens("grant_type=refresh_token&refresh_token="+enc(refresh),true); }
    private CredencialesExternas tokens(String parametros,boolean renovacion) {
        try {
            var req=HttpRequest.newBuilder(TOKEN).timeout(timeout).header("Content-Type","application/x-www-form-urlencoded")
                .header("Authorization","Basic "+Base64.getEncoder().encodeToString((enc(id)+":"+enc(secreto)).getBytes(StandardCharsets.UTF_8)))
                .header("Accept","application/json").POST(HttpRequest.BodyPublishers.ofString(parametros)).build();
            var res=cliente.send(req,info->new CuerpoOAuthLimitado());
            if (res.statusCode()!=200) {
                String error="";
                try { error=JsonMapper.builder().build().readTree(res.body()).path("error").asString(); } catch (RuntimeException ignored) {}
                if (error.equals("invalid_grant")) throw fallo(renovacion?DiagnosticoConexion.AUTORIZACION_REVOCADA:DiagnosticoConexion.AUTENTICACION_RECHAZADA);
                if (error.equals("invalid_scope") || res.statusCode()==403) throw fallo(DiagnosticoConexion.PERMISOS_INSUFICIENTES);
                if (error.equals("invalid_client") || res.statusCode()==401) throw fallo(DiagnosticoConexion.CONFIGURACION_INVALIDA);
                throw fallo(DiagnosticoConexion.RESPUESTA_INESPERADA);
            }
            if (!res.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT).startsWith("application/json"))
                throw fallo(DiagnosticoConexion.RESPUESTA_INESPERADA);
            var json=JsonMapper.builder().build().readTree(res.body());
            String access=json.path("access_token").asString("");
            String refresh=json.path("refresh_token").asString("");
            long seconds=json.path("expires_in").asLong(0);
            if (!json.path("token_type").asString("").equalsIgnoreCase("Bearer") || access.isBlank()
                    || refresh.length()>32768 || json.path("scope").asString(scopes).length()>2048 || access.length()>32768 || access.contains("\r") || access.contains("\n") || seconds<=0 || seconds>604800)
                throw fallo(DiagnosticoConexion.RESPUESTA_INESPERADA);
            return new CredencialesExternas(access,refresh.isBlank()?null:refresh,reloj.instant().plusSeconds(seconds),json.path("scope").asString(scopes));
        } catch (AutorizacionExternaException e) { throw e; }
        catch (HttpTimeoutException e) { throw fallo(DiagnosticoConexion.TIEMPO_AGOTADO); }
        catch (java.io.IOException e) { throw fallo(DiagnosticoConexion.SIN_COMUNICACION); }
        catch (InterruptedException e) { Thread.currentThread().interrupt();throw fallo(DiagnosticoConexion.ERROR_COMPROBACION); }
        catch (RuntimeException e) { throw fallo(DiagnosticoConexion.RESPUESTA_INESPERADA); }
    }
    public ResultadoConexion verificar(String token) {
        try {
            var req=HttpRequest.newBuilder(URI.create(base.replaceAll("/+$","")+"/d2l/api/lp/"+version+"/users/whoami"))
                .timeout(timeout).header("Authorization","Bearer "+token).header("Accept","application/json").GET().build();
            var res=cliente.send(req,info->new CuerpoOAuthLimitado());
            if (res.statusCode()==401) return resultado(DiagnosticoConexion.AUTENTICACION_RECHAZADA);
            if (res.statusCode()==403) return resultado(DiagnosticoConexion.PERMISOS_INSUFICIENTES);
            if (res.statusCode()==404 || res.statusCode()==410) return resultado(DiagnosticoConexion.RECURSO_NO_DISPONIBLE);
            if (res.statusCode()!=200 || !res.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT).startsWith("application/json"))
                return resultado(DiagnosticoConexion.RESPUESTA_INESPERADA);
            var json=JsonMapper.builder().build().readTree(res.body());
            return resultado(json.isObject() && json.path("Identifier").isString()
                && json.path("Identifier").asString().matches("[1-9][0-9]*") && json.path("UniqueName").isString()
                && json.path("FirstName").isString() && json.path("LastName").isString()
                ?DiagnosticoConexion.OK:DiagnosticoConexion.RESPUESTA_INESPERADA);
        } catch (HttpTimeoutException e) { return resultado(DiagnosticoConexion.TIEMPO_AGOTADO); }
        catch (java.io.IOException e) { return resultado(DiagnosticoConexion.SIN_COMUNICACION); }
        catch (InterruptedException e) { Thread.currentThread().interrupt();return resultado(DiagnosticoConexion.ERROR_COMPROBACION); }
        catch (RuntimeException e) { return resultado(DiagnosticoConexion.RESPUESTA_INESPERADA); }
    }
    private static String enc(String v) { return URLEncoder.encode(v,StandardCharsets.UTF_8); }
    private static ResultadoConexion resultado(DiagnosticoConexion c) { return new ResultadoConexion(c); }
    private static AutorizacionExternaException fallo(DiagnosticoConexion c) { return new AutorizacionExternaException(c); }
}
