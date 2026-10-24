CREATE TABLE estados_integraciones (
 plataforma VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 reserva_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 comprobando_desde TIMESTAMP(6) NULL,
 reserva_hasta TIMESTAMP(6) NULL,
 proxima_permitida TIMESTAMP(6) NULL,
 comprobacion_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 inicio TIMESTAMP(6) NULL,
 fin TIMESTAMP(6) NULL,
 fallando_desde TIMESTAMP(6) NULL,
 codigo VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NULL,
 duracion_ms BIGINT UNSIGNED NULL,
 alcance VARCHAR(250) NULL,
 CONSTRAINT chk_plataforma_integracion CHECK (plataforma IN ('ACADEMUSOFT','BRIGHTSPACE'))
);
INSERT INTO estados_integraciones(plataforma) VALUES ('ACADEMUSOFT'), ('BRIGHTSPACE');
