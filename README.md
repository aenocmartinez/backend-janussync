# JanusSync — Backend

Sistema de Integración entre Academusoft y Brightspace para la Universidad
Colegio Mayor de Cundinamarca. Primera entrega administrativa del backend.

## Qué funciona en esta entrega

- Java 21, Spring Boot 4.1.2 y Maven.
- Arquitectura hexagonal con dominio/casos de uso independientes del framework.
- Value Objects inmutables: `CorreoElectronico`, `NombrePersona`, `NombreRol`,
  `UsuarioId`. Se comparan por valor y rechazan datos inválidos al construirse.
- Consultas reales a MySQL mediante adaptadores JDBC; no hay datos en memoria
  que se presenten como persistencia real.
- Listado paginado de usuarios con búsqueda, filtro de estado y rol; detalle por UUID.
- Consulta de roles con sus permisos. Catálogo de cuatro roles y quince permisos
  administrativos derivados del frontend; esto no significa que sus escrituras
  ya estén implementadas.
- Migraciones Flyway versionadas. Sin usuarios ni contraseñas predeterminadas.
- Docker multi-stage, usuario sin privilegios y comprobación de salud con MySQL.

## Alcance y siguiente entrega

No están implementadas las altas/ediciones/inactivaciones de usuarios o roles,
autenticación del producto, sesiones, invitaciones, correo, recuperación de
contraseña, historial de cambios ni integraciones académicas. Tampoco se ha
modificado ni conectado el frontend: su acceso sigue siendo la demostración.

El perfil `local` habilita una credencial técnica HTTP Basic solo para consultar
estas primeras rutas desde una herramienta HTTP. No crea una persona en MySQL,
no usa las contraseñas del prototipo y no representa la decisión definitiva de
autenticación. Sin el perfil `local`, se bloquean los endpoints administrativos.
La asignación real de permisos por usuario desde MySQL se implementará con la
autenticación del producto; la credencial técnica solo tiene permisos de lectura.

La siguiente entrega debe implementar autenticación y autorización reales junto
con el alta de usuarios y auditoría, antes de sustituir el acceso simulado del
frontend. No se han añadido puertos o adaptadores externos vacíos.

## Preparar MySQL local, fuera de Docker

Requiere MySQL 8.4 (compatible con MySQL 8.0 que soporte la colación usada).
Crear una base vacía y un usuario propio. No apuntar a una base institucional
existente: las migraciones crean sus tablas en el esquema configurado.

Ejemplo para ejecutar como administrador de MySQL, sustituyendo la contraseña:

```sql
CREATE DATABASE janussync CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'janussync_app'@'%' IDENTIFIED BY 'REEMPLAZAR_POR_TU_CONTRASENA_MYSQL';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
ON janussync.* TO 'janussync_app'@'%';
```

`%` permite la conexión desde la red de Docker en el entorno local; los permisos
se limitan a `janussync`. Usar el host/red que corresponda al desplegar. La cuenta
de migraciones y la cuenta de ejecución se separarán al preparar producción.

## Arranque en tu Mac

Con Docker Desktop abierto, ejecutar en la carpeta del backend:

```bash
cp .env.example .env
```

Editar `.env`:

- `DB_USER` y `DB_PASSWORD`: los del usuario de MySQL que acabas de crear.
- `JANUS_LOCAL_PASSWORD`: contraseña técnica propia de al menos 15 caracteres
  y máximo 72 bytes; no es la contraseña de MySQL ni la de tu cuenta personal.
- Si el puerto MySQL difiere, ajustar `DB_PORT`.

MySQL debe aceptar conexiones desde Docker. Dentro del contenedor `localhost`
es el propio contenedor; `host.docker.internal` apunta al equipo anfitrión.
No se incluye un contenedor de base de datos en el Compose de la aplicación.

```bash
docker compose config --quiet
docker compose up --build -d
docker compose ps
docker compose logs -f backend
```

Comprobar salud:

```bash
curl --fail http://localhost:8080/actuator/health
```

Debe devolver `{"status":"UP"}`. Este estado incluye la conexión a la base
propia y no informa sobre Academusoft ni Brightspace. Los fallos de conexión o
migraciones impiden completar el arranque. El detalle de salud no es público.

Consultar roles y usuarios (curl pedirá la contraseña técnica):

