package co.edu.unicolmayor.janussync.infrastructure.adapter.persistence;
import co.edu.unicolmayor.janussync.domain.port.SolicitudesRepository;
import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.domain.model.solicitudes.*;
import co.edu.unicolmayor.janussync.domain.exception.ReglaRolException;
import java.util.*;import java.sql.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;
@Repository
public class SolicitudesMySqlRepository implements SolicitudesRepository {
 private final JdbcTemplate jdbc;private final ObjectMapper json;
 public SolicitudesMySqlRepository(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
 private String serializar(Object v){return json.writeValueAsString(v);}
 private ContenidoSolicitud contenido(String v){return v==null?null:json.readValue(v,ContenidoSolicitud.class);}
 private Solicitud mapear(ResultSet r,int n)throws SQLException{return new Solicitud(r.getString("id"),r.getLong("numero"),r.getString("creador_id"),r.getString("responsable_id"),EstadoSolicitud.valueOf(r.getString("estado")),r.getLong("version"),contenido(r.getString("contenido")),r.getTimestamp("creada_en").toInstant(),r.getTimestamp("actualizada_en").toInstant());}
 public Optional<Solicitud> obtener(String id){return jdbc.query("SELECT * FROM solicitudes_integracion WHERE id=?",this::mapear,id).stream().findFirst();}
 private String alcance(AccesoSolicitudes a,List<Object> valores,boolean pendiente){
  String local="FALSE";
  if((a.prepara()||a.envia())&&!a.programas().isEmpty()){
   var ids=a.programas().stream().sorted().toList();valores.addAll(ids);
   local="programa_id IN ("+String.join(",",Collections.nCopies(ids.size(),"?"))+")"+(pendiente?" AND estado='CON_OBSERVACIONES'":"");
  }
  String global=a.atiende()?(pendiente?"estado='ENVIADA'":"estado<>'BORRADOR'"):"FALSE";
  return "(("+local+") OR ("+global+"))";
 }
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Pagina<Solicitud> listar(AccesoSolicitudes a,String busqueda,String estado,int pagina){
  var v=new ArrayList<Object>();String where=" WHERE "+alcance(a,v,false);
  if(!estado.isEmpty()){where+=" AND estado=?";v.add(estado);}
  if(!busqueda.isEmpty()){where+=" AND (CAST(numero AS CHAR) LIKE ? OR JSON_UNQUOTE(JSON_EXTRACT(contenido,'$.programa')) COLLATE utf8mb4_0900_ai_ci LIKE ?)";String q="%"+busqueda.replace("!","!!").replace("%","!%").replace("_","!_")+"%";where=where.replace("LIKE ?","LIKE ? ESCAPE '!'");v.add(q);v.add(q);}
  long total=Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM solicitudes_integracion"+where,Long.class,v.toArray()));
  v.add((pagina-1)*20);
  return new Pagina<>(jdbc.query("SELECT * FROM solicitudes_integracion"+where+" ORDER BY actualizada_en DESC,numero DESC LIMIT 20 OFFSET ?",this::mapear,v.toArray()),pagina,20,total);
 }
 public long pendientes(AccesoSolicitudes a){var v=new ArrayList<Object>();return Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM solicitudes_integracion WHERE "+alcance(a,v,true),Long.class,v.toArray()));}
 public void crear(Solicitud s){jdbc.update("INSERT INTO solicitudes_integracion(id,creador_id,responsable_id,programa_id,estado,version,contenido,creada_en,actualizada_en) VALUES(?,?,?,?,?,?,?,?,?)",s.id(),s.creadorId(),s.responsableId(),s.contenido().programaId(),s.estado().name(),s.version(),serializar(s.contenido()),Timestamp.from(s.creadaEn()),Timestamp.from(s.actualizadaEn()));}
 public void guardar(Solicitud s,long anterior){if(jdbc.update("UPDATE solicitudes_integracion SET responsable_id=?,estado=?,version=?,contenido=?,actualizada_en=? WHERE id=? AND version=?",s.responsableId(),s.estado().name(),s.version(),serializar(s.contenido()),Timestamp.from(s.actualizadaEn()),s.id(),anterior)!=1)throw conflicto();}
 private ReglaRolException conflicto(){return new ReglaRolException("CONFLICT",null,"La solicitud cambió. Conserva tus cambios y vuelve a consultar su versión actual.");}
 public Optional<String> repeticion(String operacion,String actor,String accion,String huella){
  return jdbc.query("SELECT * FROM comandos_solicitudes WHERE operacion=?",(r,n)->{
   if(!actor.equals(r.getString("actor"))||!accion.equals(r.getString("accion"))||!huella.equals(r.getString("huella")))throw conflicto();return r.getString("solicitud");},operacion).stream().findFirst();
 }
 public void comando(String op,String actor,String accion,String huella,String id){jdbc.update("INSERT INTO comandos_solicitudes VALUES(?,?,?,?,?)",op,actor,accion,huella,id);}
 public void evento(String id,Solicitud s,String actor,String accion,String comentario,boolean incluir){jdbc.update("INSERT INTO seguimiento_solicitudes(id,solicitud,version,accion,actor_nombre,comentario,contenido,instante) SELECT ?,?,?,?,?,?,?,? FROM usuarios WHERE id=?",id,s.id(),s.version(),accion,nombre(actor),comentario,incluir?serializar(s.contenido()):null,Timestamp.from(s.actualizadaEn()),actor);}
 private String nombre(String actor){return jdbc.queryForObject("SELECT CONCAT(nombres,' ',apellidos) FROM usuarios WHERE id=?",String.class,actor);}
 public List<SeguimientoSolicitud> seguimiento(String id){return jdbc.query("SELECT * FROM seguimiento_solicitudes WHERE solicitud=? ORDER BY version",(r,n)->new SeguimientoSolicitud(r.getString("id"),r.getString("accion"),r.getString("actor_nombre"),r.getString("comentario"),contenido(r.getString("contenido")),r.getTimestamp("instante").toInstant()),id);}
 public void prepararAviso(String evento,String solicitud,String destinatario){jdbc.update("INSERT INTO avisos_solicitudes(evento,solicitud,destinatario,resultado) VALUES(?,?,?,?)",evento,solicitud,destinatario,destinatario.isEmpty()?"SIN_DESTINATARIOS":"PREPARADO");}
 public boolean reclamarAviso(String evento,String destinatario){return jdbc.update("UPDATE avisos_solicitudes SET resultado='EN_CURSO',actualizado_en=UTC_TIMESTAMP(6) WHERE evento=? AND destinatario=? AND resultado='PREPARADO'",evento,destinatario)==1;}
 public void resultadoAviso(String evento,String destinatario,String resultado){jdbc.update("UPDATE avisos_solicitudes SET resultado=?,actualizado_en=UTC_TIMESTAMP(6) WHERE evento=? AND destinatario=? AND resultado='EN_CURSO'",resultado,evento,destinatario);}
 public List<Map<String,String>> avisos(String id){return jdbc.query("SELECT evento,resultado FROM avisos_solicitudes WHERE solicitud=?",(r,n)->Map.of("evento",r.getString(1),"resultado",r.getString(2).equals("EN_CURSO")?"INCIERTO":r.getString(2)),id);}
}
