package co.edu.unicolmayor.janussync.infrastructure.adapter.integraciones;
import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.domain.model.solicitudes.*;
import co.edu.unicolmayor.janussync.domain.port.AcademicosSolicitudPort;
import co.edu.unicolmayor.janussync.domain.exception.*;
import co.edu.unicolmayor.janussync.domain.valueobject.ReferenciaAcademicaId;
import java.util.*;import java.sql.*;
public final class AcademicosSolicitudOracleAdapter implements AcademicosSolicitudPort {
 private final ConexionOracle conexion;
 private static final String BASE=" FROM ACADEMICO.GRUPO g JOIN ACADEMICO.MATERIA m ON m.MATE_CODIGOMATERIA=g.MATE_CODIGOMATERIA LEFT JOIN ACADEMICO.UNIDAD u ON u.UNID_ID=g.UNID_IDREGIONAL WHERE g.GRUP_ACTIVO='1' AND TO_CHAR(g.PEUN_ID,'TM9')=? AND EXISTS (SELECT 1 FROM ACADEMICO.PENSUMMATERIAGRUPO pm JOIN ACADEMICO.PENSUM p ON p.PENS_ID=pm.PENS_ID WHERE pm.GRUP_ID=g.GRUP_ID AND pm.MATE_CODIGOMATERIA=g.MATE_CODIGOMATERIA AND TO_CHAR(p.PROG_ID,'TM9')=?)";
 private static final String CAMPOS="TO_CHAR(g.GRUP_ID,'TM9') id,g.GRUP_NOMBRE,g.MATE_CODIGOMATERIA,m.MATE_NOMBRE,u.UNID_NOMBRE";
 private static final String NOMBRE="TRIM(pn.PENG_PRIMERNOMBRE||' '||pn.PENG_SEGUNDONOMBRE||' '||pn.PENG_PRIMERAPELLIDO||' '||pn.PENG_SEGUNDOAPELLIDO)";
 public AcademicosSolicitudOracleAdapter(ConexionOracle conexion){this.conexion=conexion;}
 private PreparedStatement consulta(Connection c,String sql,int max,Object...args)throws SQLException{var s=c.prepareStatement(sql);s.setQueryTimeout(10);s.setMaxRows(max);s.setFetchSize(Math.min(100,max));for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);return s;}
 private ReglaRolException invalido(String campo,String msg){return new ReglaRolException("VALIDATION",campo,msg);}
 public List<OpcionAcademica> periodos(String programa){
  new ReferenciaAcademicaId(programa);
  try(var c=conexion.abrir();var s=consulta(c,"SELECT DISTINCT TO_CHAR(pu.PEUN_ID,'TM9') id,pu.PEUN_ANO,pu.PEUN_PERIODO,t.TPPA_DESCRIPCION FROM ACADEMICO.PERIODOUNIVERSIDAD pu JOIN ACADEMICO.TIPOPERIODOACADEMICO t ON t.TPPA_ID=pu.TPPA_ID WHERE EXISTS (SELECT 1 FROM ACADEMICO.PERIODOPROGRAMAUNIDAD pp JOIN ACADEMICO.UNIDADPROGRAMA up ON up.UNPR_ID=pp.UNPR_ID WHERE pp.PEUN_ID=pu.PEUN_ID AND TO_CHAR(up.PROG_ID,'TM9')=?) ORDER BY pu.PEUN_ANO DESC,pu.PEUN_PERIODO DESC,id DESC",501,programa);var r=s.executeQuery()){
   var lista=new ArrayList<OpcionAcademica>();while(r.next())lista.add(new OpcionAcademica(r.getString("id"),r.getString(2)+"-"+r.getString(3)+" · "+r.getString(4)));
   if(lista.size()>500)throw new SQLException("Catálogo de períodos excede su límite.");c.rollback();return List.copyOf(lista);
  }catch(SQLException e){throw new InformacionAcademicaNoDisponibleException(e);}
 }
 private void periodo(String programa,String periodo){new ReferenciaAcademicaId(periodo);if(periodos(programa).stream().noneMatch(p->p.id().equals(periodo)))throw invalido("periodoId","El período no pertenece al programa.");}
 public Pagina<GrupoAcademico> grupos(String programa,String periodo,String q,int pagina){
  periodo(programa,periodo);
  String filtro=" AND (UPPER(m.MATE_NOMBRE) LIKE UPPER(?) ESCAPE '!' OR UPPER(g.MATE_CODIGOMATERIA) LIKE UPPER(?) ESCAPE '!' OR UPPER(g.GRUP_NOMBRE) LIKE UPPER(?) ESCAPE '!')";
  String texto="%"+q.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
  try(var c=conexion.abrir()){
   long total;try(var s=consulta(c,"SELECT COUNT(*)"+BASE+filtro,1,periodo,programa,texto,texto,texto);var r=s.executeQuery()){r.next();total=r.getLong(1);}
   var lista=new ArrayList<GrupoAcademico>();try(var s=consulta(c,"SELECT * FROM (SELECT "+CAMPOS+",ROW_NUMBER() OVER(ORDER BY m.MATE_NOMBRE,g.GRUP_NOMBRE,g.GRUP_ID) posicion"+BASE+filtro+") WHERE posicion BETWEEN ? AND ? ORDER BY posicion",20,periodo,programa,texto,texto,texto,(pagina-1)*20+1,pagina*20);var r=s.executeQuery()){while(r.next())lista.add(mapear(r,programa,periodo,List.of(),List.of()));}
   c.rollback();return new Pagina<>(lista,pagina,20,total);
  }catch(SQLException e){throw new InformacionAcademicaNoDisponibleException(e);}
 }
 private GrupoAcademico mapear(ResultSet r,String programa,String periodo,List<PersonaAcademica> docentes,List<PersonaAcademica> estudiantes)throws SQLException{
  String materia=r.getString("MATE_NOMBRE"),nombre=r.getString("GRUP_NOMBRE"),codigo=r.getString("MATE_CODIGOMATERIA");
  if(materia==null||materia.isBlank()||nombre==null||nombre.isBlank()||codigo==null||codigo.isBlank())throw new SQLException("Grupo incompleto.");
  return new GrupoAcademico(r.getString("id"),codigo,materia,nombre,r.getString("UNID_NOMBRE"),programa,periodo,docentes,estudiantes,false);
 }
 public GrupoAcademico grupo(String programa,String periodo,String grupo,boolean integrantes){
  new ReferenciaAcademicaId(programa);new ReferenciaAcademicaId(periodo);new ReferenciaAcademicaId(grupo);
  try(var c=conexion.abrir();var s=consulta(c,"SELECT "+CAMPOS+BASE+" AND TO_CHAR(g.GRUP_ID,'TM9')=?",2,periodo,programa,grupo);var r=s.executeQuery()){
   if(!r.next())throw invalido("grupos","Un grupo no existe, está inactivo o no pertenece al programa y período.");
   var dato=mapear(r,programa,periodo,List.of(),List.of());if(r.next())throw new SQLException("Grupo ambiguo.");
   if(integrantes){
    var docentes=personas(c,"SELECT DISTINCT TO_CHAR(du.PEGE_ID,'TM9') id,"+NOMBRE+" nombre FROM ACADEMICO.DOCENTEGRUPO dg LEFT JOIN ACADEMICO.DOCENTEUNIDAD du ON du.DOUN_ID=dg.DOUN_ID LEFT JOIN GENERAL.PERSONANATURALGENERAL pn ON pn.PEGE_ID=du.PEGE_ID WHERE TO_CHAR(dg.GRUP_ID,'TM9')=? ORDER BY id",grupo,null);
    var estudiantes=personas(c,"SELECT DISTINCT TO_CHAR(ep.ESTP_ID,'TM9') id,"+NOMBRE+" nombre,TO_CHAR(ma.PEUN_ID,'TM9') periodo FROM ACADEMICO.GRUPOMATRICULADO gm LEFT JOIN ACADEMICO.MATRICULAACADEMICA ma ON ma.MAAC_ID=gm.MAAC_ID LEFT JOIN ACADEMICO.ESTUDIANTEPENSUM ep ON ep.ESTP_ID=ma.ESTP_ID LEFT JOIN GENERAL.PERSONANATURALGENERAL pn ON pn.PEGE_ID=ep.PEGE_ID WHERE gm.GRMA_ESTADO='1' AND TO_CHAR(gm.GRUP_ID,'TM9')=? ORDER BY id",grupo,periodo);
    dato=new GrupoAcademico(dato.id(),dato.asignaturaId(),dato.asignatura(),dato.nombre(),dato.sede(),programa,periodo,docentes,estudiantes,true);
   }
   c.rollback();return dato;
  }catch(SQLException e){throw new InformacionAcademicaNoDisponibleException(e);}
 }
 private List<PersonaAcademica> personas(Connection c,String sql,String grupo,String periodo)throws SQLException{
  try(var s=consulta(c,sql,5001,grupo);var r=s.executeQuery()){
   var lista=new ArrayList<PersonaAcademica>();while(r.next()){
    String id=r.getString("id"),nombre=r.getString("nombre");
    if(id==null||nombre==null||nombre.isBlank()||periodo!=null&&!periodo.equals(r.getString("periodo")))throw new SQLException("Relación académica incompleta.");
    lista.add(new PersonaAcademica(id,nombre.replaceAll("\\s+"," ")));
   }
   if(lista.size()>5000)throw new SQLException("La lista de integrantes excede el límite seguro.");return List.copyOf(lista);
  }
 }
}
