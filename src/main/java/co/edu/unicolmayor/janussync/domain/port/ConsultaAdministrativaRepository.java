package co.edu.unicolmayor.janussync.domain.port;
import co.edu.unicolmayor.janussync.domain.model.*;
import java.time.Instant;
import java.util.*;
public interface ConsultaAdministrativaRepository {
 Pagina<EventoAuditoria> auditoria(String actor,String tipo,String entidad,String accion,Instant desde,Instant hasta,int pagina,int tamano);
 List<Map<String,String>> actores();List<Map<String,String>> entidades();
 Map<String,Long> cantidadesUsuarios();List<Map<String,Object>> incidencias();
}
