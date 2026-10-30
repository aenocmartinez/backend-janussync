-- Solo adiciones. No cambia sesiones JWT ni resultados de comprobaciones.
CREATE TABLE oauth_intentos (
    state_hash CHAR(64) PRIMARY KEY,
    navegador_hash CHAR(64) NOT NULL,
    usuario_id CHAR(36) NOT NULL,
    sesion_id CHAR(36) NOT NULL,
    vence_en TIMESTAMP(6) NOT NULL,
    configuracion CHAR(64) NOT NULL,
    consumido BOOLEAN NOT NULL DEFAULT FALSE,
    diagnostico VARCHAR(48) NULL,
    INDEX idx_oauth_intentos_usuario (usuario_id, vence_en),
    INDEX idx_oauth_intentos_vencimiento (vence_en)
);
CREATE TABLE oauth_conexion (
    id TINYINT PRIMARY KEY,
    acceso_cifrado MEDIUMBLOB NULL,
    refresh_cifrado MEDIUMBLOB NULL,
    vence_en TIMESTAMP(6) NULL,
    scopes VARCHAR(2048) NULL,
    estado VARCHAR(24) NOT NULL,
    configuracion CHAR(64) NOT NULL,
    autorizada_en TIMESTAMP(6) NOT NULL,
    diagnostico VARCHAR(48) NOT NULL,
    CHECK (id=1)
);
