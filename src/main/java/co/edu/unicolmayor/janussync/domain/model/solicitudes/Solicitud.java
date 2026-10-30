package co.edu.unicolmayor.janussync.domain.model.solicitudes;
import java.time.Instant;
public record Solicitud(String id,long numero,String creadorId,String responsableId,EstadoSolicitud estado,long version,ContenidoSolicitud contenido,Instant creadaEn,Instant actualizadaEn) {}
