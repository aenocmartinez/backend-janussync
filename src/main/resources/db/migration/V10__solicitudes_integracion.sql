INSERT INTO permisos(codigo,modulo,nombre) VALUES
 ('SOLICITUDES_PREPARAR','SOLICITUDES','Preparar solicitudes de sus programas'),
 ('SOLICITUDES_ENVIAR','SOLICITUDES','Enviar solicitudes como responsable'),
 ('SOLICITUDES_ATENDER','SOLICITUDES','Atender solicitudes en Educación Virtual');
INSERT INTO roles_permisos(rol_id,permiso_codigo) VALUES
 ('COORDINADOR','SOLICITUDES_PREPARAR'),('COORDINADOR','SOLICITUDES_ENVIAR'),
 ('COLABORADOR','SOLICITUDES_PREPARAR'),('OPERADOR_EV','SOLICITUDES_ATENDER');
-- Administrar usuarios no concede acceso académico automáticamente.
CREATE TABLE solicitudes_integracion (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 numero BIGINT NOT NULL AUTO_INCREMENT UNIQUE,
 creador_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 responsable_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 programa_id VARCHAR(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 estado VARCHAR(30) NOT NULL,
 version BIGINT NOT NULL,
 contenido JSON NOT NULL,
 creada_en TIMESTAMP(6) NOT NULL, actualizada_en TIMESTAMP(6) NOT NULL,
 CONSTRAINT ck_solicitud_estado CHECK(estado IN ('BORRADOR','ENVIADA','CON_OBSERVACIONES')),
 FOREIGN KEY(creador_id) REFERENCES usuarios(id), FOREIGN KEY(responsable_id) REFERENCES usuarios(id),
 INDEX solicitudes_ambito(programa_id,estado,actualizada_en)
) ENGINE=InnoDB;
CREATE TABLE comandos_solicitudes (
 operacion CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 actor CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 accion VARCHAR(20) NOT NULL, huella CHAR(64) NOT NULL,
 solicitud CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 FOREIGN KEY(solicitud) REFERENCES solicitudes_integracion(id)
) ENGINE=InnoDB;
CREATE TABLE seguimiento_solicitudes (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 solicitud CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 version BIGINT NOT NULL, accion VARCHAR(20) NOT NULL, actor_nombre VARCHAR(250) NOT NULL,
 comentario TEXT NULL, contenido JSON NULL, instante TIMESTAMP(6) NOT NULL,
 FOREIGN KEY(solicitud) REFERENCES solicitudes_integracion(id), UNIQUE(solicitud,version)
) ENGINE=InnoDB;
CREATE TABLE avisos_solicitudes (
 evento CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 solicitud CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 destinatario VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 resultado VARCHAR(30) NOT NULL, actualizado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(evento,destinatario),
 FOREIGN KEY(evento) REFERENCES seguimiento_solicitudes(id),
 FOREIGN KEY(solicitud) REFERENCES solicitudes_integracion(id)
) ENGINE=InnoDB;
