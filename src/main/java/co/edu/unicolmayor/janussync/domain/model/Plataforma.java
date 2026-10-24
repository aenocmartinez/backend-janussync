package co.edu.unicolmayor.janussync.domain.model;
public enum Plataforma {
    ACADEMUSOFT("Academusoft"), BRIGHTSPACE("Brightspace");
    private final String nombre;
    Plataforma(String nombre) { this.nombre = nombre; }
    public String nombre() { return nombre; }
    public String alcance() {
        return this == ACADEMUSOFT ? "Comunicación y autenticación mediante una consulta mínima; no verifica vistas académicas."
            : "Consulta autenticada de la identidad de integración; no verifica otras capacidades.";
    }
}
