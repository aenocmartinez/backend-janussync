package co.edu.unicolmayor.janussync.domain;
import co.edu.unicolmayor.janussync.domain.valueobject.ProgramaId;
import co.edu.unicolmayor.janussync.domain.model.ConsultaProgramas;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ProgramasTest {
 @Test void conservaIdentidadInstitucionalSinConversion(){
  assertThat(new ProgramaId("001").valor()).isEqualTo("001");
  assertThat(new ProgramaId("001")).isNotEqualTo(new ProgramaId("1"));
 }
 @Test void rechazaIdentidadVaciaExtensaOControl(){
  for(String id:new String[]{null,""," "," 001","001\n","x".repeat(81)})
   assertThatThrownBy(()->new ProgramaId(id)).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void acotaBusquedaYPaginacion(){
  assertThat(new ConsultaProgramas(" 303 ",1,20).busqueda()).isEqualTo("303");
  assertThatThrownBy(()->new ConsultaProgramas("x".repeat(101),1,20)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new ConsultaProgramas("",0,20)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new ConsultaProgramas("",1,51)).isInstanceOf(IllegalArgumentException.class);
 }
}
