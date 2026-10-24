package co.edu.unicolmayor.janussync.infrastructure.adapter.persistence;
import co.edu.unicolmayor.janussync.domain.port.AuditoriaPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.UUID;
@Repository
public class AuditoriaMySqlRepository implements AuditoriaPort {
 private final JdbcTemplate jdbc;
 public AuditoriaMySqlRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public void registrar(String actor,String accion,String tipo,String id,String nombre,String descripcion){
 String actorNombre="SISTEMA".equals(actor)?"Sistema":actor==null?"Solicitud pública":jdbc.queryForObject("SELECT CONCAT(nombres,' ',apellidos) FROM usuarios WHERE id=?",String.class,actor);
 jdbc.update("INSERT INTO auditoria_administrativa VALUES(?,?,?,?,?,?,?,CURRENT_TIMESTAMP(6),?)",UUID.randomUUID().toString(),"SISTEMA".equals(actor)?null:actor,actorNombre,accion,tipo,id,nombre,descripcion);
 }
}
