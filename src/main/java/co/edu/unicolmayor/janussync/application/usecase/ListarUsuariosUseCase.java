package co.edu.unicolmayor.janussync.application.usecase;

import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.domain.port.UsuarioRepository;

public final class ListarUsuariosUseCase {
    private final UsuarioRepository usuarios;

    public ListarUsuariosUseCase(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    public Pagina<Usuario> ejecutar(ConsultaUsuarios consulta) {
        return usuarios.buscar(consulta);
    }
}
