package co.edu.unicolmayor.janussync.domain.valueobject;
import co.edu.unicolmayor.janussync.domain.exception.DatoInvalidoException;
public record ReferenciaAcademicaId(String valor) {
 public ReferenciaAcademicaId { if(valor==null||valor.isBlank()||valor.length()>80||!valor.equals(valor.strip())||valor.chars().anyMatch(Character::isISOControl))throw new DatoInvalidoException("La referencia académica no es válida."); }
}
