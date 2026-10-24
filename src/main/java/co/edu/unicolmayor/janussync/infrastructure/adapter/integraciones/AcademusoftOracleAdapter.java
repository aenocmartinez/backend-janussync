package co.edu.unicolmayor.janussync.infrastructure.adapter.integraciones;

import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.domain.port.ComprobarConexionPort;
import co.edu.unicolmayor.janussync.domain.valueobject.TiemposConexion;
import java.sql.*;
import java.util.Properties;
import oracle.jdbc.datasource.impl.OracleDataSource;

/** Fuente adicional, creada bajo demanda: nunca sustituye el DataSource MySQL ni participa en Flyway. */
public final class AcademusoftOracleAdapter implements ComprobarConexionPort {
    private final String mecanismo, url, usuario, contrasena;
    private final TiemposConexion tiempos;
    public AcademusoftOracleAdapter(String mecanismo,String url,String usuario,String contrasena,TiemposConexion tiempos) {
        this.mecanismo=mecanismo; this.url=url; this.usuario=usuario; this.contrasena=contrasena; this.tiempos=tiempos;
    }
    public Plataforma plataforma() { return Plataforma.ACADEMUSOFT; }
    public boolean configurada() { return "ORACLE".equals(mecanismo) && url.startsWith("jdbc:oracle:thin:@") && !usuario.isBlank() && !contrasena.isBlank(); }
    public ResultadoConexion comprobar() {
        if (!configurada()) return resultado("ORACLE".equals(mecanismo) && url.isBlank() ? DiagnosticoConexion.SIN_CONFIGURAR : DiagnosticoConexion.CONFIGURACION_INVALIDA);
        try {
            var fuente = new OracleDataSource();
            fuente.setURL(url); fuente.setUser(usuario); fuente.setPassword(contrasena);
            fuente.setLoginTimeout((int)Math.max(1,tiempos.conexion().toSeconds()));
            var propiedades = new Properties();
            propiedades.setProperty("oracle.net.CONNECT_TIMEOUT",Long.toString(tiempos.conexion().toMillis()));
            propiedades.setProperty("oracle.net.OUTBOUND_CONNECT_TIMEOUT",Long.toString(tiempos.conexion().toMillis()));
            propiedades.setProperty("oracle.jdbc.ReadTimeout",Long.toString(tiempos.ejecucion().toMillis()));
            fuente.setConnectionProperties(propiedades);
            try (var conexion = fuente.getConnection()) {
                conexion.setReadOnly(true);
                conexion.setNetworkTimeout(Runnable::run,(int)tiempos.ejecucion().toMillis());
                try (var consulta = conexion.prepareStatement("SELECT 1 FROM DUAL")) {
                    consulta.setQueryTimeout((int)Math.max(1,tiempos.ejecucion().toSeconds()));
                    consulta.setMaxRows(1);
                    try (var filas = consulta.executeQuery()) {
                        return resultado(filas.next() && filas.getInt(1)==1 ? DiagnosticoConexion.OK : DiagnosticoConexion.RESPUESTA_INESPERADA);
                    }
                }
            }
        } catch (SQLException e) { return resultado(diagnostico(e)); }
    }
    static DiagnosticoConexion diagnostico(SQLException e) {
        for (SQLException causa=e;causa!=null;causa=causa.getNextException()) {
            if (causa.getErrorCode()==1017 || causa.getErrorCode()==28000 || causa.getErrorCode()==28001) return DiagnosticoConexion.AUTENTICACION_RECHAZADA;
            if (causa instanceof SQLTimeoutException || causa.getErrorCode()==1013 || causa.getErrorCode()==17002 && causa.getCause() instanceof java.net.SocketTimeoutException) return DiagnosticoConexion.TIEMPO_AGOTADO;
            if (causa.getErrorCode()==1031 || causa.getErrorCode()==942) return DiagnosticoConexion.RECURSO_NO_DISPONIBLE;
        }
        return DiagnosticoConexion.SIN_COMUNICACION;
    }
    private static ResultadoConexion resultado(DiagnosticoConexion c) { return new ResultadoConexion(c); }
}
