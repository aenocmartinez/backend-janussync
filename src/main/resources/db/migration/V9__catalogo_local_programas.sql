CREATE TABLE catalogo_programas (
 id VARCHAR(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin PRIMARY KEY,
 codigo VARCHAR(100) NULL,
 nombre VARCHAR(1000) NOT NULL,
 activo BOOLEAN NOT NULL,
 estado_oficial VARCHAR(20) NULL,
 modalidad VARCHAR(250) NULL, metodologia VARCHAR(250) NULL, jornada VARCHAR(250) NULL,
 presente_en_fuente BOOLEAN NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE actualizacion_catalogo_programas (
 id INT PRIMARY KEY,
 ultima_correcta TIMESTAMP(6) NULL, ultimo_intento TIMESTAMP(6) NULL,
 resultado VARCHAR(30) NOT NULL DEFAULT 'NUNCA', total INT NOT NULL DEFAULT 0,
 reserva_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 reserva_hasta TIMESTAMP(6) NULL, proxima_permitida TIMESTAMP(6) NULL,
 proxima_programada TIMESTAMP(6) NULL,
 CHECK (id=1)
) ENGINE=InnoDB;
INSERT INTO actualizacion_catalogo_programas(id) VALUES(1);
INSERT INTO permisos(codigo,modulo,nombre) VALUES('PROGRAMAS_ACTUALIZAR','USUARIOS','Actualizar catálogo de programas');
INSERT INTO roles_permisos(rol_id,permiso_codigo) VALUES('ADMINISTRADOR','PROGRAMAS_ACTUALIZAR');