```bash
curl --user desarrollador http://localhost:8080/api/v1/roles
curl --user desarrollador 'http://localhost:8080/api/v1/usuarios?pagina=1&tamano=10'
```

La lista de usuarios inicialmente estará vacía. Un usuario de la demostración
de Lovable no existe automáticamente en esta base.

El Compose publica el puerto solo en la interfaz local. Este Compose es para
desarrollo local, no es una configuración de producción. Detener con:

```bash
docker compose down
```

## Contrato HTTP inicial

| Método y ruta | Resultado |
|---|---|
| GET `/actuator/health` | Salud técnica de backend y base propia |
| GET `/api/v1/usuarios` | Página de usuarios |
| GET `/api/v1/usuarios/{id}` | Detalle por UUID |
| GET `/api/v1/roles` | Roles con permisos |

Filtros: `busqueda`, `estado=ACTIVO\|INACTIVO`, `rol`, `pagina` (desde 1),
`tamano` (1–100). Orden estable por apellidos, nombres e identificador.

```json
{
  "elementos": [],
  "pagina": 1,
  "tamano": 10,
  "total": 0,
  "totalPaginas": 0
}
```

DTO de usuario: `id`, `nombres`, `apellidos`, `correo`, `cargo`, `estado`, `roles`,
`creadoEn`, `actualizadoEn`. No contiene contraseñas ni tokens.
Errores: 400 para entradas inválidas, 401 sin credencial local válida, 403 sin
permiso, 404 si no existe el usuario y 503 si falla la persistencia.
No se convierten errores de base en listas vacías.

El frontend exportado conserva campos en inglés y los ids de demostración como
`u-001`; el adaptador HTTP deberá traducir este contrato a `JanusApi`, usar UUID
reales y completar los campos de acceso/programas cuando se implementen.
Este backend todavía no reemplaza todo ese contrato.

## Arquitectura y convenciones

`domain` contiene modelos, Value Objects, excepciones y puertos Repository.
`application/usecase` expresa las operaciones sin anotaciones Spring.
`infrastructure` contiene controllers/DTO, JDBC, seguridad y configuración que
conecta casos de uso con adaptadores. ArchUnit comprueba esa independencia.
La estructura se organiza por capas en este inicio; podrá agruparse por módulo
al crecer sin cambiar la dirección de las dependencias.

La validación del correo garantiza formato; no demuestra existencia, pertenencia
a la universidad o unicidad. Se normaliza a minúsculas para JanusSync y admite
dominios internacionalizados mediante IDN. El índice único de MySQL protege
la unicidad; al añadir altas habrá que traducir su conflicto a HTTP 409.
No se fija una expresión para códigos académicos hasta confirmar el contrato
real de Academusoft, para evitar perder ceros iniciales o rechazar códigos válidos.

Los instantes se transportan en UTC y MySQL usa sesiones UTC. Los filtros de
fecha de negocio y la presentación deben usar America/Bogota. El contenedor
y la JVM tienen America/Bogota explícito; guardar un `Instant` en UTC no cambia
la zona regional de las reglas del negocio.

## Pruebas

Con Java 21 instalado:

```bash
./mvnw verify
./mvnw -Pmysql-it verify
```

El segundo comando requiere Docker: crea un MySQL temporal aislado, ejecuta
Flyway y prueba búsquedas, filtros, relaciones, paginación y unicidad. No usa
la base `.env`, no se conecta a tu MySQL local y no se omite si falta Docker.
La ejecución de Maven fuera de Compose no carga automáticamente `.env`.
También se puede ejecutar toda la validación en un contenedor Maven:

```bash
docker run --rm -v "$PWD:/app" -w /app maven:3.9.11-eclipse-temurin-21 mvn -B verify
```

## Continuidad

Crear el repositorio privado vacío `backend-janussync`. Esta carpeta no contiene
`.git`; incorporar el código allí después de comprobar arranque y pruebas.
El frontend está separado en `frontend-janussync` y seguirá en el puerto 3000.
No se hacen consultas externas, envíos de correo ni tareas de sincronización.
Academusoft será siempre la fuente de información académica y su acceso por
HTTP u Oracle quedará detrás del mismo puerto. Brightspace tendrá sus puertos
y adaptadores, sin exponer esos mecanismos al usuario.
