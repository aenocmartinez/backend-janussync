package co.edu.unicolmayor.janussync.domain.port;
import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.domain.model.solicitudes.*;
import java.time.Instant;
import java.util.*;
public interface SolicitudesRepository {
 Optional<Solicitud> obtener(String id);
 Pagina<Solicitud> listar(AccesoSolicitudes acceso,String busqueda,String estado,int pagina);
 long pendientes(AccesoSolicitudes acceso);
 void crear(Solicitud solicitud);
 void guardar(Solicitud solicitud,long versionAnterior);
 Optional<String> repeticion(String operacion,String actor,String accion,String huella);
 void comando(String operacion,String actor,String accion,String huella,String solicitud);
 void evento(String id,Solicitud solicitud,String actor,String accion,String comentario,boolean contenido);
 List<SeguimientoSolicitud> seguimiento(String id);
 void prepararAviso(String evento,String solicitud,String destinatario);
 boolean reclamarAviso(String evento,String destinatario);
 void resultadoAviso(String evento,String destinatario,String resultado);
 List<Map<String,String>> avisos(String solicitud);
}
