package co.edu.unicolmayor.janussync.application.usecase;

import co.edu.unicolmayor.janussync.domain.exception.UsuarioNoEncontradoException;
import co.edu.unicolmayor.janussync.domain.model.Usuario;
import co.edu.unicolmayor.janussync.domain.port.UsuarioRepository;
import co.edu.unicolmayor.janussync.domain.valueobject.UsuarioId;

public final class ObtenerUsuarioUseCase {
    private final UsuarioRepository usuarios;

    public ObtenerUsuarioUseCase(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    public Usuario ejecutar(UsuarioId id) {
        return usuarios.obtener(id).orElseThrow(UsuarioNoEncontradoException::new);
    }
}
