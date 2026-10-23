package co.edu.unicolmayor.janussync.infrastructure.adapter.persistence;
import co.edu.unicolmayor.janussync.domain.port.ConsultaAdministrativaRepository;
import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.domain.exception.DatoInvalidoException;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class ConsultaAdministrativaMySqlRepository implements ConsultaAdministrativaRepository {
 private final JdbcTemplate jdbc;public ConsultaAdministrativaMySqlRepository(JdbcTemplate j){jdbc=j;}
 public Pagina<EventoAuditoria> auditoria(String actor,String tipo,String entidad,String accion,Instant desde,Instant hasta,int pagina,int tamano){
 if(pagina<1||tamano<1||tamano>100 || (long)pagina*tamano>Integer.MAX_VALUE || desde!=null&&hasta!=null&&desde.isAfter(hasta))throw new DatoInvalidoException("La consulta del historial no es válida.");
 StringBuilder filtro=new StringBuilder(" WHERE 1=1");List<Object> args=new ArrayList<>();
 String[] campos={"actor_id","entidad_tipo","entidad_id","accion"};String[] valores={actor,tipo,entidad,accion};
 for(int i=0;i<campos.length;i++)if(valores[i]!=null&&!valores[i].isBlank()){filtro.append(" AND "+campos[i]+"=?");args.add(valores[i]);}
 if(desde!=null){filtro.append(" AND instante>=?");args.add(Timestamp.from(desde));}if(hasta!=null){filtro.append(" AND instante<?");args.add(Timestamp.from(hasta));}
 long total=jdbc.queryForObject("SELECT COUNT(*) FROM auditoria_administrativa"+filtro,Long.class,args.toArray());
 args.add(tamano);args.add((pagina-1)*tamano);
 var elementos=jdbc.query("SELECT * FROM auditoria_administrativa"+filtro+" ORDER BY instante DESC,id DESC LIMIT ? OFFSET ?",(r,n)->new EventoAuditoria(r.getString("id"),r.getString("actor_id"),r.getString("actor_nombre"),r.getString("accion"),r.getString("entidad_tipo"),r.getString("entidad_id"),r.getString("entidad_nombre"),r.getTimestamp("instante").toInstant(),r.getString("descripcion")),args.toArray());
 return new Pagina<>(elementos,pagina,tamano,total);
 }
 public List<Map<String,String>> actores(){return jdbc.query("SELECT actor_id,MAX(actor_nombre) nombre FROM auditoria_administrativa WHERE actor_id IS NOT NULL GROUP BY actor_id ORDER BY nombre",(r,n)->Map.of("id",r.getString(1),"name",r.getString(2)));}
 public List<Map<String,String>> entidades(){return jdbc.query("SELECT entidad_id,entidad_tipo,MAX(entidad_nombre) nombre FROM auditoria_administrativa GROUP BY entidad_id,entidad_tipo ORDER BY nombre",(r,n)->Map.of("id",r.getString(1),"type",r.getString(2),"name",r.getString(3)));}
 public Map<String,Long> cantidadesUsuarios(){return jdbc.queryForObject("SELECT COUNT(*) total,COALESCE(SUM(estado='ACTIVO'),0) activos,COALESCE(SUM(estado='INACTIVO'),0) inactivos FROM usuarios",(r,n)->Map.of("total",r.getLong(1),"active",r.getLong(2),"inactive",r.getLong(3)));}
 public List<Map<String,Object>> incidencias(){return jdbc.query("""
 SELECT u.id,CONCAT(u.nombres,' ',u.apellidos) nombre,e.estado,e.solicitado_en,e.vence_en
 FROM usuarios u JOIN envios_acceso e ON e.id=(SELECT id FROM envios_acceso WHERE usuario_id=u.id AND correo=u.correo ORDER BY solicitado_en DESC,id DESC LIMIT 1)
 WHERE u.estado='ACTIVO' AND NOT EXISTS(SELECT 1 FROM credenciales c WHERE c.usuario_id=u.id)
 AND (e.estado='FALLIDO' OR (e.estado='ENVIADO' AND e.vence_en<CURRENT_TIMESTAMP(6)))
 """,(r,n)->Map.of("userId",r.getString(1),"name",r.getString(2),"kind",r.getString(3).equals("ENVIADO")?"INVITACION_VENCIDA":"ENVIO_FALLIDO","since",r.getTimestamp(r.getString(3).equals("ENVIADO")?5:4).toInstant()));}
}
