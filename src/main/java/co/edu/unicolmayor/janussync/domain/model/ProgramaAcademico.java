package co.edu.unicolmayor.janussync.domain.model;
import co.edu.unicolmayor.janussync.domain.valueobject.ProgramaId;
public record ProgramaAcademico(ProgramaId id, String codigo, String nombre, boolean activo,
                                String modalidad, String metodologia, String jornada,
                                String estadoOficial, boolean presenteEnFuente) {
    public ProgramaAcademico(ProgramaId id,String codigo,String nombre,boolean activo,String modalidad,String metodologia,String jornada) {
        this(id,codigo,nombre,activo,modalidad,metodologia,jornada,null,true);
    }
    public boolean elegible() { return activo && presenteEnFuente; }
}
