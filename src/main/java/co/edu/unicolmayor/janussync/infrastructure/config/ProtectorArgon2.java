package co.edu.unicolmayor.janussync.infrastructure.config;
import co.edu.unicolmayor.janussync.domain.port.ProtectorCredenciales;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
public final class ProtectorArgon2 implements ProtectorCredenciales {
    private final Argon2PasswordEncoder encoder=new Argon2PasswordEncoder(16,32,1,19456,2);
    private final SecureRandom azar=new SecureRandom();
    @Override public String proteger(String valor) { return encoder.encode(valor); }
    @Override public boolean coincide(String valor,String hash) { return encoder.matches(valor,hash); }
    @Override public String aleatorio() {
        byte[] bytes=new byte[32];azar.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    @Override public String hash(String valor) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
