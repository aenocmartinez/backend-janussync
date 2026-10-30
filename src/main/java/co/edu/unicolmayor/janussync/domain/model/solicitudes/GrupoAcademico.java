package co.edu.unicolmayor.janussync.domain.model.solicitudes;
import java.util.List;
public record GrupoAcademico(String id,String asignaturaId,String asignatura,String nombre,String sede,String programaId,String periodoId,List<PersonaAcademica> docentes,List<PersonaAcademica> estudiantes,Boolean integrantesConsultados) {
 public GrupoAcademico(String id,String asignaturaId,String asignatura,String nombre,String sede,String programaId,String periodoId,List<PersonaAcademica> docentes,List<PersonaAcademica> estudiantes){this(id,asignaturaId,asignatura,nombre,sede,programaId,periodoId,docentes,estudiantes,null);}
 public boolean tieneCantidades(){return Boolean.TRUE.equals(integrantesConsultados)||!docentes.isEmpty()||!estudiantes.isEmpty();}
 public GrupoAcademico {docentes=List.copyOf(docentes);estudiantes=List.copyOf(estudiantes);}
}
