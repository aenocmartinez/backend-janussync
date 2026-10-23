package co.edu.unicolmayor.janussync.infrastructure.adapter.persistence;

import co.edu.unicolmayor.janussync.domain.exception.ReglaRolException;
import co.edu.unicolmayor.janussync.domain.model.EstadoRegistro;
import co.edu.unicolmayor.janussync.domain.port.GestionRolesRepository;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class GestionRolesMySqlRepository implements GestionRolesRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaccion;

    public GestionRolesMySqlRepository(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        transaccion = new TransactionTemplate(manager);
        transaccion.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public <T> T enTransaccion(Supplier<T> trabajo) {
        try {
            return transaccion.execute(s -> {
                // Todo escritor de roles (y futuras asignaciones/estados de usuarios) debe
                // tomar este bloqueo antes de leer.
                jdbc.queryForObject("SELECT id FROM bloqueo_administracion WHERE id=1 FOR UPDATE", Integer.class);
                return trabajo.get();
            });
        } catch (DuplicateKeyException e) {
            throw new ReglaRolException("DUPLICATE_NAME", "name", "Ya existe un rol con este nombre.");
        }
    }

    public Set<String> usuariosConCredenciales(){return Set.copyOf(jdbc.queryForList("SELECT usuario_id FROM credenciales",String.class));}

    public Map<String, Set<String>> permisosDeUsuariosActivos() {
        Map<String, Set<String>> resultado = new HashMap<>();
        jdbc.query("""
                SELECT u.id,rp.permiso_codigo FROM usuarios u JOIN usuarios_roles ur ON ur.usuario_id=u.id
                JOIN roles r ON r.id=ur.rol_id JOIN roles_permisos rp ON rp.rol_id=r.id
                WHERE u.estado='ACTIVO' AND r.estado='ACTIVO'
                """, (org.springframework.jdbc.core.RowCallbackHandler) r -> resultado
                .computeIfAbsent(r.getString(1), k -> new HashSet<>()).add(r.getString(2)));
        return resultado;
    }

    public Set<String> catalogoPermisos() {
        return Set.copyOf(jdbc.queryForList("SELECT codigo FROM permisos", String.class));
    }

    public boolean existeNombre(String nombre, String id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM roles WHERE nombre=? AND id<>?", Integer.class, nombre,
                id) > 0;
    }

    public void crear(String id, String nombre, String descripcion, Set<String> permisos) {
        jdbc.update("INSERT INTO roles(id,nombre,descripcion,estado,inicial) VALUES(?,?,?,'ACTIVO',FALSE)", id, nombre,
                descripcion);
        permisos(id, permisos);
    }

    public void datos(String id, String nombre, String descripcion) {
        jdbc.update("UPDATE roles SET nombre=?,descripcion=?,actualizado_en=CURRENT_TIMESTAMP(6) WHERE id=?", nombre,
                descripcion, id);
    }

    public void permisos(String id, Set<String> permisos) {
        jdbc.update("DELETE FROM roles_permisos WHERE rol_id=?", id);
        permisos.stream().sorted().forEach(p -> jdbc.update("INSERT INTO roles_permisos VALUES(?,?)", id, p));
        jdbc.update("UPDATE roles SET actualizado_en=CURRENT_TIMESTAMP(6) WHERE id=?", id);
    }

    public void estado(String id, EstadoRegistro estado) {
        jdbc.update("UPDATE roles SET estado=?,actualizado_en=CURRENT_TIMESTAMP(6) WHERE id=?", estado.name(), id);
    }
}
