package co.edu.unicolmayor.janussync.domain.port;

import co.edu.unicolmayor.janussync.domain.model.Rol;
import java.util.List;

public interface RolRepository {
    List<Rol> listar();
}
