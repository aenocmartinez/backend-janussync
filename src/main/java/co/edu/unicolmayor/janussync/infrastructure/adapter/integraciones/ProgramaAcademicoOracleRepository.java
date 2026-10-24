package co.edu.unicolmayor.janussync.infrastructure.adapter.integraciones;
import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.domain.valueobject.*;
import co.edu.unicolmayor.janussync.domain.port.FuenteProgramasAcademicos;
import co.edu.unicolmayor.janussync.domain.exception.AcademusoftNoDisponibleException;
import java.sql.*;
import java.util.*;
public final class ProgramaAcademicoOracleRepository implements FuenteProgramasAcademicos {
    private static final String CAMPOS="p.PROG_ID,p.PROG_CODIGOPROGRAMA,p.PROG_NOMBRE,p.PROG_ESTADO,m.MODA_DESCRIPCION,t.METO_DESCRIPCION,j.JORN_DESCRIPCION";
    private static final String FUENTE=" FROM ACADEMICO.PROGRAMA p LEFT JOIN ACADEMICO.MODALIDAD m ON m.MODA_ID=p.MODA_ID LEFT JOIN ACADEMICO.METODOLOGIA t ON t.METO_ID=p.METO_ID LEFT JOIN ACADEMICO.JORNADA j ON j.JORN_ID=p.JORN_ID";
    private final ConexionOracle conexion;
    private final int segundos;
    public ProgramaAcademicoOracleRepository(ConexionOracle conexion,TiemposConexion tiempos) {
        this.conexion=conexion;this.segundos=(int)Math.max(1,tiempos.ejecucion().toSeconds());
    }
    private PreparedStatement preparar(Connection c,String sql,int limite) throws SQLException {
        var s=c.prepareStatement(sql);s.setQueryTimeout(segundos);s.setMaxRows(limite);s.setFetchSize(Math.min(limite,50));return s;
    }
    public Pagina<ProgramaAcademico> buscar(ConsultaProgramas q) {
        String filtro=" WHERE p.PROG_ESTADO='1' AND (UPPER(p.PROG_NOMBRE) LIKE UPPER(?) ESCAPE '!' OR UPPER(p.PROG_CODIGOPROGRAMA) LIKE UPPER(?) ESCAPE '!')";
        String texto="%"+q.busqueda().replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        try(var c=conexion.abrir()) {
            long total;
            try(var s=preparar(c,"SELECT COUNT(*)"+FUENTE+filtro,1)) {
                s.setString(1,texto);s.setString(2,texto);try(var r=s.executeQuery()){r.next();total=r.getLong(1);}
            }
            var lista=new ArrayList<ProgramaAcademico>();
            try(var s=preparar(c,"SELECT * FROM (SELECT "+CAMPOS+",ROW_NUMBER() OVER (ORDER BY UPPER(p.PROG_NOMBRE),p.PROG_ID) posicion"+FUENTE+filtro+") WHERE posicion BETWEEN ? AND ? ORDER BY posicion",q.tamano())) {
                s.setString(1,texto);s.setString(2,texto);s.setInt(3,(q.pagina()-1)*q.tamano()+1);s.setInt(4,q.pagina()*q.tamano());
                try(var r=s.executeQuery()){while(r.next())lista.add(mapear(r));}
            }
            c.rollback();return new Pagina<>(lista,q.pagina(),q.tamano(),total);
        } catch(SQLException e){throw new AcademusoftNoDisponibleException(e);}
    }
    public List<ProgramaAcademico> resolver(Set<ProgramaId> ids) {
        if(ids.isEmpty())return List.of();
        if(ids.size()>100)throw new IllegalArgumentException("Consulta hasta 100 programas por vez.");
        var ordenados=ids.stream().map(ProgramaId::valor).sorted().toList();
        try(var c=conexion.abrir();var s=preparar(c,"SELECT "+CAMPOS+FUENTE+" WHERE TO_CHAR(p.PROG_ID,'TM9') IN ("+String.join(",",Collections.nCopies(ids.size(),"?"))+") ORDER BY p.PROG_ID",ids.size())) {
            for(int i=0;i<ordenados.size();i++)s.setString(i+1,ordenados.get(i));
            var lista=new ArrayList<ProgramaAcademico>();try(var r=s.executeQuery()){while(r.next())lista.add(mapear(r));}
            c.rollback();return List.copyOf(lista);
        } catch(SQLException e){throw new AcademusoftNoDisponibleException(e);}
    }
    public LecturaProgramas leerCatalogo() {
        long limite=System.nanoTime()+java.time.Duration.ofSeconds(60).toNanos();
        try(var c=conexion.abrir()) {
            long esperado;
            try(var s=preparar(c,"SELECT COUNT(*) FROM ACADEMICO.PROGRAMA",1);var r=s.executeQuery()) {
                if(!r.next())throw new SQLException("Lectura sin total.");esperado=r.getLong(1);
            }
            if(esperado<1 || esperado>10000)throw new SQLException("Volumen de catálogo inesperado.");
            var datos=new ArrayList<ProgramaAcademico>();
            try(var s=preparar(c,"SELECT "+CAMPOS+FUENTE+" ORDER BY p.PROG_ID",10001);var r=s.executeQuery()) {
                while(true) {
                    if(System.nanoTime()>limite)throw new SQLTimeoutException("La lectura completa agotó su límite.");
                    if(!r.next())break;
                    datos.add(mapear(r));
                    if(datos.size()>10000)throw new SQLException("Catálogo excede el límite.");
                }
            }
            c.rollback();return new LecturaProgramas(datos,esperado,datos.size()==esperado);
        } catch(SQLException e) { throw new AcademusoftNoDisponibleException(e); }
    }
    private ProgramaAcademico mapear(ResultSet r)throws SQLException {
        return new ProgramaAcademico(new ProgramaId(r.getString("PROG_ID")),r.getString("PROG_CODIGOPROGRAMA"),r.getString("PROG_NOMBRE"),"1".equals(r.getString("PROG_ESTADO")),r.getString("MODA_DESCRIPCION"),r.getString("METO_DESCRIPCION"),r.getString("JORN_DESCRIPCION"),r.getString("PROG_ESTADO"),true);
    }
}
