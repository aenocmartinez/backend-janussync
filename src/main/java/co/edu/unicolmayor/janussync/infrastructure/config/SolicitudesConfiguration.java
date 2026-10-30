package co.edu.unicolmayor.janussync.infrastructure.config;
import co.edu.unicolmayor.janussync.application.usecase.solicitudes.GestionarSolicitudesUseCase;
import co.edu.unicolmayor.janussync.domain.port.*;
import co.edu.unicolmayor.janussync.domain.valueobject.TiemposConexion;
import co.edu.unicolmayor.janussync.infrastructure.adapter.integraciones.*;
import java.time.*;
import java.util.Set;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
@Configuration(proxyBeanMethods=false)
@Profile("jwt")
public class SolicitudesConfiguration {
 @Bean AcademicosSolicitudPort academicosSolicitudes(Environment e,TiemposConexion t){
  var tiempos=new TiemposConexion(Duration.ofSeconds(5),Duration.ofSeconds(10),t.pausa(),t.desactualizadaTrasMinutos());
  return new AcademicosSolicitudOracleAdapter(new ConexionOracle(e.getProperty("ACADEMUSOFT_MECANISMO","ORACLE"),e.getProperty("ACADEMUSOFT_ORACLE_URL",""),e.getProperty("ACADEMUSOFT_ORACLE_USERNAME",""),e.getProperty("ACADEMUSOFT_ORACLE_PASSWORD",""),tiempos));
 }
 @Bean GestionarSolicitudesUseCase gestionarSolicitudes(SolicitudesRepository s,GestionRolesRepository a,UsuarioRepository u,CatalogoProgramasRepository c,AcademicosSolicitudPort f,AuditoriaPort audit,CorreoPort correo,Clock reloj,Environment e){
  String url=e.getProperty("JANUS_FRONTEND_PUBLIC_URL","http://localhost:3000");
  var uri=java.net.URI.create(url);if(!Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getQuery()!=null||uri.getFragment()!=null||uri.getUserInfo()!=null)throw new IllegalArgumentException("Configura la URL pública sin credenciales ni parámetros.");
  return new GestionarSolicitudesUseCase(s,a,u,c,f,audit,correo,reloj,url.replaceAll("/+$",""));
 }
}
