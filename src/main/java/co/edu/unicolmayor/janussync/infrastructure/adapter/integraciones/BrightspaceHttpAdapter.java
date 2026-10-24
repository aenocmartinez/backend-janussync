package co.edu.unicolmayor.janussync.infrastructure.adapter.integraciones;

import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.domain.port.ComprobarConexionPort;
import co.edu.unicolmayor.janussync.domain.valueobject.TiemposConexion;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import tools.jackson.databind.json.JsonMapper;

/** Valence ID-Key heredado del Laravel. No registra URI firmada ni cuerpo de respuesta. */
public final class BrightspaceHttpAdapter implements ComprobarConexionPort {
    private final String base, version, appId, appKey, userId, userKey;
    private final Clock reloj;
    private final TiemposConexion tiempos;
    private final HttpClient cliente;
    public BrightspaceHttpAdapter(String base,String version,String appId,String appKey,String userId,String userKey,
                                  TiemposConexion tiempos,Clock reloj) {
        this(base,version,appId,appKey,userId,userKey,tiempos,reloj,HttpClient.newBuilder()
            .connectTimeout(tiempos.conexion()).followRedirects(HttpClient.Redirect.NEVER).build());
    }
    // Cliente inyectable para probar fallos de transporte sin cambiar redes ni secretos reales.
    BrightspaceHttpAdapter(String base,String version,String appId,String appKey,String userId,String userKey,
                            TiemposConexion tiempos,Clock reloj,HttpClient cliente) {
        this.base=base; this.version=version; this.appId=appId; this.appKey=appKey; this.userId=userId;
        this.userKey=userKey; this.tiempos=tiempos; this.reloj=reloj; this.cliente=cliente;
    }
    public Plataforma plataforma() { return Plataforma.BRIGHTSPACE; }
    public boolean configurada() {
        if (base.isBlank() || version.isBlank() || appId.isBlank() || appKey.isBlank() || userId.isBlank() || userKey.isBlank()) return false;
        if (!java.util.stream.Stream.of(appId,appKey,userId,userKey).allMatch(v -> v.matches("[A-Za-z0-9_-]{22}"))) return false;
        try {
            var u=URI.create(base);
            return "https".equals(u.getScheme()) && u.getHost()!=null && u.getUserInfo()==null &&
                u.getQuery()==null && u.getFragment()==null && (u.getPath().isEmpty() || u.getPath().equals("/")) && version.matches("[1-9][0-9]*\\.[0-9]+");
        } catch (IllegalArgumentException e) { return false; }
    }
    public ResultadoConexion comprobar() {
        if (!configurada()) return resultado(base.isBlank() || appId.isBlank() || appKey.isBlank() || userId.isBlank() || userKey.isBlank() || version.isBlank()
            ? DiagnosticoConexion.SIN_CONFIGURAR : DiagnosticoConexion.CONFIGURACION_INVALIDA);
        String path="/d2l/api/lp/" + version + "/users/whoami";
        try {
            var consulta=HttpRequest.newBuilder(firmar(path)).timeout(tiempos.ejecucion()).header("Accept","application/json").GET().build();
            // Cuerpo acotado: solo se valida el contrato; nunca se conserva información del usuario externo.
            var respuesta=cliente.send(consulta,info -> new CuerpoLimitado());
            {
                int estado=respuesta.statusCode();
                if (estado==401) return resultado(DiagnosticoConexion.AUTENTICACION_RECHAZADA);
                if (estado==403) return resultado(clasificarProhibido(respuesta.body()));
                if (estado==404 || estado==410) return resultado(DiagnosticoConexion.RECURSO_NO_DISPONIBLE);
                if (estado!=200) return resultado(DiagnosticoConexion.RESPUESTA_INESPERADA);
                if (!respuesta.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT).startsWith("application/json"))
                    return resultado(DiagnosticoConexion.RESPUESTA_INESPERADA);
                var bytes=respuesta.body();
                if (bytes.length>16384) return resultado(DiagnosticoConexion.RESPUESTA_INESPERADA);
                try {
                    var json=JsonMapper.builder().build().readTree(bytes);
                    boolean valido=json.isObject() && json.path("Identifier").isString() && json.path("Identifier").asString().matches("[1-9][0-9]*") &&
                        json.path("FirstName").isString() && json.path("LastName").isString() && json.path("UniqueName").isString() && json.path("ProfileIdentifier").isString();
                    return resultado(valido ? DiagnosticoConexion.OK : DiagnosticoConexion.RESPUESTA_INESPERADA);
                } catch (RuntimeException e) { return resultado(DiagnosticoConexion.RESPUESTA_INESPERADA); }
            }
        } catch (HttpTimeoutException e) { return resultado(DiagnosticoConexion.TIEMPO_AGOTADO); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return resultado(DiagnosticoConexion.ERROR_COMPROBACION); }
        catch (java.io.IOException e) { return resultado(DiagnosticoConexion.SIN_COMUNICACION); }
        catch (IllegalArgumentException e) { return resultado(DiagnosticoConexion.CONFIGURACION_INVALIDA); }
    }
    /** Brightspace usa 403 tanto para ID-Key inválido como para falta de permisos.
     * Solo se inspeccionan marcadores documentados; el texto externo nunca se devuelve ni registra. */
    static DiagnosticoConexion clasificarProhibido(byte[] cuerpo) {
        String mensaje=new String(cuerpo,StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        if (mensaje.contains("timestamp out of range")) return DiagnosticoConexion.RELOJ_DESAJUSTADO;
        if (mensaje.contains("invalid token")) return DiagnosticoConexion.AUTENTICACION_RECHAZADA;
        if (mensaje.contains("not authorized") || mensaje.contains("insufficient permission") || mensaje.contains("insufficient scope"))
            return DiagnosticoConexion.PERMISOS_INSUFICIENTES;
        return DiagnosticoConexion.ACCESO_DENEGADO;
    }
    private static final class CuerpoLimitado implements HttpResponse.BodySubscriber<byte[]> {
        private final java.util.concurrent.CompletableFuture<byte[]> resultado = new java.util.concurrent.CompletableFuture<>();
        private final java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        private java.util.concurrent.Flow.Subscription suscripcion;
        public java.util.concurrent.CompletionStage<byte[]> getBody() { return resultado; }
        public void onSubscribe(java.util.concurrent.Flow.Subscription s) { suscripcion=s; s.request(1); }
        public void onNext(List<java.nio.ByteBuffer> partes) {
            for (var parte:partes) {
                int cantidad=Math.min(parte.remaining(),16385-buffer.size());
                var bytes=new byte[cantidad];parte.get(bytes);buffer.writeBytes(bytes);
                if (buffer.size()>16384) { suscripcion.cancel();resultado.complete(buffer.toByteArray());return; }
            }
            suscripcion.request(1);
        }
        public void onError(Throwable error) { resultado.completeExceptionally(error); }
        public void onComplete() { resultado.complete(buffer.toByteArray()); }
    }
    URI firmar(String path) {
        String instante=Long.toString(reloj.instant().getEpochSecond());
        String cadena="GET&" + path.toLowerCase(Locale.ROOT) + "&" + instante;
        return URI.create(base.replaceAll("/+$","") + path + "?x_a="+codificar(appId)+"&x_b="+codificar(userId)+
            "&x_c="+firma(appKey,cadena)+"&x_d="+firma(userKey,cadena)+"&x_t="+instante);
    }
    static String firma(String clave,String cadena) {
        try {
            var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(clave.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(cadena.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) { throw new IllegalStateException("No está disponible el algoritmo de firma."); }
    }
    private static String codificar(String v) { return URLEncoder.encode(v,StandardCharsets.UTF_8); }
    private static ResultadoConexion resultado(DiagnosticoConexion c) { return new ResultadoConexion(c); }
}
