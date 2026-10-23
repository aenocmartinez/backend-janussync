-- Catálogo administrativo tomado del frontend; los endpoints se implementan por entregas.
-- No se insertan usuarios, contraseñas ni programas académicos ficticios.
INSERT INTO permisos (codigo, modulo, nombre) VALUES
('INTEGRACIONES_CONSULTAR', 'INTEGRACIONES', 'Consultar estado de integraciones'),
('INTEGRACIONES_COMPROBAR', 'INTEGRACIONES', 'Solicitar comprobaciones'),
('USUARIOS_CONSULTAR', 'USUARIOS', 'Consultar usuarios'),
('USUARIOS_CREAR', 'USUARIOS', 'Crear usuarios'),
('USUARIOS_EDITAR', 'USUARIOS', 'Editar datos de usuarios'),
('USUARIOS_ESTADO', 'USUARIOS', 'Activar o inactivar usuarios'),
('USUARIOS_ACCESO', 'USUARIOS', 'Gestionar acceso de usuarios'),
('ROLES_ASIGNAR', 'USUARIOS', 'Asignar roles y programas'),
('ROLES_CONSULTAR', 'ROLES', 'Consultar roles'),
('ROLES_CREAR', 'ROLES', 'Crear roles'),
('ROLES_EDITAR', 'ROLES', 'Editar roles'),
('ROLES_PERMISOS', 'ROLES', 'Gestionar permisos de roles'),
('ROLES_ESTADO', 'ROLES', 'Activar o inactivar roles'),
('HISTORIAL_CONSULTAR', 'HISTORIAL', 'Consultar historial'),
('PERFIL_CONSULTAR', 'CUENTA', 'Consultar su propio perfil');

INSERT INTO roles (id, nombre, descripcion, estado, inicial) VALUES
('ADMINISTRADOR', 'Administrador', 'Gestiona usuarios, roles y permisos; supervisa integraciones y atiende incidencias técnicas.', 'ACTIVO', TRUE),
('COORDINADOR', 'Coordinador del programa', 'Prepara, revisa, valida y envía solicitudes a Educación Virtual, con apoyo del colaborador.', 'ACTIVO', TRUE),
('COLABORADOR', 'Colaborador del programa', 'Apoya al coordinador en la preparación de solicitudes y atiende observaciones.', 'ACTIVO', TRUE),
('OPERADOR_EV', 'Operador de Educación Virtual', 'Revisa solicitudes, devuelve observaciones, ejecuta integraciones y solicita reintentos.', 'ACTIVO', TRUE);

INSERT INTO roles_permisos (rol_id, permiso_codigo)
SELECT 'ADMINISTRADOR', codigo FROM permisos;
INSERT INTO roles_permisos (rol_id, permiso_codigo) VALUES
('COORDINADOR', 'PERFIL_CONSULTAR'),
('COLABORADOR', 'PERFIL_CONSULTAR'),
('OPERADOR_EV', 'PERFIL_CONSULTAR'),
('OPERADOR_EV', 'INTEGRACIONES_CONSULTAR'),
('OPERADOR_EV', 'INTEGRACIONES_COMPROBAR');
