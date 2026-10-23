package co.edu.unicolmayor.janussync.infrastructure.adapter.web;

import co.edu.unicolmayor.janussync.application.usecase.ListarRolesUseCase;
import co.edu.unicolmayor.janussync.infrastructure.adapter.web.dto.RolDTO;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/roles")
public class RolController {
    private final ListarRolesUseCase listar;

    public RolController(ListarRolesUseCase listar) { this.listar = listar; }

    @GetMapping
    public List<RolDTO> listar() {
        return listar.ejecutar().stream().map(RolDTO::desde).toList();
    }
}
