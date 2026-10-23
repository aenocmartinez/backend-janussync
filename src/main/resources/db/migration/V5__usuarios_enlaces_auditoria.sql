CREATE TABLE enlaces_contrasena (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 hash_token CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
 usuario_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 proposito VARCHAR(20) NOT NULL, correo VARCHAR(254) NOT NULL,
 creado_en TIMESTAMP(6) NOT NULL, vence_en TIMESTAMP(6) NOT NULL,
 utilizado_en TIMESTAMP(6) NULL, invalidado_en TIMESTAMP(6) NULL,
 FOREIGN KEY(usuario_id) REFERENCES usuarios(id),
 CHECK(proposito IN ('INVITACION','RESTABLECIMIENTO')), INDEX idx_enlaces_usuario(usuario_id)
) ENGINE=InnoDB;
CREATE TABLE envios_acceso (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 enlace_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 usuario_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 correo VARCHAR(254) NOT NULL, proposito VARCHAR(20) NOT NULL,
 estado VARCHAR(12) NOT NULL, solicitado_en TIMESTAMP(6) NOT NULL,
 terminado_en TIMESTAMP(6) NULL, vence_en TIMESTAMP(6) NOT NULL,
 solicitado_por CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 FOREIGN KEY(enlace_id) REFERENCES enlaces_contrasena(id),
 FOREIGN KEY(usuario_id) REFERENCES usuarios(id),
 CHECK(estado IN ('PREPARANDO','ENVIADO','FALLIDO','INCIERTO')),
 INDEX idx_envios_usuario(usuario_id,solicitado_en)
) ENGINE=InnoDB;
CREATE TABLE auditoria_administrativa (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 actor_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 actor_nombre VARCHAR(210) NOT NULL, accion VARCHAR(40) NOT NULL,
 entidad_tipo VARCHAR(20) NOT NULL, entidad_id VARCHAR(80) NOT NULL,
 entidad_nombre VARCHAR(254) NOT NULL, instante TIMESTAMP(6) NOT NULL,
 descripcion TEXT NOT NULL,
 INDEX idx_auditoria_entidad(entidad_tipo,entidad_id,instante)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
