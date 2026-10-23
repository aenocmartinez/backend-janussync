package co.edu.unicolmayor.janussync.domain.model;

import co.edu.unicolmayor.janussync.domain.valueobject.NombreRol;
import java.util.Objects;
import java.util.Set;

public record Rol(String id, NombreRol nombre, String descripcion, EstadoRegistro estado,
        boolean inicial, Set<String> permisos) {
    public Rol {
        Objects.requireNonNull(id);
        Objects.requireNonNull(nombre);
        Objects.requireNonNull(estado);
        permisos = Set.copyOf(permisos);
    }
}
