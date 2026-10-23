package co.edu.unicolmayor.janussync.domain.valueobject;
import co.edu.unicolmayor.janussync.domain.exception.ReglaRolException;
public record ContrasenaNueva(String valor) {
 public ContrasenaNueva {
 if(valor==null || valor.length()<10 || valor.length()>128 || !valor.codePoints().anyMatch(Character::isUpperCase) || !valor.codePoints().anyMatch(Character::isLowerCase) || !valor.chars().anyMatch(c->c>='0'&&c<='9'))
 throw new ReglaRolException("VALIDATION","password","Usa entre 10 y 128 caracteres, una mayúscula, una minúscula y un número.");
 }
 @Override public String toString(){return "ContrasenaNueva[protegida]";}
}
