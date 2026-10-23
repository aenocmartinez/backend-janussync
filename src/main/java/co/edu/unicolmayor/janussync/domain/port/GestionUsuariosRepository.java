package co.edu.unicolmayor.janussync.domain.port;
import co.edu.unicolmayor.janussync.domain.model.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
public interface GestionUsuariosRepository {
 <T> T enTransaccion(Supplier<T> tarea);
 boolean existeCorreo(String correo,String excepto);
 void crear(String id,DatosUsuario datos,EstadoRegistro estado,Set<String> roles);
 void editar(String id,DatosUsuario datos);
 void asignar(String id,Set<String> roles);
 void estado(String id,EstadoRegistro estado);
 void revocarSesiones(String id,Instant ahora);
 void invalidarEnlaces(String id,Instant ahora);
 Optional<String> hashContrasena(String id);
 void establecerContrasena(String id,String hash,Instant ahora);
 Optional<String> usuarioPorCorreo(String correo);
 void guardarEnlace(EnlaceContrasena enlace,String hash,Instant ahora);
 Optional<EnlaceContrasena> enlace(String hash,boolean bloquear);
 void consumirEnlace(String id,Instant ahora);
 void prepararEnvio(String id,EnlaceContrasena enlace,String actor,Instant ahora);
 void resultadoEnvio(String id,CorreoPort.Resultado resultado,Instant ahora);
}
