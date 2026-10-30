package co.edu.unicolmayor.janussync.domain.model.solicitudes;
import java.time.Instant;
public record SeguimientoSolicitud(String id,String accion,String actor,String comentario,ContenidoSolicitud contenido,Instant instante) {}
