ALTER TABLE usuarios ADD COLUMN version_asignacion BIGINT UNSIGNED NOT NULL DEFAULT 0;
CREATE TABLE usuarios_programas (
 usuario_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 programa_id VARCHAR(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 PRIMARY KEY (usuario_id, programa_id),
 CONSTRAINT fk_usuario_programa_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);
-- El programa pertenece a Academusoft, no se crea un catálogo académico local ni FK externa.
