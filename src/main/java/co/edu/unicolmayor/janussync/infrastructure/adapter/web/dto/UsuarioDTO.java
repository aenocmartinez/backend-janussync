package co.edu.unicolmayor.janussync.infrastructure.adapter.web.dto;

import co.edu.unicolmayor.janussync.domain.model.Usuario;
import java.time.Instant;
import java.util.List;

public record UsuarioDTO(String id, String nombres, String apellidos, String correo, String cargo,
        String estado, List<String> roles, Instant creadoEn, Instant actualizadoEn,Instant contrasenaActualizada,co.edu.unicolmayor.janussync.domain.model.CorreoAcceso ultimoCorreo,List<String> programas,long versionAsignacion) {
    public static UsuarioDTO desde(Usuario usuario) {
        return new UsuarioDTO(usuario.id().valor().toString(), usuario.nombres().valor(), usuario.apellidos().valor(),
                usuario.correo().valor(), usuario.cargo(), usuario.estado().name(), usuario.roles().stream().sorted().toList(),
                usuario.creadoEn(), usuario.actualizadoEn(),usuario.contrasenaActualizada(),usuario.ultimoCorreo(),usuario.programas().stream().sorted().toList(),usuario.versionAsignacion());
    }
}
