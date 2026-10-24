package co.edu.unicolmayor.janussync.infrastructure.adapter.persistence;

import co.edu.unicolmayor.janussync.domain.exception.ReglaRolException;
import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.domain.port.*;
import java.sql.*;
import java.time.*;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Repository
public class IntegracionesMySqlRepository implements IntegracionesRepository {
    private final JdbcTemplate jdbc;
    private final DataSource fuente;
    private final TransactionTemplate transaccion;
    private final AuditoriaPort auditoria;
    public IntegracionesMySqlRepository(JdbcTemplate jdbc, DataSource fuente, PlatformTransactionManager gestor, AuditoriaPort auditoria) {
        this.jdbc = jdbc; this.fuente = fuente; this.auditoria = auditoria;
        this.transaccion = new TransactionTemplate(gestor);
    }
    public EstadoIntegracion consultar(Plataforma p, OrigenComprobacion origen, boolean configurada, int vigencia) {
        return jdbc.queryForObject("SELECT *, reserva_hasta > UTC_TIMESTAMP(6) AS vigente FROM estados_integraciones WHERE plataforma=? AND origen=?",
                (r, n) -> new EstadoIntegracion(p, configurada,
                    r.getString("comprobacion_id") == null ? null : new ComprobacionConexion(r.getString("comprobacion_id"),
                        instante(r,"inicio"), instante(r,"fin"), new ResultadoConexion(DiagnosticoConexion.valueOf(r.getString("codigo"))), r.getLong("duracion_ms"), r.getString("alcance")),
                    r.getBoolean("vigente") ? instante(r,"comprobando_desde") : null, instante(r,"fallando_desde"), vigencia, origen), p.name(), origen.name());
    }
    private static Instant instante(ResultSet r, String campo) throws SQLException {
        var t = r.getTimestamp(campo); return t == null ? null : t.toInstant();
    }
    public Reserva reservar(Plataforma p, OrigenComprobacion origen, Duration pausa, Duration maximo) {
        // GET_LOCK vive en esta conexión dedicada, sin transacción abierta durante la llamada externa.
        // También evita duplicados si vence el indicador persistido mientras el driver aún está finalizando.
        Connection c = null;
        String nombre = "janus:conexion:" + p.name();
        try {
            c = fuente.getConnection();
            try (var s = c.prepareStatement("SELECT GET_LOCK(?,0)")) {
                s.setString(1,nombre);
                try (var r = s.executeQuery()) {
                    if (!r.next() || r.getInt(1) != 1) throw conflicto("Ya hay una comprobación de esta plataforma en curso.");
                }
            }
            String id = UUID.randomUUID().toString();
            int filas = jdbc.update("""
                UPDATE estados_integraciones SET reserva_id=?, comprobando_desde=UTC_TIMESTAMP(6),
                 reserva_hasta=TIMESTAMPADD(SECOND,?,UTC_TIMESTAMP(6)), proxima_permitida=TIMESTAMPADD(SECOND,?,UTC_TIMESTAMP(6))
                WHERE plataforma=? AND origen=? AND (proxima_permitida IS NULL OR proxima_permitida<=UTC_TIMESTAMP(6))
                """, id, maximo.toSeconds()+30, pausa.toSeconds(), p.name(), origen.name());
            if (filas != 1) throw conflicto("Espera antes de solicitar otra comprobación de esta plataforma.");
            Instant inicio = jdbc.queryForObject("SELECT comprobando_desde FROM estados_integraciones WHERE plataforma=? AND origen=?",
                    (r,n) -> instante(r,"comprobando_desde"), p.name(), origen.name());
            Connection conexion = c;
            return new Reserva() {
                public String id() { return id; }
                public Instant inicio() { return inicio; }
                public void close() {
                    try { jdbc.update("UPDATE estados_integraciones SET reserva_id=NULL,reserva_hasta=NULL,comprobando_desde=NULL WHERE plataforma=? AND origen=? AND reserva_id=?",p.name(),origen.name(),id); }
                    finally { liberar(conexion,nombre); }
                }
            };
        } catch (SQLException e) {
            liberar(c,nombre);
            throw new DataAccessResourceFailureException("No fue posible reservar la comprobación.");
        } catch (RuntimeException e) { liberar(c,nombre); throw e; }
    }
    private static ReglaRolException conflicto(String mensaje) { return new ReglaRolException("CONFLICT",null,mensaje); }
    private static void liberar(Connection c, String nombre) {
        if (c == null) return;
        try {
            try (var s = c.prepareStatement("SELECT RELEASE_LOCK(?)")) { s.setString(1,nombre); s.execute(); }
        } catch (SQLException e) { try { c.abort(Runnable::run); } catch (SQLException ignored) { /* Conexión no reutilizable. */ } }
        finally { try { c.close(); } catch (SQLException ignored) { /* Sin datos externos en logs. */ } }
    }
    public void completar(Plataforma p, OrigenComprobacion origen, ComprobacionConexion comprobacion, String actor) {
        transaccion.executeWithoutResult(t -> {
            int filas = jdbc.update("""
                UPDATE estados_integraciones SET comprobacion_id=?,inicio=?,fin=?,codigo=?,duracion_ms=?,alcance=?,
                 fallando_desde=CASE WHEN ?='NO_DISPONIBLE' THEN COALESCE(fallando_desde,?) ELSE NULL END
                 WHERE plataforma=? AND origen=? AND reserva_id=?
                """,comprobacion.id(),Timestamp.from(comprobacion.inicio()),Timestamp.from(comprobacion.fin()),
                    comprobacion.resultado().codigo().name(),comprobacion.duracionMs(),comprobacion.alcance(),comprobacion.resultado().estado(),Timestamp.from(comprobacion.fin()),p.name(),origen.name(),comprobacion.id());
            if (filas != 1) throw conflicto("No fue posible guardar esta comprobación.");
            auditoria.registrar(actor,"INTEGRACION_COMPROBACION","INTEGRACION",p.name(),p.nombre(),
                    "Comprobación de comunicación de solo lectura (" + origen.name() + "): " + comprobacion.resultado().codigo().name() + ".");
        });
    }
}
