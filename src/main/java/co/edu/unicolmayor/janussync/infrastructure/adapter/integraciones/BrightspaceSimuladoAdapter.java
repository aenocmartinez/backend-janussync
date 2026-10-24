package co.edu.unicolmayor.janussync.infrastructure.adapter.integraciones;

import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.domain.port.ComprobarConexionPort;

/** Solo la comprobación local de Inicio. No contiene cliente HTTP ni credenciales. */
public final class BrightspaceSimuladoAdapter implements ComprobarConexionPort {
    public Plataforma plataforma() { return Plataforma.BRIGHTSPACE; }
    public boolean configurada() { return true; }
    public OrigenComprobacion origen() { return OrigenComprobacion.SIMULADO; }
    public String alcance() { return "Simulación local de la comprobación de Brightspace; no se contactó al proveedor."; }
    public ResultadoConexion comprobar() { return new ResultadoConexion(DiagnosticoConexion.SIMULADO); }
}
