CREATE TABLE credenciales (
    usuario_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    hash_contrasena VARCHAR(255) NOT NULL,
    actualizado_en TIMESTAMP(6) NOT NULL,
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
) ENGINE=InnoDB;
CREATE TABLE sesiones_autenticacion (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    usuario_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    creado_en TIMESTAMP(6) NOT NULL,
    vence_en TIMESTAMP(6) NOT NULL,
    revocada_en TIMESTAMP(6) NULL,
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    INDEX idx_sesiones_usuario (usuario_id)
) ENGINE=InnoDB;
CREATE TABLE tokens_renovacion (
    hash_token CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    sesion_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    creado_en TIMESTAMP(6) NOT NULL,
    consumido_en TIMESTAMP(6) NULL,
    FOREIGN KEY (sesion_id) REFERENCES sesiones_autenticacion(id),
    INDEX idx_refresh_sesion (sesion_id)
) ENGINE=InnoDB;
CREATE TABLE limites_autenticacion (
    clave_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    inicio_en TIMESTAMP(6) NOT NULL,
    intentos INT NOT NULL
) ENGINE=InnoDB;
CREATE TABLE inicializacion_acceso (
    id INT PRIMARY KEY,
    completada BOOLEAN NOT NULL
) ENGINE=InnoDB;
INSERT INTO inicializacion_acceso VALUES (1, FALSE);
