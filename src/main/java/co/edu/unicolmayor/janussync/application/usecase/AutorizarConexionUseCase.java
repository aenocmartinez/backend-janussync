package co.edu.unicolmayor.janussync.application.usecase;
import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.domain.port.*;
import co.edu.unicolmayor.janussync.domain.exception.AutorizacionExternaException;
import java.time.*;
import java.util.*;
import java.security.*;
import java.nio.charset.StandardCharsets;

/** Una conexión institucional; nunca autentica personas en JanusSync. */
public final class AutorizarConexionUseCase implements ComprobarConexionPort {
    public static final String PERMISO="INTEGRACIONES_COMPROBAR";
    private final AutorizacionExternaPort proveedor;
    private final AutorizacionExternaRepository repositorio;
    private final AccesoRepository acceso;
    private final Clock reloj;
    private final SecureRandom aleatorio=new SecureRandom();
    public AutorizarConexionUseCase(AutorizacionExternaPort proveedor, AutorizacionExternaRepository repositorio,
            AccesoRepository acceso, Clock reloj) {
        this.proveedor=proveedor; this.repositorio=repositorio; this.acceso=acceso; this.reloj=reloj;
    }
    public record Inicio(String url, String navegador) {
        @Override public String toString() { return "Inicio[protegido]"; }
    }
    public record Estado(String estado, DiagnosticoConexion diagnostico) {}
    public Plataforma plataforma() { return Plataforma.BRIGHTSPACE; }
    public boolean configurada() { return proveedor.configurada(); }
    private boolean permitido(UUID usuario, UUID sesion) {
        return acceso.permisosActuales(sesion,usuario,reloj.instant()).map(p->p.contains(PERMISO)).orElse(false);
    }
    public Inicio iniciar(UUID usuario, UUID sesion) {
        if (!permitido(usuario,sesion)) throw fallo(DiagnosticoConexion.PERMISOS_INSUFICIENTES);
        if (!configurada()) throw fallo(DiagnosticoConexion.SIN_CONFIGURAR);
        String state=secreto(), navegador=secreto();
        repositorio.guardarIntento(hash(state),hash(navegador),new IntentoAutorizacion(usuario,sesion,
            reloj.instant().plusSeconds(300),proveedor.configuracion()));
        return new Inicio(proveedor.urlAutorizacion(state),navegador);
    }
    public void completar(String state, String navegador, String codigo, String error) {
        if (!valido(state) || !valido(navegador)) throw fallo(DiagnosticoConexion.AUTORIZACION_PENDIENTE);
        // Consumir también los rechazos del proveedor. No depender de cookies JWT Strict.
        var intento=repositorio.consumirIntento(hash(state),hash(navegador))
            .orElseThrow(()->fallo(DiagnosticoConexion.AUTORIZACION_PENDIENTE));
        try {
        if (!intento.venceEn().isAfter(reloj.instant()) || !configurada()
                || !intento.configuracion().equals(proveedor.configuracion()))
            throw fallo(DiagnosticoConexion.AUTORIZACION_PENDIENTE);
        if (!permitido(intento.usuario(),intento.sesion())) throw fallo(DiagnosticoConexion.PERMISOS_INSUFICIENTES);
        if (error!=null || codigo==null || codigo.isBlank() || codigo.length()>8192)
            throw fallo(DiagnosticoConexion.AUTENTICACION_RECHAZADA);
        repositorio.exclusivo(()-> {
            var tokens=proveedor.intercambiar(codigo);
            var resultado=proveedor.verificar(tokens.acceso());
            if (resultado.codigo()!=DiagnosticoConexion.OK) throw fallo(resultado.codigo());
            if (!permitido(intento.usuario(),intento.sesion())) throw fallo(DiagnosticoConexion.PERMISOS_INSUFICIENTES);
            repositorio.guardar(new ConexionAutorizada(tokens,"AUTORIZADA",proveedor.configuracion(),
                reloj.instant(),DiagnosticoConexion.OK));
            return null;
        });
            repositorio.resultadoIntento(hash(state),DiagnosticoConexion.OK);
        } catch (AutorizacionExternaException e) {
            repositorio.resultadoIntento(hash(state),e.codigo());throw e;
        } catch (RuntimeException e) {
            repositorio.resultadoIntento(hash(state),DiagnosticoConexion.ERROR_COMPROBACION);
            throw fallo(DiagnosticoConexion.ERROR_COMPROBACION);
        }
    }
    public DiagnosticoConexion ultimoResultado(UUID usuario) { return repositorio.ultimoResultado(usuario).orElse(null); }

