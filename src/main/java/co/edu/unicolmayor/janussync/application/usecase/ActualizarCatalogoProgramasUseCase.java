package co.edu.unicolmayor.janussync.application.usecase;
import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.domain.port.*;
import co.edu.unicolmayor.janussync.domain.exception.*;
import java.time.*;
import java.util.*;
public final class ActualizarCatalogoProgramasUseCase {
    private final CatalogoProgramasRepository catalogo;
    private final FuenteProgramasAcademicos fuente;
    private final GestionarUsuariosUseCase autorizacion;
    private final Duration pausa;
    public ActualizarCatalogoProgramasUseCase(CatalogoProgramasRepository catalogo,FuenteProgramasAcademicos fuente,GestionarUsuariosUseCase autorizacion,Duration pausa) {
        if(pausa.isNegative()||pausa.isZero())throw new IllegalArgumentException("Configura una pausa positiva.");
        this.catalogo=catalogo;this.fuente=fuente;this.autorizacion=autorizacion;this.pausa=pausa;
    }
    public EstadoCatalogoProgramas manual(String actor) {
        if(actor==null||actor.equals("SISTEMA"))throw new ReglaRolException("FORBIDDEN",null,"No tienes permiso para actualizar programas.");
        return actualizar(actor,null);
    }
    public EstadoCatalogoProgramas sistema(Instant siguienteProgramada) { return actualizar("SISTEMA",siguienteProgramada); }
    private void autorizar(String actor) { if(!actor.equals("SISTEMA"))autorizacion.autorizar(actor,"PROGRAMAS_ACTUALIZAR"); }
    private EstadoCatalogoProgramas actualizar(String actor,Instant siguiente) {
        autorizar(actor);
        var reserva=catalogo.reservar(actor,pausa,siguiente);
        if(reserva.isEmpty())return catalogo.estado();
        try(var turno=reserva.get()) {
            String fallo="FALLO_FUENTE";
            try {
                var lectura=fuente.leerCatalogo();
                fallo="LECTURA_RECHAZADA";
                validar(lectura);
                fallo="FALLO_PUBLICACION";
                catalogo.publicar(turno,lectura.programas(),actor,()->autorizar(actor));
            } catch(RuntimeException e) {
                catalogo.fallar(turno,actor,e instanceof ReglaRolException regla && regla.codigo().equals("FORBIDDEN")?"NO_AUTORIZADO":fallo);
                if(e instanceof ReglaRolException regla && regla.codigo().equals("FORBIDDEN"))throw regla;
                throw new ActualizacionCatalogoException(e);
            }
        }
        return catalogo.estado();
    }
    private void validar(LecturaProgramas lectura) {
        if(lectura==null||!lectura.completa()||lectura.programas().isEmpty()||lectura.programas().size()!=lectura.totalEsperado()||lectura.totalEsperado()>10000)
            throw new IllegalArgumentException("La lectura académica está vacía o incompleta.");
        var ids=new HashSet<String>();
        for(var p:lectura.programas()) {
            if(p==null||p.id()==null||!ids.add(p.id().valor())||p.nombre()==null||p.nombre().isBlank()||p.nombre().length()>1000||
                p.codigo()!=null&&p.codigo().length()>100||p.estadoOficial()!=null&&p.estadoOficial().length()>20||
                java.util.stream.Stream.of(p.modalidad(),p.metodologia(),p.jornada()).anyMatch(v->v!=null&&v.length()>250)||!p.presenteEnFuente())
                throw new IllegalArgumentException("La lectura académica no es íntegra.");
        }
    }
}
