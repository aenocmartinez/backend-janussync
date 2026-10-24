-- Las filas existentes son reales y se conservan sin alterar su diagnóstico ni sus tiempos.
ALTER TABLE estados_integraciones
 ADD COLUMN origen VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'REAL',
 DROP PRIMARY KEY,
 ADD PRIMARY KEY (plataforma, origen),
 ADD CONSTRAINT chk_origen_integracion CHECK (
   origen = 'REAL' OR (origen = 'SIMULADO' AND plataforma = 'BRIGHTSPACE')
 );
INSERT INTO estados_integraciones (plataforma, origen) VALUES ('BRIGHTSPACE', 'SIMULADO');
