package co.edu.unicolmayor.janussync.infrastructure.adapter.persistence;

import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.domain.port.AccesoRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class AccesoMySqlRepository implements AccesoRepository {
    private final JdbcTemplate jdbc;

    public AccesoMySqlRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private Timestamp fecha(Instant instante) {
        return Timestamp.from(instante);
    }

    @Override
    public Optional<CuentaAcceso> buscarCuenta(String correo) {
        return jdbc
                .query("""
                        SELECT u.id,c.hash_contrasena,u.estado FROM usuarios u
                        JOIN credenciales c ON c.usuario_id=u.id WHERE u.correo=?
                        """,
                        (r, n) -> new CuentaAcceso(UUID.fromString(r.getString(1)), r.getString(2),
                                "ACTIVO".equals(r.getString(3))),
                        correo)
                .stream().findFirst();
    }

    @Override
    public void abrir(SesionAcceso sesion, String hash, Instant ahora) {
        jdbc.update("INSERT INTO sesiones_autenticacion(id,usuario_id,creado_en,vence_en) VALUES(?,?,?,?)",
                sesion.id().toString(), sesion.usuarioId().toString(), fecha(ahora), fecha(sesion.venceEn()));
        insertarToken(sesion.id(), hash, ahora);
    }

    private void insertarToken(UUID sesion, String hash, Instant ahora) {
        jdbc.update("INSERT INTO tokens_renovacion(hash_token,sesion_id,creado_en) VALUES(?,?,?)", hash,
                sesion.toString(), fecha(ahora));
    }

    @Override
    public Optional<SesionAcceso> renovar(String anterior, String nuevo, Instant ahora) {
        var ids = jdbc.queryForList("SELECT sesion_id FROM tokens_renovacion WHERE hash_token=?", String.class,
                anterior);
        if (ids.isEmpty())
            return Optional.empty();
        String id = ids.getFirst();
        var sesiones = jdbc
                .query("SELECT id,usuario_id,vence_en,revocada_en FROM sesiones_autenticacion WHERE id=? FOR UPDATE",
                        (r, n) -> new Object[] { new SesionAcceso(UUID.fromString(r.getString(1)),
                                UUID.fromString(r.getString(2)), r.getTimestamp(3).toInstant()), r.getTimestamp(4) },
                        id);
        if (sesiones.isEmpty())
            return Optional.empty();
        var sesion = (SesionAcceso) sesiones.getFirst()[0];
        // Lectura actual bajo el mismo bloqueo de sesión, incluso con REPEATABLE READ.
        var consumidos = jdbc.query("SELECT consumido_en FROM tokens_renovacion WHERE hash_token=? FOR UPDATE",
                (r, n) -> r.getTimestamp(1) != null, anterior);
        boolean activa = Boolean.TRUE.equals(jdbc.queryForObject("SELECT estado='ACTIVO' FROM usuarios WHERE id=?",
                Boolean.class, sesion.usuarioId().toString()));
        if (consumidos.isEmpty() || consumidos.getFirst() || sesiones.getFirst()[1] != null
                || !sesion.venceEn().isAfter(ahora) || !activa) {
            revocarSesion(id, ahora);
            return Optional.empty();
        }
        jdbc.update("UPDATE tokens_renovacion SET consumido_en=? WHERE hash_token=?", fecha(ahora), anterior);
        insertarToken(sesion.id(), nuevo, ahora);
        return Optional.of(sesion);
    }

    private void revocarSesion(String id, Instant ahora) {
        jdbc.update("UPDATE sesiones_autenticacion SET revocada_en=COALESCE(revocada_en,?) WHERE id=?", fecha(ahora),
                id);
    }

    @Override
    public void revocarSesion(UUID sesion, UUID usuario, Instant ahora) {
        jdbc.update("UPDATE sesiones_autenticacion SET revocada_en=COALESCE(revocada_en,?) WHERE id=? AND usuario_id=?",
                fecha(ahora), sesion.toString(), usuario.toString());
    }

    @Override
    public void revocar(String hash, Instant ahora) {
        jdbc.queryForList("SELECT sesion_id FROM tokens_renovacion WHERE hash_token=?", String.class, hash)
                .forEach(id -> revocarSesion(id, ahora));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Set<String>> permisosActuales(UUID sesion, UUID usuario, Instant ahora) {
        Integer cantidad = jdbc.queryForObject("""
                SELECT COUNT(*) FROM sesiones_autenticacion s JOIN usuarios u ON u.id=s.usuario_id
                WHERE s.id=? AND s.usuario_id=? AND s.revocada_en IS NULL AND s.vence_en>? AND u.estado='ACTIVO'
                """, Integer.class, sesion.toString(), usuario.toString(), fecha(ahora));
        if (cantidad == null || cantidad != 1)
            return Optional.empty();
        return Optional.of(Set.copyOf(jdbc.queryForList("""
                SELECT DISTINCT rp.permiso_codigo FROM usuarios_roles ur JOIN roles r ON r.id=ur.rol_id
                JOIN roles_permisos rp ON rp.rol_id=r.id WHERE ur.usuario_id=? AND r.estado='ACTIVO'
                """, String.class, usuario.toString())));
    }

    @Override
    public boolean consumirIntento(String clave, Instant ahora, int limite, long ventana) {
        jdbc.update("INSERT IGNORE INTO limites_autenticacion(clave_hash,inicio_en,intentos) VALUES(?,?,0)", clave,
                fecha(ahora));
        var datos = jdbc.queryForMap(
                "SELECT inicio_en,intentos FROM limites_autenticacion WHERE clave_hash=? FOR UPDATE", clave);
        boolean reiniciar = !((Timestamp) datos.get("inicio_en")).toInstant().plusSeconds(ventana).isAfter(ahora);
        int intentos = reiniciar ? 0 : ((Number) datos.get("intentos")).intValue();
        if (intentos >= limite)
            return false;
        jdbc.update("UPDATE limites_autenticacion SET inicio_en=?,intentos=? WHERE clave_hash=?",
                reiniciar ? fecha(ahora) : datos.get("inicio_en"), intentos + 1, clave);
        return true;
    }

    @Override
    public void inicializar(AdministradorInicial datos, String hash, Instant ahora) {
        boolean completada = Boolean.TRUE.equals(jdbc
                .queryForObject("SELECT completada FROM inicializacion_acceso WHERE id=1 FOR UPDATE", Boolean.class));
        if (completada || jdbc.queryForObject("SELECT COUNT(*) FROM credenciales", Integer.class) > 0)
            throw new IllegalStateException(
                    "La inicialización de acceso ya fue realizada o hay credenciales existentes.");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM roles WHERE id='ADMINISTRADOR' AND estado='ACTIVO'",
                Integer.class) != 1)
            throw new IllegalStateException("El rol inicial de administración debe estar activo.");
        String id = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO usuarios(id,nombres,apellidos,correo,estado,creado_en,actualizado_en) VALUES(?,?,?,?,'ACTIVO',?,?)",
                id, datos.nombres().valor(), datos.apellidos().valor(), datos.correo().valor(), fecha(ahora),
                fecha(ahora));
        jdbc.update("INSERT INTO credenciales VALUES(?,?,?)", id, hash, fecha(ahora));
        jdbc.update("INSERT INTO usuarios_roles VALUES(?,'ADMINISTRADOR')", id);
        jdbc.update("UPDATE inicializacion_acceso SET completada=TRUE WHERE id=1");
    }
}
