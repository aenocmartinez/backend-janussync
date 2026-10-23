package co.edu.unicolmayor.janussync.infrastructure.adapter.web;

import co.edu.unicolmayor.janussync.application.usecase.ListarRolesUseCase;
import co.edu.unicolmayor.janussync.infrastructure.adapter.web.dto.RolDTO;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/roles")
public class RolController {
    private final ListarRolesUseCase listar;
    private final co.edu.unicolmayor.janussync.application.usecase.GestionarRolesUseCase gestionar;

    public RolController(ListarRolesUseCase listar,
            co.edu.unicolmayor.janussync.application.usecase.GestionarRolesUseCase gestionar) {
        this.listar = listar;
        this.gestionar = gestionar;
    }

    public record Cambios(String nombre, String descripcion, java.util.Set<String> permisos) {
    }

    public record CambioEstado(co.edu.unicolmayor.janussync.domain.model.EstadoRegistro estado) {
    }

    @GetMapping("/permisos")
    public java.util.Set<String> permisos() {
        return gestionar.catalogo();
    }

    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public RolDTO crear(org.springframework.security.core.Authentication actor, @RequestBody Cambios datos) {
        return RolDTO.desde(gestionar.crear(actor.getName(), datos.nombre(), datos.descripcion(), datos.permisos()));
    }

    @PatchMapping("/{id}")
    public RolDTO editar(org.springframework.security.core.Authentication actor, @PathVariable String id,
            @RequestBody Cambios datos) {
        return RolDTO
                .desde(gestionar.editar(actor.getName(), id, datos.nombre(), datos.descripcion(), datos.permisos()));
    }

    @PutMapping("/{id}/estado")
    public RolDTO estado(org.springframework.security.core.Authentication actor, @PathVariable String id,
            @RequestBody CambioEstado datos) {
        return RolDTO.desde(gestionar.estado(actor.getName(), id, datos.estado()));
    }

    @GetMapping
    public List<RolDTO> listar() {
        return listar.ejecutar().stream().map(RolDTO::desde).toList();
    }
}
