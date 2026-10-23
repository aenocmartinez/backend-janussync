package co.edu.unicolmayor.janussync.infrastructure.adapter.persistence;

import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.domain.port.UsuarioRepository;
import co.edu.unicolmayor.janussync.domain.valueobject.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class UsuarioMySqlRepository implements UsuarioRepository {
    private static final String COLUMNAS = "u.id, u.nombres, u.apellidos, u.correo, u.cargo, u.estado, u.creado_en, u.actualizado_en, (SELECT c.actualizado_en FROM credenciales c WHERE c.usuario_id=u.id) AS contrasena_en, e.proposito AS correo_proposito,e.estado AS correo_estado,e.solicitado_en AS correo_en,e.vence_en AS correo_vence,(SELECT CONCAT(a.nombres,' ',a.apellidos) FROM usuarios a WHERE a.id=e.solicitado_por) AS correo_actor,ep.invalidado_en AS enlace_invalidado,ep.utilizado_en AS enlace_utilizado";
    private static final String ENVIO=" LEFT JOIN envios_acceso e ON e.id=(SELECT ultimo.id FROM envios_acceso ultimo WHERE ultimo.usuario_id=u.id AND ultimo.correo=u.correo ORDER BY ultimo.solicitado_en DESC,ultimo.id DESC LIMIT 1) LEFT JOIN enlaces_contrasena ep ON ep.id=e.enlace_id ";
    private final NamedParameterJdbcTemplate jdbc;

    public UsuarioMySqlRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Pagina<Usuario> buscar(ConsultaUsuarios consulta) {
        var parametros = new MapSqlParameterSource();
        StringBuilder filtro = new StringBuilder(" WHERE 1=1");
        if (!consulta.busqueda().isEmpty()) {
            filtro.append(
                    " AND (CONCAT(u.nombres, ' ', u.apellidos) LIKE :busqueda ESCAPE '!' OR u.correo LIKE :busqueda ESCAPE '!')");
            parametros.addValue("busqueda", "%" + escaparBusqueda(consulta.busqueda()) + "%");
        }
        if (consulta.estado() != null) {
            filtro.append(" AND u.estado = :estado");
            parametros.addValue("estado", consulta.estado().name());
        }
        if (consulta.rol() != null) {
            filtro.append(" AND EXISTS (SELECT 1 FROM usuarios_roles ur WHERE ur.usuario_id=u.id AND ur.rol_id=:rol)");
            parametros.addValue("rol", consulta.rol());
        }
        long total = Objects.requireNonNull(
                jdbc.queryForObject("SELECT COUNT(*) FROM usuarios u" + filtro, parametros, Long.class));
        parametros.addValue("limite", consulta.tamano()).addValue("desplazamiento", consulta.desplazamiento());
        var usuarios = jdbc.query("SELECT " + COLUMNAS + " FROM usuarios u" + ENVIO + filtro
                + " ORDER BY u.apellidos, u.nombres, u.id LIMIT :limite OFFSET :desplazamiento", parametros,
                this::mapear);
        return new Pagina<>(conRoles(usuarios), consulta.pagina(), consulta.tamano(), total);
    }

    @Override
    public Optional<Usuario> obtener(UsuarioId id) {
        var usuarios = jdbc.query("SELECT " + COLUMNAS + " FROM usuarios u" + ENVIO + " WHERE u.id=:id",
                Map.of("id", id.valor().toString()), this::mapear);
        return conRoles(usuarios).stream().findFirst();
    }

    private List<Usuario> conRoles(List<Usuario> usuarios) {
        if (usuarios.isEmpty())
            return List.of();
        Map<String, Set<String>> roles = new HashMap<>();
        jdbc.query("SELECT usuario_id, rol_id FROM usuarios_roles WHERE usuario_id IN (:ids)",
                Map.of("ids", usuarios.stream().map(u -> u.id().valor().toString()).toList()),
                (org.springframework.jdbc.core.RowCallbackHandler) fila -> roles
                        .computeIfAbsent(fila.getString("usuario_id"), clave -> new HashSet<>())
                        .add(fila.getString("rol_id")));
        return usuarios.stream().map(u -> new Usuario(u.id(), u.nombres(), u.apellidos(), u.correo(), u.cargo(),
                u.estado(), roles.getOrDefault(u.id().valor().toString(), Set.of()), u.creadoEn(), u.actualizadoEn(),u.contrasenaActualizada(),u.ultimoCorreo()))
                .toList();
    }

    private Usuario mapear(ResultSet fila, int numero) throws SQLException {
        return new Usuario(UsuarioId.desde(fila.getString("id")), new NombrePersona(fila.getString("nombres")),
                new NombrePersona(fila.getString("apellidos")), new CorreoElectronico(fila.getString("correo")),
                fila.getString("cargo"), EstadoRegistro.valueOf(fila.getString("estado")), Set.of(),
                fila.getTimestamp("creado_en").toInstant(), fila.getTimestamp("actualizado_en").toInstant(),
                fila.getTimestamp("contrasena_en")==null?null:fila.getTimestamp("contrasena_en").toInstant(),
                fila.getString("correo_proposito")==null?null:new CorreoAcceso(fila.getString("correo_proposito"),fila.getString("correo_estado"),fila.getTimestamp("correo_en").toInstant(),fila.getTimestamp("correo_vence").toInstant(),fila.getString("correo_actor"),fila.getTimestamp("enlace_invalidado")==null?null:fila.getTimestamp("enlace_invalidado").toInstant(),fila.getTimestamp("enlace_utilizado")==null?null:fila.getTimestamp("enlace_utilizado").toInstant()));
    }

    private String escaparBusqueda(String valor) {
        return valor.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
