package co.edu.unicolmayor.janussync.application.usecase;

import co.edu.unicolmayor.janussync.domain.model.Rol;
import co.edu.unicolmayor.janussync.domain.port.RolRepository;
import java.util.List;

public final class ListarRolesUseCase {
    private final RolRepository roles;

    public ListarRolesUseCase(RolRepository roles) {
        this.roles = roles;
    }

    public List<Rol> ejecutar() {
        return roles.listar();
    }
}
