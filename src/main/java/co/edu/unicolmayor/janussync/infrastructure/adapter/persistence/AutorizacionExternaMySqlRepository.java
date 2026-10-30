package co.edu.unicolmayor.janussync.infrastructure.adapter.persistence;
import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.domain.port.AutorizacionExternaRepository;
import co.edu.unicolmayor.janussync.domain.exception.AutorizacionExternaException;
import java.sql.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.jdbc.core.*;
import org.springframework.transaction.support.TransactionTemplate;

public final class AutorizacionExternaMySqlRepository implements AutorizacionExternaRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transacciones;
    private final CifradoCredencialesExternas cifrado;
    public AutorizacionExternaMySqlRepository(JdbcTemplate jdbc,TransactionTemplate tx,CifradoCredencialesExternas cifrado) {
        this.jdbc=jdbc;this.transacciones=tx;this.cifrado=cifrado;
    }
    public void guardarIntento(String state,String navegador,IntentoAutorizacion i) {
        // Limpieza oportunista: no tarea programada ni tokens históricos indefinidos.
        jdbc.update("DELETE FROM oauth_intentos WHERE vence_en < UTC_TIMESTAMP(6) - INTERVAL 1 DAY");
        jdbc.update("INSERT INTO oauth_intentos(state_hash,navegador_hash,usuario_id,sesion_id,vence_en,configuracion) VALUES(?,?,?,?,?,?)",
            state,navegador,i.usuario().toString(),i.sesion().toString(),Timestamp.from(i.venceEn()),i.configuracion());
    }
    public Optional<IntentoAutorizacion> consumirIntento(String state,String navegador) {
        return transacciones.execute(status->{
            var filas=jdbc.query("SELECT usuario_id,sesion_id,vence_en,configuracion FROM oauth_intentos WHERE state_hash=? AND navegador_hash=? AND consumido=FALSE FOR UPDATE",
                (r,n)->new IntentoAutorizacion(UUID.fromString(r.getString(1)),UUID.fromString(r.getString(2)),r.getTimestamp(3).toInstant(),r.getString(4)),state,navegador);
            if (filas.isEmpty()) return Optional.empty();
            jdbc.update("UPDATE oauth_intentos SET consumido=TRUE WHERE state_hash=?",state);
            return Optional.of(filas.getFirst());
        });
    }
    public void resultadoIntento(String state,DiagnosticoConexion codigo) {
        jdbc.update("UPDATE oauth_intentos SET diagnostico=? WHERE state_hash=?",codigo.name(),state);
    }
    public Optional<DiagnosticoConexion> ultimoResultado(UUID usuario) {
        return jdbc.query("SELECT diagnostico FROM oauth_intentos WHERE usuario_id=? ORDER BY vence_en DESC LIMIT 1",
            (r,n)->r.getString(1),usuario.toString()).stream().filter(Objects::nonNull).map(DiagnosticoConexion::valueOf).findFirst();
    }
    public Optional<ConexionAutorizada> consultar() {
        return jdbc.query("SELECT * FROM oauth_conexion WHERE id=1",(r,n)-> {
            var acceso=cifrado.descifrar(r.getBytes("acceso_cifrado"),"acceso");
            var tokens=acceso==null?null:new CredencialesExternas(acceso,cifrado.descifrar(r.getBytes("refresh_cifrado"),"refresh"),
                r.getTimestamp("vence_en").toInstant(),r.getString("scopes"));
            return new ConexionAutorizada(tokens,r.getString("estado"),r.getString("configuracion"),r.getTimestamp("autorizada_en").toInstant(),
                DiagnosticoConexion.valueOf(r.getString("diagnostico")));
        }).stream().findFirst();
    }
    public void guardar(ConexionAutorizada c) {
        var t=c.credenciales();
        jdbc.update("""
            INSERT INTO oauth_conexion(id,acceso_cifrado,refresh_cifrado,vence_en,scopes,estado,configuracion,autorizada_en,diagnostico)
            VALUES(1,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE acceso_cifrado=VALUES(acceso_cifrado),refresh_cifrado=VALUES(refresh_cifrado),
            vence_en=VALUES(vence_en),scopes=VALUES(scopes),estado=VALUES(estado),configuracion=VALUES(configuracion),
            autorizada_en=VALUES(autorizada_en),diagnostico=VALUES(diagnostico)
            """,cifrado.cifrar(t==null?null:t.acceso(),"acceso"),cifrado.cifrar(t==null?null:t.renovacion(),"refresh"),
            t==null?null:Timestamp.from(t.venceEn()),t==null?null:t.scopes(),c.estado(),c.configuracion(),Timestamp.from(c.autorizadaEn()),c.diagnostico().name());
    }
    public <T> T exclusivo(Supplier<T> operacion) {
        // Advisory lock de MySQL: compartido por instancias, conexión retenida hasta
        // RELEASE_LOCK. Las escrituras son autocommit para que RENOVANDO sea durable.
        return jdbc.execute((ConnectionCallback<T>)conexion->{
            String nombre;
            try (var p=conexion.prepareStatement("SELECT CONCAT('janus-oauth:',LEFT(SHA2(DATABASE(),256),32))");var r=p.executeQuery()) { r.next();nombre=r.getString(1); }
            boolean adquirido=false;
            try {
                try (var p=conexion.prepareStatement("SELECT GET_LOCK(?,0)")) {
                    p.setString(1,nombre);try(var r=p.executeQuery()) { adquirido=r.next() && r.getInt(1)==1; }
                }
                if (!adquirido) throw new AutorizacionExternaException(DiagnosticoConexion.ERROR_COMPROBACION);
                return operacion.get();
            } finally {
                if (adquirido) try(var p=conexion.prepareStatement("SELECT RELEASE_LOCK(?)")) { p.setString(1,nombre);p.execute(); }
            }
        });
    }
}
