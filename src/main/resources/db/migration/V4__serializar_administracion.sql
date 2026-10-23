-- Bloqueo compartido para proteger la capacidad efectiva de administración.
-- No altera roles, permisos ni asignaciones existentes.
CREATE TABLE bloqueo_administracion (id INT PRIMARY KEY, CONSTRAINT ck_bloqueo_unico CHECK (id=1)) ENGINE=InnoDB;
INSERT INTO bloqueo_administracion VALUES (1);
