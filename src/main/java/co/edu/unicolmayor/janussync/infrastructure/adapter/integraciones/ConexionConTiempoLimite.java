package co.edu.unicolmayor.janussync.infrastructure.adapter.integraciones;
import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.domain.port.ComprobarConexionPort;
import java.time.Duration;
import java.util.concurrent.*;
/** Limita el recorrido completo, incluida resolución DNS y lectura del cuerpo. Sin cola de tareas. */
public final class ConexionConTiempoLimite implements ComprobarConexionPort, AutoCloseable {
    private final ComprobarConexionPort delegado;
    private final Duration maximo;
    private final ExecutorService ejecutor;
    public ConexionConTiempoLimite(ComprobarConexionPort delegado, Duration maximo) {
        this.delegado = delegado; this.maximo = maximo;
        this.ejecutor = new ThreadPoolExecutor(0,1,30,TimeUnit.SECONDS,new SynchronousQueue<>(),r -> {
            var hilo = new Thread(r,"conexion-" + delegado.plataforma().name()); hilo.setDaemon(true); return hilo;
        });
    }
    public Plataforma plataforma() { return delegado.plataforma(); }
    public OrigenComprobacion origen() { return delegado.origen(); }
    public String alcance() { return delegado.alcance(); }
    public boolean configurada() { return delegado.configurada(); }
    public ResultadoConexion comprobar() {
        Future<ResultadoConexion> tarea;
        try { tarea = ejecutor.submit(delegado::comprobar); }
        catch (RejectedExecutionException e) { return new ResultadoConexion(DiagnosticoConexion.TIEMPO_AGOTADO); }
        try { return tarea.get(maximo.toMillis(),TimeUnit.MILLISECONDS); }
        catch (TimeoutException e) { tarea.cancel(true); return new ResultadoConexion(DiagnosticoConexion.TIEMPO_AGOTADO); }
        catch (InterruptedException e) { tarea.cancel(true); Thread.currentThread().interrupt(); return new ResultadoConexion(DiagnosticoConexion.ERROR_COMPROBACION); }
        catch (ExecutionException e) { return new ResultadoConexion(DiagnosticoConexion.ERROR_COMPROBACION); }
    }
    public void close() { ejecutor.shutdownNow(); }
}
