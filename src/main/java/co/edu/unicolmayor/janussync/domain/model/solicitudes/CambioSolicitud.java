package co.edu.unicolmayor.janussync.domain.model.solicitudes;
import java.util.List;
import co.edu.unicolmayor.janussync.domain.valueobject.*;
import co.edu.unicolmayor.janussync.domain.exception.ReglaRolException;
public record CambioSolicitud(String operacion,Long version,String programaId,String periodoId,String responsableId,List<String> grupos,String nota) {
 public CambioSolicitud {
  try {UsuarioId.desde(operacion);new ProgramaId(programaId);new ReferenciaAcademicaId(periodoId);UsuarioId.desde(responsableId);}
  catch(RuntimeException e){throw new ReglaRolException("VALIDATION",null,"Revisa el programa, período y coordinador responsable.");}
  if(grupos==null||grupos.size()>50||grupos.stream().distinct().count()!=grupos.size())throw new ReglaRolException("VALIDATION","grupos","Selecciona hasta 50 grupos sin repetir.");
  grupos.forEach(ReferenciaAcademicaId::new);grupos=List.copyOf(grupos);
  nota=nota==null?"":nota.strip();if(nota.length()>4000)throw new ReglaRolException("VALIDATION","nota","La nota admite hasta 4000 caracteres.");
 }
}
