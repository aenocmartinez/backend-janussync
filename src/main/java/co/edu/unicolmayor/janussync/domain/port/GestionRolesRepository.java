package co.edu.unicolmayor.janussync.domain.port;

import co.edu.unicolmayor.janussync.domain.model.*;
import java.util.*;
import java.util.function.Supplier;

public interface GestionRolesRepository {
    <T> T enTransaccion(Supplier<T> trabajo);

    Map<String, Set<String>> permisosDeUsuariosActivos();
    Set<String> usuariosConCredenciales();

    Set<String> catalogoPermisos();

    boolean existeNombre(String nombre, String exceptoId);

    void crear(String id, String nombre, String descripcion, Set<String> permisos);

    void datos(String id, String nombre, String descripcion);

    void permisos(String id, Set<String> permisos);

    void estado(String id, EstadoRegistro estado);
}
