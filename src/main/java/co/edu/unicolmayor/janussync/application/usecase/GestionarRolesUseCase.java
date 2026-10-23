package co.edu.unicolmayor.janussync.application.usecase;

import co.edu.unicolmayor.janussync.domain.exception.ReglaRolException;
import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.domain.port.*;
import co.edu.unicolmayor.janussync.domain.valueobject.*;
import java.util.*;

public final class GestionarRolesUseCase {
    public static final Set<String> ESENCIALES = Set.of("USUARIOS_CONSULTAR", "USUARIOS_ESTADO", "ROLES_ASIGNAR",
            "ROLES_CONSULTAR", "ROLES_PERMISOS", "ROLES_ESTADO");
    private final GestionRolesRepository gestion;
    private final RolRepository roles;
    private final AuditoriaPort auditoria;

    public GestionarRolesUseCase(GestionRolesRepository gestion, RolRepository roles,AuditoriaPort auditoria) {
        this.gestion = gestion;
        this.roles = roles;
        this.auditoria=auditoria;
    }

    private void autorizar(String actor, String permiso) {
        if (!gestion.permisosDeUsuariosActivos().getOrDefault(actor, Set.of()).contains(permiso))
            throw new ReglaRolException("FORBIDDEN", null, "No tienes permiso para esta operación.");
    }

    private String nombre(String valor, String id) {
        final String nombre;
        try {
            nombre = new NombreRol(valor).valor();
        } catch (IllegalArgumentException e) {
            throw new ReglaRolException("VALIDATION", "name", e.getMessage());
        }
        if (gestion.existeNombre(nombre, id))
            throw new ReglaRolException("DUPLICATE_NAME", "name", "Ya existe un rol con este nombre.");
        return nombre;
    }

    private Set<String> validarPermisos(Set<String> permisos) {
        if (permisos == null || permisos.isEmpty() || permisos.stream().anyMatch(Objects::isNull)
                || !gestion.catalogoPermisos().containsAll(permisos))
            throw new ReglaRolException("VALIDATION", "permissions",
                    "Selecciona al menos un permiso válido del catálogo.");
        return Set.copyOf(permisos);
    }

    private Rol obtener(String id) {
        return roles.listar().stream().filter(r -> r.id().equals(id)).findFirst()
                .orElseThrow(() -> new ReglaRolException("NOT_FOUND", null, "No se encontró el rol solicitado."));
    }

    private void protegerAdministracion() {
        var conAcceso=gestion.usuariosConCredenciales();
        if (gestion.permisosDeUsuariosActivos().entrySet().stream().filter(e->conAcceso.contains(e.getKey())).noneMatch(e -> e.getValue().containsAll(ESENCIALES)))
            throw new ReglaRolException("ADMIN_REQUIRED", null,
                    "JanusSync debe conservar al menos una persona activa con contraseña establecida y los permisos esenciales para administrar usuarios, roles y asignaciones.");
    }

    public Rol crear(String actor, String nombre, String descripcion, Set<String> permisos) {
        return gestion.enTransaccion(() -> {
            autorizar(actor, "ROLES_CREAR");
            String id = UUID.randomUUID().toString();
            gestion.crear(id, nombre(nombre, id), new DescripcionRol(descripcion).valor(), validarPermisos(permisos));
            protegerAdministracion();
            var actual=obtener(id);
            auditoria.registrar(actor,"ROL_CREACION","ROL",id,actual.nombre().valor(),"Rol creado; permisos: "+actual.permisos().stream().sorted().toList());
            return actual;
        });
    }

    // Campos ausentes no se modifican. Datos y permisos se guardan atómicamente.
    public Rol editar(String actor, String id, String nombre, String descripcion, Set<String> permisos) {
        return gestion.enTransaccion(() -> {
            Rol anterior = obtener(id);
            if (nombre == null && descripcion == null && permisos == null)
                throw new ReglaRolException("VALIDATION", null, "Indica los cambios del rol.");
            if (nombre != null || descripcion != null) {
                autorizar(actor, "ROLES_EDITAR");
                gestion.datos(id, nombre(nombre == null ? anterior.nombre().valor() : nombre, id),
                        new DescripcionRol(descripcion == null ? anterior.descripcion() : descripcion).valor());
            }
            if (permisos != null) {
                autorizar(actor, "ROLES_PERMISOS");
                gestion.permisos(id, validarPermisos(permisos));
            }
            protegerAdministracion();
            var actual=obtener(id);
            auditoria.registrar(actor,"ROL_EDICION","ROL",id,actual.nombre().valor(),"Datos: "+anterior.nombre().valor()+" → "+actual.nombre().valor()+"; descripción: "+anterior.descripcion()+" → "+actual.descripcion());
            if(permisos!=null)auditoria.registrar(actor,"ROL_PERMISOS","ROL",id,actual.nombre().valor(),"Permisos: "+anterior.permisos().stream().sorted().toList()+" → "+actual.permisos().stream().sorted().toList());
            return actual;
        });
    }

    public Rol estado(String actor, String id, EstadoRegistro estado) {
        return gestion.enTransaccion(() -> {
            autorizar(actor, "ROLES_ESTADO");
            obtener(id);
            if (estado == null)
                throw new ReglaRolException("VALIDATION", null, "Indica un estado válido.");
            gestion.estado(id, estado);
            protegerAdministracion();
            var actual=obtener(id);
            auditoria.registrar(actor,estado==EstadoRegistro.ACTIVO?"ROL_ACTIVACION":"ROL_INACTIVACION","ROL",id,actual.nombre().valor(),"Estado del rol: "+estado);
            return actual;
        });
    }

    public Set<String> catalogo() {
        return gestion.catalogoPermisos();
    }
}
