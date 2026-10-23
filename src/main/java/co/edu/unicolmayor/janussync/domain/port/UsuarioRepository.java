package co.edu.unicolmayor.janussync.domain.port;

import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.domain.valueobject.UsuarioId;
import java.util.Optional;

public interface UsuarioRepository {
    Pagina<Usuario> buscar(ConsultaUsuarios consulta);
    Optional<Usuario> obtener(UsuarioId id);
}
