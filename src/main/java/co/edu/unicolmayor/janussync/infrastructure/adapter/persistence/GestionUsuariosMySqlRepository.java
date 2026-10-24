package co.edu.unicolmayor.janussync.infrastructure.adapter.persistence;
import co.edu.unicolmayor.janussync.domain.port.*;
import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.domain.exception.ReglaRolException;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DuplicateKeyException;
@Repository
public class GestionUsuariosMySqlRepository implements GestionUsuariosRepository {
 private final JdbcTemplate jdbc; private final TransactionTemplate tx;
 public GestionUsuariosMySqlRepository(JdbcTemplate jdbc,PlatformTransactionManager manager) {
 this.jdbc=jdbc;tx=new TransactionTemplate(manager);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
 }
 public <T>T enTransaccion(Supplier<T> tarea) {
 try {return tx.execute(s->{jdbc.queryForObject("SELECT id FROM bloqueo_administracion WHERE id=1 FOR UPDATE",Integer.class);return tarea.get();});}
 catch(DuplicateKeyException e){throw new ReglaRolException("DUPLICATE_EMAIL","email","Ya existe una cuenta con este correo.");}
 }
 private Timestamp fecha(Instant i){return Timestamp.from(i);}
 public boolean existeCorreo(String correo,String excepto){return jdbc.queryForObject("SELECT COUNT(*) FROM usuarios WHERE correo=? AND id<>?",Integer.class,correo,excepto)>0;}
 public void crear(String id,DatosUsuario d,EstadoRegistro e,Set<String> roles){
 jdbc.update("INSERT INTO usuarios(id,nombres,apellidos,correo,cargo,estado) VALUES(?,?,?,?,?,?)",id,d.nombres().valor(),d.apellidos().valor(),d.correo().valor(),d.cargo().valor(),e.name());asignar(id,roles);
 }
 public void editar(String id,DatosUsuario d){jdbc.update("UPDATE usuarios SET nombres=?,apellidos=?,correo=?,cargo=?,actualizado_en=CURRENT_TIMESTAMP(6) WHERE id=?",d.nombres().valor(),d.apellidos().valor(),d.correo().valor(),d.cargo().valor(),id);}
 public void asignar(String id,Set<String> roles){jdbc.update("DELETE FROM usuarios_roles WHERE usuario_id=?",id);roles.stream().sorted().forEach(r->jdbc.update("INSERT INTO usuarios_roles VALUES(?,?)",id,r));jdbc.update("UPDATE usuarios SET actualizado_en=CURRENT_TIMESTAMP(6),version_asignacion=version_asignacion+1 WHERE id=?",id);}
 public void guardarProgramas(String id,Set<String> programas){
 jdbc.update("DELETE FROM usuarios_programas WHERE usuario_id=?",id);
 programas.stream().sorted().forEach(p->jdbc.update("INSERT INTO usuarios_programas(usuario_id,programa_id) VALUES(?,?)",id,p));
 }
 public void estado(String id,EstadoRegistro e){jdbc.update("UPDATE usuarios SET estado=?,actualizado_en=CURRENT_TIMESTAMP(6) WHERE id=?",e.name(),id);}
 public void revocarSesiones(String id,Instant ahora){jdbc.update("UPDATE sesiones_autenticacion SET revocada_en=COALESCE(revocada_en,?) WHERE usuario_id=?",fecha(ahora),id);}
 public void invalidarEnlaces(String id,Instant ahora){jdbc.update("UPDATE enlaces_contrasena SET invalidado_en=COALESCE(invalidado_en,?) WHERE usuario_id=? AND utilizado_en IS NULL",fecha(ahora),id);}
 public Optional<String> hashContrasena(String id){return jdbc.queryForList("SELECT hash_contrasena FROM credenciales WHERE usuario_id=?",String.class,id).stream().findFirst();}
 public void establecerContrasena(String id,String hash,Instant ahora){jdbc.update("INSERT INTO credenciales VALUES(?,?,?) ON DUPLICATE KEY UPDATE hash_contrasena=?,actualizado_en=?",id,hash,fecha(ahora),hash,fecha(ahora));}
 public Optional<String> usuarioPorCorreo(String correo){return jdbc.queryForList("SELECT id FROM usuarios WHERE correo=?",String.class,correo).stream().findFirst();}
 public void guardarEnlace(EnlaceContrasena e,String hash,Instant ahora){jdbc.update("INSERT INTO enlaces_contrasena(id,hash_token,usuario_id,proposito,correo,creado_en,vence_en) VALUES(?,?,?,?,?,?,?)",e.id(),hash,e.usuarioId(),e.proposito(),e.correo(),fecha(ahora),fecha(e.venceEn()));}
 public Optional<EnlaceContrasena> enlace(String hash,boolean bloquear){return jdbc.query("SELECT * FROM enlaces_contrasena WHERE hash_token=?"+(bloquear?" FOR UPDATE":""),(r,n)->new EnlaceContrasena(r.getString("id"),r.getString("usuario_id"),r.getString("proposito"),r.getString("correo"),r.getTimestamp("vence_en").toInstant(),r.getTimestamp("utilizado_en")==null?null:r.getTimestamp("utilizado_en").toInstant(),r.getTimestamp("invalidado_en")==null?null:r.getTimestamp("invalidado_en").toInstant()),hash).stream().findFirst();}
 public void consumirEnlace(String id,Instant ahora){jdbc.update("UPDATE enlaces_contrasena SET utilizado_en=? WHERE id=?",fecha(ahora),id);}
 public void prepararEnvio(String id,EnlaceContrasena e,String actor,Instant ahora){jdbc.update("INSERT INTO envios_acceso(id,enlace_id,usuario_id,correo,proposito,estado,solicitado_en,vence_en,solicitado_por) VALUES(?,?,?,?,?,'PREPARANDO',?,?,?)",id,e.id(),e.usuarioId(),e.correo(),e.proposito(),fecha(ahora),fecha(e.venceEn()),actor);}
 public void resultadoEnvio(String id,CorreoPort.Resultado r,Instant ahora){jdbc.update("UPDATE envios_acceso SET estado=?,terminado_en=? WHERE id=?",r.name(),fecha(ahora),id);if(r==CorreoPort.Resultado.FALLIDO)jdbc.update("UPDATE enlaces_contrasena SET invalidado_en=COALESCE(invalidado_en,?) WHERE id=(SELECT enlace_id FROM envios_acceso WHERE id=?)",fecha(ahora),id);}
}
