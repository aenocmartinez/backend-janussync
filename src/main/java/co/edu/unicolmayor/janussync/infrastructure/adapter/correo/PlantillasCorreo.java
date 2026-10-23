package co.edu.unicolmayor.janussync.infrastructure.adapter.correo;
import java.time.*;
import java.time.format.DateTimeFormatter;
import org.springframework.web.util.HtmlUtils;
final class PlantillasCorreo {
 record Contenido(String asunto,String texto,String html) { @Override public String toString(){return "Contenido[protegido]";} }
 static Contenido acceso(String nombre,String proposito,String enlace,Instant vence){
 if(proposito.equals("PRUEBA"))return new Contenido("JanusSync · prueba SMTP Sandbox","Este mensaje verifica la aceptación SMTP en Mailtrap Sandbox. No contiene credenciales ni enlaces de contraseña.","<!doctype html><html lang=\"es\"><body><h1>JanusSync</h1><p>Prueba SMTP Sandbox. No contiene credenciales ni enlaces de contraseña.</p></body></html>");
 String accion=proposito.equals("INVITACION")?"Establecer contraseña":"Restablecer contraseña";
 String vencimiento=DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm").withZone(ZoneId.of("America/Bogota")).format(vence)+" (Bogotá)";
 String texto="Hola, "+nombre+".\n\n"+accion+" en JanusSync:\n"+enlace+"\n\nVence el "+vencimiento+". Solo se usa una vez. Si no esperabas este mensaje, ignóralo.";
 String html="<!doctype html><html lang=\"es\"><body style=\"font-family:Arial,sans-serif;color:#243047;max-width:560px;margin:32px auto;line-height:1.6\"><h1>JanusSync</h1><p>Hola, "+HtmlUtils.htmlEscape(nombre)+".</p><p><a href=\""+HtmlUtils.htmlEscape(enlace)+"\">"+accion+"</a></p><p>Vence el "+vencimiento+". Solo se usa una vez.</p><p>Si no esperabas este mensaje, ignóralo.</p></body></html>";
 return new Contenido("JanusSync · "+accion,texto,html);
 }
}
