package co.edu.unicolmayor.janussync.domain.model.solicitudes;
import java.util.List;
public record ContenidoSolicitud(String programaId,String programa,String periodoId,String periodo,List<GrupoAcademico> grupos,String nota,String origen,java.time.Instant consultadaEn) {
 public ContenidoSolicitud {grupos=List.copyOf(grupos);}
}
