package co.edu.unicolmayor.janussync.domain.model;

import co.edu.unicolmayor.janussync.domain.valueobject.*;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** Proyección inmutable del usuario interno. Los secretos de acceso no forman parte de ella. */
public record Usuario(UsuarioId id, NombrePersona nombres, NombrePersona apellidos,
        CorreoElectronico correo, String cargo, EstadoRegistro estado, Set<String> roles,
        Instant creadoEn, Instant actualizadoEn, Instant contrasenaActualizada, CorreoAcceso ultimoCorreo,
        Set<String> programas, long versionAsignacion) {
    public Usuario(UsuarioId id,NombrePersona nombres,NombrePersona apellidos,CorreoElectronico correo,String cargo,EstadoRegistro estado,Set<String> roles,Instant creadoEn,Instant actualizadoEn,Instant contrasenaActualizada,CorreoAcceso ultimoCorreo){this(id,nombres,apellidos,correo,cargo,estado,roles,creadoEn,actualizadoEn,contrasenaActualizada,ultimoCorreo,Set.of(),0);}
    public Usuario(UsuarioId id,NombrePersona nombres,NombrePersona apellidos,CorreoElectronico correo,String cargo,EstadoRegistro estado,Set<String> roles,Instant creadoEn,Instant actualizadoEn){this(id,nombres,apellidos,correo,cargo,estado,roles,creadoEn,actualizadoEn,null,null);}
    public Usuario {
        Objects.requireNonNull(id);
        Objects.requireNonNull(nombres);
        Objects.requireNonNull(apellidos);
        Objects.requireNonNull(correo);
        Objects.requireNonNull(estado);
        Objects.requireNonNull(creadoEn);
        Objects.requireNonNull(actualizadoEn);
        roles = Set.copyOf(roles);
        programas = Set.copyOf(programas);
    }
}
