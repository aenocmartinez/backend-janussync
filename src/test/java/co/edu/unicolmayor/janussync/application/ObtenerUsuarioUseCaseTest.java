package co.edu.unicolmayor.janussync.application;

import co.edu.unicolmayor.janussync.application.usecase.ObtenerUsuarioUseCase;
import co.edu.unicolmayor.janussync.domain.exception.UsuarioNoEncontradoException;
import co.edu.unicolmayor.janussync.domain.port.UsuarioRepository;
import co.edu.unicolmayor.janussync.domain.valueobject.UsuarioId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ObtenerUsuarioUseCaseTest {
    @Test
    void distingueAusenciaDeUsuarioDeUnFalloDelRepositorio() {
        var repositorio = mock(UsuarioRepository.class);
        var id = new UsuarioId(UUID.randomUUID());
        var caso = new ObtenerUsuarioUseCase(repositorio);
        when(repositorio.obtener(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> caso.ejecutar(id)).isInstanceOf(UsuarioNoEncontradoException.class);
        when(repositorio.obtener(id)).thenThrow(new IllegalStateException("fallo de infraestructura"));
        assertThatThrownBy(() -> caso.ejecutar(id)).isInstanceOf(IllegalStateException.class)
                .hasMessage("fallo de infraestructura");
    }
}
