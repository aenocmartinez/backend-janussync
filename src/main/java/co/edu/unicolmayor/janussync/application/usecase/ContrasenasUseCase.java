package co.edu.unicolmayor.janussync.application.usecase;
import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.domain.port.*;
import co.edu.unicolmayor.janussync.domain.valueobject.*;
import co.edu.unicolmayor.janussync.domain.exception.ReglaRolException;
import java.time.*;
import java.util.*;
public final class ContrasenasUseCase {
 private final GestionUsuariosRepository repo;private final GestionarUsuariosUseCase usuarios;private final AccesoRepository limites;private final ProtectorCredenciales protector;private final CorreoPort correo;private final AuditoriaPort auditoria;private final Clock reloj;private final String origen;private final Duration invitacion,recuperacion;private final long pausaEnvios;
 public ContrasenasUseCase(GestionUsuariosRepository r,GestionarUsuariosUseCase u,AccesoRepository a,ProtectorCredenciales p,CorreoPort c,AuditoriaPort audit,Clock reloj,String origen,Duration invitacion,Duration recuperacion,long pausaEnvios){repo=r;usuarios=u;limites=a;protector=p;correo=c;auditoria=audit;this.reloj=reloj;this.origen=origen;this.invitacion=invitacion;this.recuperacion=recuperacion;this.pausaEnvios=pausaEnvios;}
 public record Info(String status,String kind,String email,String firstName) {}
 private record Preparado(String envioId,EnlaceContrasena enlace,String token,String nombre) { @Override public String toString(){return "Preparado[protegido]";} }
 private boolean permitir(String clave,int max,long ventana){return limites.consumirIntento(protector.hash(clave),reloj.instant(),max,ventana);}
 public Usuario enviarAdministrativo(String actor,String id,String proposito){return enviar(actor,id,proposito,false);}
 private Usuario enviar(String actor,String id,String proposito,boolean publico){
 Preparado preparado=repo.enTransaccion(()->{
 if(!publico)usuarios.autorizar(actor,"USUARIOS_ACCESO");var u=usuarios.obtener(id);
 boolean establecida=repo.hashContrasena(id).isPresent();
 if(u.estado()!=EstadoRegistro.ACTIVO || proposito.equals("INVITACION")==establecida) {
 if(publico)return null;throw new ReglaRolException("CONFLICT",null,"La cuenta no admite este tipo de enlace en su estado actual.");}
 if(!permitir("correo-usuario:"+id,1,pausaEnvios)){if(publico)return null;throw new ReglaRolException("CONFLICT",null,"Espera antes de solicitar otro envío.");}
 String token=protector.aleatorio();Instant ahora=reloj.instant();
 var enlace=new EnlaceContrasena(UUID.randomUUID().toString(),id,proposito,u.correo().valor(),ahora.plus(proposito.equals("INVITACION")?invitacion:recuperacion),null,null);
 repo.invalidarEnlaces(id,ahora);repo.guardarEnlace(enlace,protector.hash(token),ahora);String envioId=UUID.randomUUID().toString();repo.prepararEnvio(envioId,enlace,actor,ahora);
 auditoria.registrar(actor,proposito.equals("INVITACION")?"ACCESO_INVITACION":"ACCESO_RESTABLECIMIENTO","USUARIO",id,u.nombres().valor()+" "+u.apellidos().valor(),"Solicitud de enlace de acceso; propósito: "+proposito);
 return new Preparado(envioId,enlace,token,u.nombres().valor());
 });
 if(preparado!=null){
 String url=origen+(proposito.equals("INVITACION")?"/establecer-contrasena":"/restablecer-contrasena")+"?token="+preparado.token();
 // La transacción de preparación ya terminó. Ninguna llamada SMTP mantiene bloqueos MySQL.
 CorreoPort.Resultado resultado;
 try{resultado=correo.enviar(preparado.enlace().correo(),preparado.nombre(),proposito,url,preparado.enlace().venceEn());}catch(RuntimeException e){resultado=CorreoPort.Resultado.INCIERTO;}
 final var estado=resultado;repo.enTransaccion(()->{repo.resultadoEnvio(preparado.envioId(),estado,reloj.instant());return null;});
 }
 return usuarios.obtener(id);
 }
 public void recuperar(String correoSolicitado,String direccion){
 if(!permitir("recuperar-ip:"+direccion,15,900))return;
 final String normalizado;try{normalizado=new CorreoElectronico(correoSolicitado).valor();}catch(IllegalArgumentException e){return;}
 if(!permitir("recuperar-correo:"+normalizado,3,900))return;
 repo.usuarioPorCorreo(normalizado).ifPresent(id->enviar(null,id,"RESTABLECIMIENTO",true));
 }
 private String estado(EnlaceContrasena e,String proposito){
 if(e==null || !Set.of("INVITACION","RESTABLECIMIENTO").contains(proposito==null?"":proposito) || !e.proposito().equals(proposito))return "INVALIDO";
 if(e.utilizadoEn()!=null)return "UTILIZADO";if(e.invalidadoEn()!=null)return "INVALIDO";if(!e.venceEn().isAfter(reloj.instant()))return "EXPIRADO";
 var u=usuarios.obtener(e.usuarioId());if(u.estado()!=EstadoRegistro.ACTIVO || !u.correo().valor().equals(e.correo()))return "INVALIDO";
 boolean establecida=repo.hashContrasena(e.usuarioId()).isPresent();if(proposito.equals("INVITACION")==establecida)return "INVALIDO";return "VALIDO";
 }
 private EnlaceContrasena buscar(String token,boolean bloquear){return token==null || token.length()>128?null:repo.enlace(protector.hash(token),bloquear).orElse(null);}
 public Info inspeccionar(String token,String proposito){return repo.enTransaccion(()->{
 var e=buscar(token,false);String s=estado(e,proposito);var u=s.equals("VALIDO")?usuarios.obtener(e.usuarioId()):null;
 return new Info(s,proposito,u==null?null:u.correo().valor(),u==null?null:u.nombres().valor());});}
 public void completar(String token,String proposito,String nueva,String confirmacion){
 validar(nueva,confirmacion);String hash=protector.proteger(nueva);
 repo.enTransaccion(()->{var e=buscar(token,true);String s=estado(e,proposito);
 if(!s.equals("VALIDO"))throw new ReglaRolException(s.equals("EXPIRADO")?"LINK_EXPIRED":s.equals("UTILIZADO")?"LINK_USED":"LINK_INVALID",null,"El enlace no está disponible. Solicita uno nuevo cuando corresponda.");
 repo.consumirEnlace(e.id(),reloj.instant());repo.establecerContrasena(e.usuarioId(),hash,reloj.instant());repo.invalidarEnlaces(e.usuarioId(),reloj.instant());repo.revocarSesiones(e.usuarioId(),reloj.instant());
 var u=usuarios.obtener(e.usuarioId());auditoria.registrar(e.usuarioId(),"ACCESO_CONTRASENA","USUARIO",e.usuarioId(),u.nombres().valor()+" "+u.apellidos().valor(),"Contraseña establecida o restablecida; sesiones y enlaces revocados.");return null;});
 }
 private void validar(String nueva,String confirmacion){new ContrasenaNueva(nueva);if(!Objects.equals(nueva,confirmacion))throw new ReglaRolException("VALIDATION","confirm","Las contraseñas no coinciden.");}
 public void cambiar(String id,String actual,String nueva,String confirmacion){
 if(!permitir("cambio-contrasena:"+id,10,900))throw new ReglaRolException("CONFLICT",null,"Demasiados intentos. Intenta más tarde.");
 validar(nueva,confirmacion);String hash=protector.proteger(nueva);
 repo.enTransaccion(()->{var u=usuarios.obtener(id);if(u.estado()!=EstadoRegistro.ACTIVO || actual==null || actual.length()>128 || !repo.hashContrasena(id).map(h->protector.coincide(actual,h)).orElse(false))throw new ReglaRolException("VALIDATION","currentPassword","La contraseña actual no es correcta.");
 repo.establecerContrasena(id,hash,reloj.instant());repo.revocarSesiones(id,reloj.instant());repo.invalidarEnlaces(id,reloj.instant());auditoria.registrar(id,"ACCESO_CONTRASENA","USUARIO",id,u.nombres().valor()+" "+u.apellidos().valor(),"Contraseña modificada; todas las sesiones y enlaces revocados.");return null;});
 }
}