    public Estado estado() {
        if (!configurada()) return new Estado("SIN_CONFIGURAR",DiagnosticoConexion.SIN_CONFIGURAR);
        var c=repositorio.consultar().orElse(null);
        if (c==null || !c.configuracion().equals(proveedor.configuracion()))
            return new Estado("PENDIENTE",DiagnosticoConexion.AUTORIZACION_PENDIENTE);
        if (c.estado().equals("RENOVANDO")) return new Estado("ERROR",DiagnosticoConexion.ERROR_COMPROBACION);
        if (c.estado().equals("AUTORIZADA") && !c.credenciales().venceEn().isAfter(reloj.instant())
                && (c.credenciales().renovacion()==null || c.credenciales().renovacion().isBlank()))
            return new Estado("REVOCADA",DiagnosticoConexion.AUTORIZACION_REVOCADA);
        return new Estado(c.estado(),c.diagnostico());
    }
    public boolean vigente(ComprobacionConexion comprobacion) {
        if (comprobacion.resultado().codigo()!=DiagnosticoConexion.OK) return true;
        if (!configurada()) return false;
        var c=repositorio.consultar().orElse(null);
        return c!=null && c.estado().equals("AUTORIZADA") && c.configuracion().equals(proveedor.configuracion())
            && !comprobacion.fin().isBefore(c.autorizadaEn());
    }
    public ResultadoConexion comprobar() {
        if (!configurada()) return new ResultadoConexion(DiagnosticoConexion.SIN_CONFIGURAR);
        try { return repositorio.exclusivo(this::comprobarExclusivo); }
        catch (AutorizacionExternaException e) { return new ResultadoConexion(e.codigo()); }
    }
    private ResultadoConexion comprobarExclusivo() {
        var c=repositorio.consultar().orElse(null);
        if (c==null || !c.configuracion().equals(proveedor.configuracion()))
            return new ResultadoConexion(DiagnosticoConexion.AUTORIZACION_PENDIENTE);
        // Marca durable ANTES de enviar un refresh de un solo uso. Tras caída o
        // respuesta ambigua, exigir nueva autorización; jamás reenviar el mismo refresh.
        if (!c.estado().equals("AUTORIZADA")) return new ResultadoConexion(
            c.estado().equals("RENOVANDO")?DiagnosticoConexion.AUTORIZACION_REVOCADA:c.diagnostico());
        var tokens=c.credenciales();
        if (!tokens.venceEn().isAfter(reloj.instant().plusSeconds(30))) {
            if (tokens.renovacion()==null || tokens.renovacion().isBlank()) {
                invalidar(c,"REVOCADA",DiagnosticoConexion.AUTORIZACION_REVOCADA);
                return new ResultadoConexion(DiagnosticoConexion.AUTORIZACION_REVOCADA);
            }
            repositorio.guardar(new ConexionAutorizada(tokens,"RENOVANDO",c.configuracion(),c.autorizadaEn(),c.diagnostico()));
            try {
                var nuevos=proveedor.renovar(tokens.renovacion());
                tokens=new CredencialesExternas(nuevos.acceso(),nuevos.renovacion()==null?tokens.renovacion():nuevos.renovacion(),
                    nuevos.venceEn(),nuevos.scopes());
                c=new ConexionAutorizada(tokens,"AUTORIZADA",c.configuracion(),c.autorizadaEn(),DiagnosticoConexion.OK);
                repositorio.guardar(c);
            } catch (AutorizacionExternaException e) {
                invalidar(c,e.codigo()==DiagnosticoConexion.AUTORIZACION_REVOCADA?"REVOCADA":"ERROR",e.codigo());
                return new ResultadoConexion(e.codigo());
            }
        }
        var resultado=proveedor.verificar(tokens.acceso());
        if (resultado.codigo()==DiagnosticoConexion.AUTENTICACION_RECHAZADA) {
            invalidar(c,"REVOCADA",DiagnosticoConexion.AUTORIZACION_REVOCADA);
            return new ResultadoConexion(DiagnosticoConexion.AUTORIZACION_REVOCADA);
        }
        return resultado;
    }
    private void invalidar(ConexionAutorizada c,String estado,DiagnosticoConexion codigo) {
        repositorio.guardar(new ConexionAutorizada(null,estado,c.configuracion(),c.autorizadaEn(),codigo));
    }
    private String secreto() { byte[] b=new byte[32]; aleatorio.nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    private static boolean valido(String s) { return s!=null && s.matches("[A-Za-z0-9_-]{43}"); }
    public static String hash(String valor) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8))); }
        catch (GeneralSecurityException e) { throw new IllegalStateException("SHA-256 no disponible"); }
    }
    private static AutorizacionExternaException fallo(DiagnosticoConexion c) { return new AutorizacionExternaException(c); }
}
