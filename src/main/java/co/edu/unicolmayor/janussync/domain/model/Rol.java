package co.edu.unicolmayor.janussync.domain.model;

import co.edu.unicolmayor.janussync.domain.valueobject.NombreRol;
import java.util.Objects;
import java.time.Instant;
import java.util.Set;

public record Rol(String id, NombreRol nombre, String descripcion, EstadoRegistro estado,
        boolean inicial, Set<String> permisos, long totalUsuarios, long usuariosActivos,
        Instant creadoEn, Instant actualizadoEn) {
    public Rol {
        Objects.requireNonNull(id);
        Objects.requireNonNull(nombre);
        Objects.requireNonNull(estado);
        permisos = Set.copyOf(permisos);
        Objects.requireNonNull(creadoEn);
        Objects.requireNonNull(actualizadoEn);
        if (totalUsuarios < 0 || usuariosActivos < 0 || usuariosActivos > totalUsuarios)
            throw new IllegalArgumentException("Cantidades de usuarios inválidas.");
    }
}
