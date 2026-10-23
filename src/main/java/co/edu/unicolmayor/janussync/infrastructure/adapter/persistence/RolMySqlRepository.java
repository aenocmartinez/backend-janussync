package co.edu.unicolmayor.janussync.infrastructure.adapter.persistence;

import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.domain.port.RolRepository;
import co.edu.unicolmayor.janussync.domain.valueobject.NombreRol;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class RolMySqlRepository implements RolRepository {
    private final JdbcTemplate jdbc;

    public RolMySqlRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Rol> listar() {
        Map<String, Rol> roles = new LinkedHashMap<>();
        Map<String, Set<String>> permisos = new HashMap<>();
        jdbc.query("""
                SELECT r.id, r.nombre, r.descripcion, r.estado, r.inicial, r.creado_en, r.actualizado_en,
                       COALESCE(c.total,0) AS total_usuarios, COALESCE(c.activos,0) AS usuarios_activos,
                       rp.permiso_codigo
                FROM roles r LEFT JOIN roles_permisos rp ON rp.rol_id=r.id
                LEFT JOIN (
                    SELECT ur.rol_id, COUNT(*) AS total, SUM(u.estado='ACTIVO') AS activos
                    FROM usuarios_roles ur JOIN usuarios u ON u.id=ur.usuario_id GROUP BY ur.rol_id
                ) c ON c.rol_id=r.id
                ORDER BY r.nombre, r.id, rp.permiso_codigo
                """, (org.springframework.jdbc.core.RowCallbackHandler) fila -> {
            String id = fila.getString("id");
            roles.putIfAbsent(id, new Rol(id, new NombreRol(fila.getString("nombre")), fila.getString("descripcion"),
                    EstadoRegistro.valueOf(fila.getString("estado")), fila.getBoolean("inicial"), Set.of(),
                    fila.getLong("total_usuarios"), fila.getLong("usuarios_activos"),
                    fila.getTimestamp("creado_en").toInstant(), fila.getTimestamp("actualizado_en").toInstant()));
            String codigo = fila.getString("permiso_codigo");
            if (codigo != null)
                permisos.computeIfAbsent(id, clave -> new HashSet<>()).add(codigo);
        });
        return roles.values().stream().map(r -> new Rol(r.id(), r.nombre(), r.descripcion(), r.estado(),
                r.inicial(), permisos.getOrDefault(r.id(), Set.of()), r.totalUsuarios(), r.usuariosActivos(),
                r.creadoEn(), r.actualizadoEn())).toList();
    }
}
