# JanusSync — backend

- Java 21 y Spring Boot. Arquitectura hexagonal: `domain` y `application` no
  importan Spring, JDBC, HTTP, ORM ni clases de `infrastructure`.
- Nombres del negocio en español. Conservar términos como `UseCase`,
  `Repository`, `Controller`, `DTO`, `domain`, `application`, `infrastructure`.
- Value Objects inmutables con invariantes propias; no consultar repositorios
  desde ellos. La unicidad y existencia corresponden a casos de uso/persistencia.
- Antes de trabajar, revisar rama y estado local; ejecutar `git pull --ff-only`
  cuando exista historial remoto, sin sobrescribir cambios locales.
- MySQL propia externa a Docker por defecto. Versionar cambios con Flyway.
- Academusoft es fuente académica. Futuro puerto común con adaptadores HTTP y
  Oracle; Brightspace tendrá puertos/adaptadores propios. No crear adaptadores
  vacíos ni consultas simuladas que se presenten como verificaciones reales.
- Sin CRON de sincronizaciones. Los reintentos serán solicitados por usuarios.
- Fechas de API en ISO-8601 con offset UTC; presentación/fechas de negocio en
  America/Bogota. No depender de la zona del servidor o navegador.
- No guardar secretos, contraseñas fijas ni usuarios de demostración en producción.
- El perfil `local` tiene HTTP Basic técnico provisional, sin conexión al login
  del frontend. El perfil por defecto bloquea administración. No extender este
  mecanismo a producción ni desactivar CSRF al añadir escrituras.
- Verificar `./mvnw verify`; con Docker, `./mvnw -Pmysql-it verify` comprueba
  migraciones y repositorios en MySQL real. No sustituirlo por H2 y afirmar que
  MySQL está validado. Probar cambios significativos, sin repetir checks sin razón.
