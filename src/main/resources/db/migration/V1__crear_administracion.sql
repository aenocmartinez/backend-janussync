CREATE TABLE usuarios (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    nombres VARCHAR(100) NOT NULL,
    apellidos VARCHAR(100) NOT NULL,
    correo VARCHAR(254) NOT NULL,
    cargo VARCHAR(150) NULL,
    estado VARCHAR(8) NOT NULL,
    creado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_usuarios_correo UNIQUE (correo),
    CONSTRAINT ck_usuarios_estado CHECK (estado IN ('ACTIVO', 'INACTIVO')),
    INDEX idx_usuarios_orden (apellidos, nombres, id),
    INDEX idx_usuarios_estado (estado)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE roles (
    id VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    nombre VARCHAR(80) NOT NULL,
    descripcion VARCHAR(500) NOT NULL,
    estado VARCHAR(8) NOT NULL,
    inicial BOOLEAN NOT NULL DEFAULT FALSE,
    creado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_roles_nombre UNIQUE (nombre),
    CONSTRAINT ck_roles_estado CHECK (estado IN ('ACTIVO', 'INACTIVO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE permisos (
    codigo VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    modulo VARCHAR(40) NOT NULL,
    nombre VARCHAR(100) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE usuarios_roles (
    usuario_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    rol_id VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (usuario_id, rol_id),
    CONSTRAINT fk_usuarios_roles_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT fk_usuarios_roles_rol FOREIGN KEY (rol_id) REFERENCES roles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE roles_permisos (
    rol_id VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    permiso_codigo VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (rol_id, permiso_codigo),
    CONSTRAINT fk_roles_permisos_rol FOREIGN KEY (rol_id) REFERENCES roles(id),
    CONSTRAINT fk_roles_permisos_permiso FOREIGN KEY (permiso_codigo) REFERENCES permisos(codigo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
