package co.edu.unicolmayor.janussync.infrastructure.config;
import co.edu.unicolmayor.janussync.application.usecase.*;
import co.edu.unicolmayor.janussync.domain.port.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.nio.file.*;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import java.time.*;
import java.util.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;

@Configuration(proxyBeanMethods=false)
@Profile("jwt")
@EnableConfigurationProperties(PropiedadesAutenticacion.class)
public class AutenticacionConfiguration {
    @Bean Clock relojAutenticacion() { return Clock.systemUTC(); }
    @Bean ProtectorCredenciales protectorCredenciales() { return new ProtectorArgon2(); }
    private byte[] leerPem(String ruta,String tipo) throws Exception {
        String pem=Files.readString(Path.of(ruta));
        return Base64.getMimeDecoder().decode(pem.replace("-----BEGIN "+tipo+"-----","").replace("-----END "+tipo+"-----",""));
    }
    @Bean RSAPrivateKey clavePrivada(PropiedadesAutenticacion p) throws Exception {
        var key=(RSAPrivateKey)KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(leerPem(p.clavePrivada(),"PRIVATE KEY")));
        if (key.getModulus().bitLength()<3072) throw new IllegalArgumentException("La clave RSA debe tener al menos 3072 bits.");
        return key;
    }
    @Bean RSAPublicKey clavePublica(PropiedadesAutenticacion p,RSAPrivateKey privada) throws Exception {
        var key=(RSAPublicKey)KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(leerPem(p.clavePublica(),"PUBLIC KEY")));
        if (!key.getModulus().equals(privada.getModulus())) throw new IllegalArgumentException("Las claves no corresponden.");
        return key;
    }
    @Bean JwtEncoder emisorJwt(RSAPublicKey publica,RSAPrivateKey privada) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(new com.nimbusds.jose.jwk.RSAKey.Builder(publica).privateKey(privada).build())));
    }
    @Bean JwtDecoder decodificadorJwt(RSAPublicKey publica,PropiedadesAutenticacion p) {
        var decoder=NimbusJwtDecoder.withPublicKey(publica).signatureAlgorithm(SignatureAlgorithm.RS256).build();
        OAuth2TokenValidator<Jwt> obligatorios=jwt -> {
            boolean valido=jwt.getExpiresAt()!=null && jwt.getIssuedAt()!=null && jwt.getNotBefore()!=null
                && jwt.getSubject()!=null && jwt.getClaimAsString("sid")!=null && jwt.getAudience().contains(p.audiencia());
            return valido?OAuth2TokenValidatorResult.success():OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(Duration.ZERO),new JwtIssuerValidator(p.emisor()),obligatorios));
        return decoder;
    }
    @Bean EmisorAcceso emisorAcceso(JwtEncoder encoder,PropiedadesAutenticacion p) {
        return (sesion,ahora,vence)->encoder.encode(JwtEncoderParameters.from(
            JwsHeader.with(SignatureAlgorithm.RS256).type("JWT").build(),JwtClaimsSet.builder()
                .issuer(p.emisor()).audience(List.of(p.audiencia())).subject(sesion.usuarioId().toString())
                .claim("sid",sesion.id().toString()).issuedAt(ahora).notBefore(ahora).expiresAt(vence).build())).getTokenValue();
    }
    @Bean AutenticacionUseCase autenticacion(AccesoRepository r,ProtectorCredenciales p,EmisorAcceso e,Clock reloj,PropiedadesAutenticacion config,GestionUsuariosRepository transacciones) {
        return new AutenticacionUseCase(r,p,e,reloj,config.acceso(),config.refresh(),transacciones);
    }
    @Bean InicializarAdministradorUseCase inicializar(AccesoRepository r,ProtectorCredenciales p,Clock reloj) {
        return new InicializarAdministradorUseCase(r,p,reloj);
    }
}
