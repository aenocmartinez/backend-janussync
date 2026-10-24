package co.edu.unicolmayor.janussync.application.usecase;
import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.domain.port.ProgramaAcademicoRepository;
import co.edu.unicolmayor.janussync.domain.valueobject.ProgramaId;
import co.edu.unicolmayor.janussync.domain.exception.DatoInvalidoException;
import java.util.*;
public final class ConsultarProgramasUseCase {
 private final ProgramaAcademicoRepository programas;
 public ConsultarProgramasUseCase(ProgramaAcademicoRepository programas){this.programas=programas;}
 public Pagina<ProgramaAcademico> buscar(ConsultaProgramas consulta){return programas.buscar(consulta);}
 public List<ProgramaAcademico> resolver(List<String> ids){
  if(ids==null||ids.size()>100)throw new DatoInvalidoException("Consulta hasta 100 programas por vez.");
  var unicos=new HashSet<ProgramaId>();for(String id:ids)unicos.add(new ProgramaId(id));
  if(unicos.size()!=ids.size())throw new DatoInvalidoException("La consulta contiene programas duplicados.");
  return programas.resolver(unicos);
 }
}
