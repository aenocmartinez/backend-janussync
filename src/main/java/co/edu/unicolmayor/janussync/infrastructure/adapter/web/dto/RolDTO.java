package co.edu.unicolmayor.janussync.infrastructure.adapter.web.dto;

import co.edu.unicolmayor.janussync.domain.model.Rol;
import java.util.List;

public record RolDTO(String id, String nombre, String descripcion, String estado, boolean inicial, List<String> permisos) {
    public static RolDTO desde(Rol rol) {
        return new RolDTO(rol.id(), rol.nombre().valor(), rol.descripcion(), rol.estado().name(), rol.inicial(),
                rol.permisos().stream().sorted().toList());
    }
}
