package co.edu.unicolmayor.janussync.infrastructure.adapter.web;
import co.edu.unicolmayor.janussync.domain.model.solicitudes.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class GruposSolicitudPresentacionTest {
 private GrupoAcademico grupo(Boolean consultado){return new GrupoAcademico("g","m","Asignatura","GRUPO A","Sede","p","t",List.of(),List.of(),consultado);}
 @Test void noConsultadoNoEsCero(){var dto=SolicitudesController.GrupoDTO.desde(grupo(false));assertThat(dto.totalDocentes()).isNull();assertThat(dto.totalEstudiantes()).isNull();}
 @Test void consultaVaciaSiEsCero(){var dto=SolicitudesController.GrupoDTO.desde(grupo(true));assertThat(dto.totalDocentes()).isZero();assertThat(dto.totalEstudiantes()).isZero();}
 @Test void revisionAnteriorSinIndicadorConservaDatosSinInventarCero(){
 var mapper=new tools.jackson.databind.ObjectMapper();
 var anterior=mapper.readValue("{\"id\":\"g\",\"asignaturaId\":\"m\",\"asignatura\":\"Materia\",\"nombre\":\"A\",\"sede\":null,\"programaId\":\"p\",\"periodoId\":\"t\",\"docentes\":[],\"estudiantes\":[]}",GrupoAcademico.class);
 assertThat(anterior.integrantesConsultados()).isNull();assertThat(SolicitudesController.GrupoDTO.desde(anterior).totalEstudiantes()).isNull();
 var conDatos=new GrupoAcademico("g","m","Materia","A",null,"p","t",List.of(),List.of(new PersonaAcademica("e","Persona de prueba")));
 assertThat(SolicitudesController.GrupoDTO.desde(conDatos).totalEstudiantes()).isEqualTo(1);
 }
}
