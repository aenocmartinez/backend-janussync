package co.edu.unicolmayor.janussync.infrastructure.adapter.web;

import co.edu.unicolmayor.janussync.application.usecase.*;
import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.domain.valueobject.UsuarioId;
import co.edu.unicolmayor.janussync.infrastructure.adapter.web.dto.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/usuarios")
public class UsuarioController {
    private final ListarUsuariosUseCase listar;
    private final ObtenerUsuarioUseCase obtener;

    public UsuarioController(ListarUsuariosUseCase listar, ObtenerUsuarioUseCase obtener) {
        this.listar = listar;
        this.obtener = obtener;
    }

    @GetMapping
    public PaginaDTO<UsuarioDTO> listar(@RequestParam(defaultValue = "") String busqueda,
            @RequestParam(required = false) EstadoRegistro estado, @RequestParam(required = false) String rol,
            @RequestParam(defaultValue = "1") int pagina, @RequestParam(defaultValue = "10") int tamano) {
        return PaginaDTO.desde(listar.ejecutar(new ConsultaUsuarios(busqueda, estado, rol, pagina, tamano)),
                UsuarioDTO::desde);
    }

    @GetMapping("/{id}")
    public UsuarioDTO obtener(@PathVariable String id) {
        return UsuarioDTO.desde(obtener.ejecutar(UsuarioId.desde(id)));
    }
}
