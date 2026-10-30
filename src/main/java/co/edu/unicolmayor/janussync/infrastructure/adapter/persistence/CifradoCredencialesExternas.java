package co.edu.unicolmayor.janussync.infrastructure.adapter.persistence;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
/** AES-256-GCM: IV aleatorio y datos asociados por columna. La clave nunca está en MySQL. */
public final class CifradoCredencialesExternas {
    private final SecretKeySpec clave;
    private final SecureRandom random=new SecureRandom();
    public CifradoCredencialesExternas(String archivo) {
        if (archivo==null || archivo.isBlank()) { clave=null;return; }
        try {
            byte[] bytes=Base64.getDecoder().decode(Files.readString(Path.of(archivo)).trim());
            if (bytes.length!=32) throw new IllegalArgumentException();
            clave=new SecretKeySpec(bytes,"AES");Arrays.fill(bytes,(byte)0);
        } catch (Exception e) { throw new IllegalStateException("No se pudo cargar la clave externa OAuth."); }
    }
    public boolean disponible() { return clave!=null; }
    public byte[] cifrar(String valor,String campo) {
        if (valor==null) return null;
        try {
            byte[] iv=new byte[12];random.nextBytes(iv);
            var c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,clave,new GCMParameterSpec(128,iv));
            c.updateAAD(("janussync:brightspace:v1:"+campo).getBytes(StandardCharsets.UTF_8));
            byte[] cifrado=c.doFinal(valor.getBytes(StandardCharsets.UTF_8));
            return java.nio.ByteBuffer.allocate(1+iv.length+cifrado.length).put((byte)1).put(iv).put(cifrado).array();
        } catch (GeneralSecurityException | IllegalArgumentException e) { throw new IllegalStateException("No se pudieron proteger las credenciales externas."); }
    }
    public String descifrar(byte[] datos,String campo) {
        if (datos==null) return null;
        try {
            if (datos.length<29 || datos[0]!=1) throw new GeneralSecurityException();
            var c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,clave,new GCMParameterSpec(128,Arrays.copyOfRange(datos,1,13)));
            c.updateAAD(("janussync:brightspace:v1:"+campo).getBytes(StandardCharsets.UTF_8));
            return new String(c.doFinal(Arrays.copyOfRange(datos,13,datos.length)),StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) { throw new IllegalStateException("No se pudieron leer las credenciales externas."); }
    }
}
