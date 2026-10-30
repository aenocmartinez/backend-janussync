package co.edu.unicolmayor.janussync.domain.port;
import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.domain.model.solicitudes.*;
import java.util.*;
public interface AcademicosSolicitudPort {
 List<OpcionAcademica> periodos(String programa);
 Pagina<GrupoAcademico> grupos(String programa,String periodo,String busqueda,int pagina);
 GrupoAcademico grupo(String programa,String periodo,String grupo,boolean integrantes);
}
