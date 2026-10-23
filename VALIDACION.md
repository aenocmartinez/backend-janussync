# Verificación de la primera entrega del backend

- Java 21.0.13 y Maven 3.9.11 utilizados en este entorno.
- `mvn verify`: correcto, JAR Spring Boot generado.
- 46 pruebas aprobadas; cero fallos, cero errores, cero omitidas.
- Las pruebas cubren Value Objects, distinción entre ausencia y error del
  repositorio, independencia hexagonal con ArchUnit, HTTP Basic local, permisos,
  entradas inválidas, error de base de datos y bloqueo fuera del perfil local.
- POM y archivos YAML parseados correctamente.
- `mvn -Pmysql-it -DskipTests test-compile`: correcto; prueba MySQL compilada.

## Verificación pendiente en el equipo del desarrollador

No hay Docker disponible en este entorno: no se construyó ni ejecutó la imagen
Docker, y no se ejecutaron las cinco pruebas de integración sobre MySQL real.
Compilar esas pruebas no equivale a verificar las migraciones o consultas SQL.
Ejecutar `./mvnw -Pmysql-it verify` con Java 21 y Docker, y luego arrancar Compose
contra MySQL local, siguiendo el README. También se debe verificar que las
imágenes base puedan descargarse para la arquitectura del equipo.

El frontend todavía no está conectado a esta API. No hay autenticación del
producto, CRUD completo, correo ni conexiones con los sistemas académicos.
No se creó ni se publicó ningún repositorio de GitHub desde este entorno.
