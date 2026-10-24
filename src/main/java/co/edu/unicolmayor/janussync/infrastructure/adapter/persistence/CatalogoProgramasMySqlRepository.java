package co.edu.unicolmayor.janussync.infrastructure.adapter.persistence;

import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.domain.port.*;
import co.edu.unicolmayor.janussync.domain.valueobject.ProgramaId;
import co.edu.unicolmayor.janussync.domain.exception.ReglaRolException;
import java.sql.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataAccessResourceFailureException;

@Repository
public class CatalogoProgramasMySqlRepository implements CatalogoProgramasRepository {
    private static final String BLOQUEO="janus:catalogo:programas";
    private final JdbcTemplate jdbc;
    private final DataSource fuente;
    private final TransactionTemplate tx;
    private final AuditoriaPort auditoria;
    public CatalogoProgramasMySqlRepository(JdbcTemplate jdbc,DataSource fuente,PlatformTransactionManager gestor,AuditoriaPort auditoria) {
        this.jdbc=jdbc;this.fuente=fuente;this.auditoria=auditoria;
        tx=new TransactionTemplate(gestor);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Pagina<ProgramaAcademico> buscar(ConsultaProgramas q) {
        String filtro=" WHERE presente_en_fuente=TRUE AND activo=TRUE AND (nombre LIKE ? ESCAPE '!' OR codigo LIKE ? ESCAPE '!')";
        String texto="%"+q.busqueda().replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        long total=Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM catalogo_programas"+filtro,Long.class,texto,texto));
        var datos=jdbc.query("SELECT * FROM catalogo_programas"+filtro+" ORDER BY nombre,id LIMIT ? OFFSET ?",this::mapear,texto,texto,q.tamano(),(q.pagina()-1)*q.tamano());
        return new Pagina<>(datos,q.pagina(),q.tamano(),total);
    }
    public List<ProgramaAcademico> resolver(Set<ProgramaId> ids) {
        if(ids.isEmpty())return List.of();
        if(ids.size()>100)throw new IllegalArgumentException("Consulta hasta 100 programas por vez.");
        return jdbc.query("SELECT * FROM catalogo_programas WHERE id IN ("+String.join(",",Collections.nCopies(ids.size(),"?"))+") ORDER BY id",this::mapear,ids.stream().map(ProgramaId::valor).sorted().toArray());
    }
    private ProgramaAcademico mapear(ResultSet r,int n)throws SQLException {
        return new ProgramaAcademico(new ProgramaId(r.getString("id")),r.getString("codigo"),r.getString("nombre"),r.getBoolean("activo"),r.getString("modalidad"),r.getString("metodologia"),r.getString("jornada"),r.getString("estado_oficial"),r.getBoolean("presente_en_fuente"));
    }
    public EstadoCatalogoProgramas estado() {
        return jdbc.queryForObject("SELECT *,reserva_hasta>UTC_TIMESTAMP(6) AS vigente FROM actualizacion_catalogo_programas WHERE id=1",(r,n)-> {
            boolean enCurso=r.getString("reserva_id")!=null&&r.getBoolean("vigente");
            String resultado=r.getString("resultado");
            if(resultado.equals("EN_CURSO")&&!enCurso)resultado="INTERRUMPIDO";
            return new EstadoCatalogoProgramas(instante(r,"ultima_correcta"),instante(r,"ultimo_intento"),resultado,r.getInt("total"),enCurso);
        });
    }
    private static Instant instante(ResultSet r,String columna)throws SQLException {var t=r.getTimestamp(columna);return t==null?null:t.toInstant();}
    public Optional<Reserva> reservar(String actor,Duration pausa,Instant siguiente) {
        Connection c=null;
        try {
            c=fuente.getConnection();
            try(var s=c.prepareStatement("SELECT GET_LOCK(?,0)")) {
                s.setString(1,BLOQUEO);
                try(var r=s.executeQuery()) {
                    if(!r.next()||r.getInt(1)!=1) {
                        if(siguiente!=null){liberar(c);return Optional.empty();}
                        throw conflicto("Ya hay una actualización de programas en curso.");
                    }
                }
            }
            String id=UUID.randomUUID().toString();
            boolean reservada=Boolean.TRUE.equals(tx.execute(t->{
                int filas=jdbc.update("""
                    UPDATE actualizacion_catalogo_programas SET reserva_id=?,reserva_hasta=TIMESTAMPADD(SECOND,120,UTC_TIMESTAMP(6)),
                     ultimo_intento=UTC_TIMESTAMP(6),resultado='EN_CURSO',proxima_permitida=TIMESTAMPADD(SECOND,?,UTC_TIMESTAMP(6)),
                     proxima_programada=COALESCE(?,proxima_programada)
                    WHERE id=1 AND (proxima_permitida IS NULL OR proxima_permitida<=UTC_TIMESTAMP(6))
                     AND (? IS NULL OR proxima_programada IS NULL OR proxima_programada<=UTC_TIMESTAMP(6))
                    """,id,pausa.toSeconds(),siguiente==null?null:Timestamp.from(siguiente),siguiente==null?null:Timestamp.from(siguiente));
                if(filas==0) {
                    if(siguiente!=null)return false;
                    throw conflicto("Espera antes de solicitar otra actualización de programas.");
                }
                auditoria.registrar(actor,"CATALOGO_PROGRAMAS","INTEGRACION","PROGRAMAS","Catálogo de programas","Inicio de actualización del catálogo de programas.");
                return true;
            }));
            if(!reservada){liberar(c);return Optional.empty();}
            Connection conexion=c;
            return Optional.of(new Reserva() {
                public String id(){return id;}
                public void close(){
                    try {jdbc.update("UPDATE actualizacion_catalogo_programas SET reserva_id=NULL,reserva_hasta=NULL WHERE id=1 AND reserva_id=?",id);}
                    finally {liberar(conexion);}
                }
            });
        } catch(SQLException e){liberar(c);throw new DataAccessResourceFailureException("No fue posible coordinar el catálogo de programas.");}
          catch(RuntimeException e){liberar(c);throw e;}
    }
    public void publicar(Reserva reserva,List<ProgramaAcademico> programas,String actor,Runnable reautorizar) {
        tx.executeWithoutResult(t->{
            // Mismo orden de bloqueo que las asignaciones: nadie confirma elegibilidad contra una versión intermedia.
            jdbc.queryForObject("SELECT id FROM bloqueo_administracion WHERE id=1 FOR UPDATE",Integer.class);
            comprobarReserva(reserva);
            reautorizar.run();
            jdbc.update("UPDATE catalogo_programas SET presente_en_fuente=FALSE");
            var filas=programas.stream().map(p->new Object[]{p.id().valor(),p.codigo(),p.nombre(),p.activo(),p.estadoOficial(),p.modalidad(),p.metodologia(),p.jornada()}).toList();
            jdbc.batchUpdate("""
                INSERT INTO catalogo_programas(id,codigo,nombre,activo,estado_oficial,modalidad,metodologia,jornada,presente_en_fuente)
                VALUES(?,?,?,?,?,?,?,?,TRUE)
                ON DUPLICATE KEY UPDATE codigo=VALUES(codigo),nombre=VALUES(nombre),activo=VALUES(activo),estado_oficial=VALUES(estado_oficial),
                 modalidad=VALUES(modalidad),metodologia=VALUES(metodologia),jornada=VALUES(jornada),presente_en_fuente=TRUE
                """,filas);
            jdbc.update("UPDATE actualizacion_catalogo_programas SET ultima_correcta=UTC_TIMESTAMP(6),resultado='CORRECTO',total=? WHERE id=1 AND reserva_id=?",programas.size(),reserva.id());
            auditoria.registrar(actor,"CATALOGO_PROGRAMAS","INTEGRACION","PROGRAMAS","Catálogo de programas","Actualización correcta: "+programas.size()+" programas leídos de Academusoft.");
        });
    }
    public void fallar(Reserva reserva,String actor,String resultado) {
        tx.executeWithoutResult(t->{
            comprobarReserva(reserva);
            jdbc.update("UPDATE actualizacion_catalogo_programas SET resultado=? WHERE id=1 AND reserva_id=?",resultado,reserva.id());
            auditoria.registrar(actor,"CATALOGO_PROGRAMAS","INTEGRACION","PROGRAMAS","Catálogo de programas","Actualización fallida ("+resultado+"); se conserva el último catálogo válido.");
        });
    }
    private void comprobarReserva(Reserva reserva) {
        String actual=jdbc.queryForObject("SELECT reserva_id FROM actualizacion_catalogo_programas WHERE id=1 FOR UPDATE",String.class);
        if(!reserva.id().equals(actual))throw conflicto("La reserva de actualización dejó de estar vigente.");
    }
    private static ReglaRolException conflicto(String mensaje){return new ReglaRolException("CONFLICT",null,mensaje);}
    private static void liberar(Connection c) {
        if(c==null)return;
        try(var s=c.prepareStatement("SELECT RELEASE_LOCK(?)")){s.setString(1,BLOQUEO);s.execute();}
        catch(SQLException e){try{c.abort(Runnable::run);}catch(SQLException ignored){}}
        finally{try{c.close();}catch(SQLException ignored){}}
    }
}
