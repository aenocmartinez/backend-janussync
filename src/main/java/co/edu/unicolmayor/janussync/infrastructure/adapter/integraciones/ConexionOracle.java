package co.edu.unicolmayor.janussync.infrastructure.adapter.integraciones;
import co.edu.unicolmayor.janussync.domain.valueobject.TiemposConexion;
import java.sql.*;
import java.util.Properties;
import oracle.jdbc.datasource.impl.OracleDataSource;
/** Conexión independiente de MySQL/Flyway. No se abre hasta que se necesita. */
public final class ConexionOracle {
    private final String mecanismo, url, usuario, contrasena;
    private final TiemposConexion tiempos;
    public ConexionOracle(String mecanismo,String url,String usuario,String contrasena,TiemposConexion tiempos) {
        this.mecanismo=mecanismo;this.url=url;this.usuario=usuario;this.contrasena=contrasena;this.tiempos=tiempos;
    }
    public Connection abrir() throws SQLException {
        if (!"ORACLE".equals(mecanismo) || !url.startsWith("jdbc:oracle:thin:@") || usuario.isBlank() || contrasena.isBlank())
            throw new SQLException("Conexión académica sin configurar.");
        var ds=new OracleDataSource();ds.setURL(url);ds.setUser(usuario);ds.setPassword(contrasena);
        ds.setLoginTimeout((int)Math.max(1,tiempos.conexion().toSeconds()));
        var p=new Properties();p.setProperty("oracle.net.CONNECT_TIMEOUT",Long.toString(tiempos.conexion().toMillis()));
        p.setProperty("oracle.net.OUTBOUND_CONNECT_TIMEOUT",Long.toString(tiempos.conexion().toMillis()));
        p.setProperty("oracle.jdbc.ReadTimeout",Long.toString(tiempos.ejecucion().toMillis()));ds.setConnectionProperties(p);
        var c=ds.getConnection();
        try {
            c.setReadOnly(true);c.setNetworkTimeout(Runnable::run,(int)tiempos.ejecucion().toMillis());
            c.setAutoCommit(false);
            try(var s=c.createStatement()) { s.execute("SET TRANSACTION READ ONLY"); }
            return c;
        } catch (SQLException e) { c.close();throw e; }
    }
}
