package co.edu.unicolmayor.janussync.domain.valueobject;
import co.edu.unicolmayor.janussync.domain.exception.ReglaRolException;
public record ObservacionSolicitud(String valor) {
 public ObservacionSolicitud {
  valor=valor==null?"":valor.strip();
  if(valor.length()<15||valor.length()>4000||valor.split("\\s+").length<3)throw new ReglaRolException("VALIDATION","comentario","Explica qué debe corregirse con al menos tres palabras y 15 caracteres (máximo 4000).");
 }
}
