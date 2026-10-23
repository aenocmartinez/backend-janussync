package co.edu.unicolmayor.janussync.domain.valueobject;
import co.edu.unicolmayor.janussync.domain.exception.DatoInvalidoException;
public record Cargo(String valor) {
 public Cargo { if(valor!=null) { valor=valor.strip();if(valor.isEmpty())valor=null;
 if(valor!=null && (valor.codePointCount(0,valor.length())>150 || valor.codePoints().anyMatch(Character::isISOControl)))throw new DatoInvalidoException("El cargo admite hasta 150 caracteres sin caracteres de control."); } }
}
