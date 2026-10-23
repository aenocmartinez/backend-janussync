package co.edu.unicolmayor.janussync.domain.model;
import java.time.Instant;
public record EventoAuditoria(String id,String actorId,String actorNombre,String accion,String entidadTipo,String entidadId,String entidadNombre,Instant instante,String descripcion) {}
