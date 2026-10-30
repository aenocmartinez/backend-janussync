package co.edu.unicolmayor.janussync.infrastructure.adapter.integraciones;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.*;
import java.nio.ByteBuffer;
final class CuerpoOAuthLimitado implements HttpResponse.BodySubscriber<byte[]> {
    private final CompletableFuture<byte[]> resultado=new CompletableFuture<>();
    private final java.io.ByteArrayOutputStream buffer=new java.io.ByteArrayOutputStream();
    private Flow.Subscription suscripcion;
    public CompletionStage<byte[]> getBody() { return resultado; }
    public void onSubscribe(Flow.Subscription s) { suscripcion=s;s.request(1); }
    public void onNext(List<ByteBuffer> partes) {
        for (var p:partes) {
            if (p.remaining()>65536-buffer.size()) {
                suscripcion.cancel();resultado.completeExceptionally(new java.io.IOException("Respuesta externa excedida"));return;
            }
            byte[] b=new byte[p.remaining()];p.get(b);buffer.writeBytes(b);
        }
        suscripcion.request(1);
    }
    public void onError(Throwable e) { resultado.completeExceptionally(new java.io.IOException("Respuesta externa incompleta")); }
    public void onComplete() { resultado.complete(buffer.toByteArray()); }
}
