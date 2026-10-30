package co.edu.unicolmayor.janussync.domain.model.solicitudes;
import java.util.Set;
public record AccesoSolicitudes(String actor,Set<String> permisos,Set<String> programas) {
 public boolean prepara(){return permisos.contains("SOLICITUDES_PREPARAR");}
 public boolean envia(){return permisos.contains("SOLICITUDES_ENVIAR");}
 public boolean atiende(){return permisos.contains("SOLICITUDES_ATENDER");}
 public boolean academico(String programa){return (prepara()||envia())&&programas.contains(programa);}
 public boolean ve(Solicitud s){return academico(s.contenido().programaId())||atiende()&&s.estado()!=EstadoSolicitud.BORRADOR;}
}
