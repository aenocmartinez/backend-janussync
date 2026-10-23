# JanusSync — Backend

## Usuarios, contraseñas, correo y auditoría

La gestión de usuarios es real: altas, edición de datos, estado y asignación de
roles, además de consultas, invitaciones, recuperación y cambio de contraseña.
V5 añade enlaces de contraseña, estados de envío y auditoría; no altera las
migraciones previas ni incorpora cuentas o programas ficticios. Usuarios y roles
comparten el bloqueo de administración de V4: las operaciones concurrentes no
pueden retirar la última capacidad efectiva de administración con contraseña establecida; una invitación pendiente
no cuenta como respaldo. Inactivar revoca
sesiones y enlaces; reactivar no recupera las sesiones anteriores. El ingreso
coordina validación de contraseña y apertura de sesión con ese mismo bloqueo,
para impedir sesiones nuevas con una credencial anterior a un cambio concurrente.

No existe integración real de programas con Academusoft. Se conservan las
asignaciones existentes, pero se rechazan nuevas asignaciones de coordinador o
colaborador y cualquier identificador de programa sin poder validarlo. Los demás
roles se pueden asignar normalmente. No hay eliminación física de usuarios.

### Mailtrap Sandbox local

El puerto `CorreoPort` no depende de SMTP ni Spring. `CorreoSmtpAdapter` utiliza
Spring Mail con plantillas HTML/texto separadas de los casos de uso. Cambiar entre
proveedores SMTP requiere configuración, no un adaptador nuevo. Gmail y selección
de proveedores en la interfaz quedan fuera de esta entrega.

Completar únicamente en `.env` local (ignorado por Git/Docker): `MAIL_USERNAME`,
`MAIL_PASSWORD` y activar `MAIL_ENABLED=true`. Las credenciales no tienen valores
predeterminados. Compose pasa las variables explícitamente en ejecución; nunca
se usan argumentos de construcción para secretos.

| Variable | Predeterminado / propósito |
|---|---|
| MAIL_ENABLED | false; habilitar tras completar las credenciales |
| MAIL_HOST | sandbox.smtp.mailtrap.io |
| MAIL_PORT | 2525 |
| MAIL_USERNAME / MAIL_PASSWORD | vacías; credenciales SMTP de la integración Sandbox |
| MAIL_FROM / MAIL_FROM_NAME | from@example.com / JanusSync |
| MAIL_SMTP_AUTH | true |
| MAIL_STARTTLS_ENABLED / MAIL_STARTTLS_REQUIRED | true / true |
| MAIL_SSL_ENABLED | false; STARTTLS no es SSL implícito |
| MAIL_CONNECTION_TIMEOUT_MS | 5000 |
| MAIL_READ_TIMEOUT_MS / MAIL_WRITE_TIMEOUT_MS | 10000 / 10000 |
| JANUS_FRONTEND_PUBLIC_URL | http://localhost:3000; HTTPS fuera de local |
| JANUS_INVITATION_TTL | P7D; conserva los 7 días ya definidos en el producto |
| JANUS_RESET_TTL | PT60M |
| JANUS_EMAIL_COOLDOWN | PT1M; intervalo mínimo por usuario entre envíos |

Al habilitar SMTP se validan credenciales, remitente, autenticación, TLS obligatorio
y tiempos positivos, sin mostrar valores. Se verifica la identidad del servidor TLS.
La salud del backend no hace llamadas SMTP; un fallo de correo no bloquea la base
ni impide gestionar cuentas. El resultado se registra por operación.

Después de editar `.env`, recrear solo el backend:

```bash
docker compose up -d --no-build --no-deps --force-recreate backend
```

Prueba mínima que no crea usuarios ni enlaces ni cambia contraseñas:

```bash
docker compose run --rm --no-deps --entrypoint java backend \
  -Duser.timezone=America/Bogota -jar /app/janussync.jar \
  --spring.main.web-application-type=none --janus.probar-correo=true
```

El comando solo admite `sandbox.smtp.mailtrap.io` y envía un mensaje sin secretos
a `diagnostico@pruebas.invalid`. Abrir Email Testing/Sandbox en Mailtrap y revisar
la bandeja seleccionada en su integración SMTP. Aceptación SMTP no significa
entrega al buzón real. Sin acceso a esa bandeja no se afirma haberla inspeccionado.
Referencias: [Spring Mail y timeouts](https://docs.spring.io/spring-boot/reference/io/email.html),
[configuración Sandbox SMTP](https://docs.mailtrap.io/email-sandbox/setup/sandbox-smtp-integration).

### Seguridad y resultados del correo

Los tokens son aleatorios, de propósito específico y solo se guardan como hash.
Consultar un enlace no lo consume; confirmar una contraseña lo consume en la misma
transacción que guarda Argon2id, invalida enlaces pendientes y revoca sesiones.
No hay ingreso automático. La política compartida es 10–128 caracteres, mayúscula,
minúscula y número. La inicialización especial por consola conserva su mínimo de
15 caracteres. Cambiar desde Mi cuenta exige la contraseña actual y cierra también
la sesión actual. No se reciben contraseñas en la administración de usuarios.

Reenviar invalida el enlace anterior; cambiar el correo invalida enlaces del
anterior. La recuperación devuelve siempre una respuesta genérica; no habilita
cuentas inactivas ni evita la invitación inicial. Límite de recuperación: 3 por
correo y 15 por dirección en 15 minutos. Consulta/consumo de enlaces: 60 por minuto
por dirección. Cambio de contraseña: 10 intentos por usuario en 15 minutos.
El proxy local comparte su dirección; un proxy de confianza para direcciones
reales y un proveedor CAPTCHA de producción continúan pendientes. CAPTCHA demo
no autoriza operaciones reales y se mantiene la excepción explícita local.

No hay transacción MySQL abierta durante SMTP. `ENVIADO` significa aceptación
SMTP; `FALLIDO`, rechazo o correo deshabilitado; `INCIERTO`, aceptación que no se
pudo confirmar; `PREPARANDO`, operación sin finalización registrada. Los últimos
dos estados no afirman que el mensaje no salió. No hay colas, CRON ni reintentos
automáticos. La cuenta se conserva y el reenvío es manual y limitado.

### API y auditoría contextual

| Ruta | Permiso actual |
|---|---|
| POST /api/v1/usuarios | USUARIOS_CREAR y ROLES_ASIGNAR; USUARIOS_ACCESO si solicita invitación |
| PUT /api/v1/usuarios/{id} | USUARIOS_EDITAR; solo datos personales |
| PUT /api/v1/usuarios/{id}/roles | ROLES_ASIGNAR |
| PUT /api/v1/usuarios/{id}/estado | USUARIOS_ESTADO |
| POST /api/v1/usuarios/{id}/invitacion o /restablecimiento | USUARIOS_ACCESO |
| GET /api/v1/usuarios/estadisticas o /incidencias | USUARIOS_CONSULTAR |
| POST /api/v1/auth/recuperacion | Público, CSRF y límites; respuesta genérica |
| POST /api/v1/auth/enlaces/consulta o /consumo | Público, CSRF y límites; token en cuerpo |
| POST /api/v1/auth/contrasena | Identidad JWT actual |
| GET /api/v1/auditoria, /actores, /entidades | HISTORIAL_CONSULTAR |

JWT, refresh, revocación, autorización vigente y CSRF se conservan. Los DTO de
usuario añaden `contrasenaActualizada` y `ultimoCorreo` sin secretos. El último
correo contiene propósito, resultado SMTP, solicitud, vencimiento, solicitante y
uso/invalidación del enlace, nunca el enlace ni su hash.

La auditoría guarda actor, entidad, instante UTC y cambios de usuarios, roles y
asignaciones dentro de la misma transacción. No registra contraseñas, hashes ni
tokens. Se consulta desde detalles de usuarios y roles, con filtros y fechas
interpretadas en America/Bogota; no se añadió una entrada principal al menú.

Pruebas aisladas de esta entrega:

```bash
# Después de compilar y construir las imágenes de esta entrega:
JANUS_TEST_BACKEND_IMAGE=janussync-usuarios-backend \
JANUS_TEST_FRONTEND_IMAGE=janussync-usuarios-frontend \
python3 scripts/verificar-usuarios.py
```

El script usa MySQL y buzón SMTP temporales con STARTTLS obligatorio, certificado
y credenciales efímeros, y Chrome. El certificado de prueba solo se confía dentro
del contenedor temporal; la configuración local/producción mantiene la validación
TLS normal. Se reducen los intervalos entre envíos únicamente en esa prueba.


Sistema de Integración entre Academusoft y Brightspace para la Universidad
Colegio Mayor de Cundinamarca. Primera entrega administrativa del backend.

## Autenticación JWT: configuración y ejecución

La autenticación del producto usa usuarios reales de MySQL, Argon2id y JWT RS256
validado mediante el Resource Server de Spring Security. No utiliza HttpSession.
El Compose activa `local,jwt`; el Basic técnico antiguo solo sigue disponible
si se selecciona explícitamente `local` sin `jwt`, para consultas de desarrollo.

### Tiempos y revocación

| Configuración | Valor predeterminado | Límite admitido |
|---|---|---|
| `JANUS_ACCESS_TTL` | `PT5M` (5 minutos) | 30 segundos a 15 minutos |
| `JANUS_REFRESH_TTL` | `P7D` (7 días) | Mayor que access, máximo 30 días |

El refresh tiene vencimiento absoluto desde el login; renovar no extiende esos
7 días. El JWT vence como máximo con la sesión. Se validan firma, RS256, emisor,
audiencia, `exp`, `nbf`, `iat`, `sub` y `sid`; no hay tolerancia temporal, por lo
que los servidores deben mantener sus relojes sincronizados. El JWT solo incluye
esos claims: no transporta contraseñas, correo ni permisos.

Cada petición Bearer consulta la sesión persistida, el usuario activo y los
permisos de sus roles activos. Logout o reutilización de un refresh consumido
revocan la sesión completa: también se rechazan JWT ya emitidos, aunque no hayan
vencido. Los refresh son aleatorios de 256 bits; solo se guardan SHA-256 y
metadatos. Se retienen sus hashes consumidos para detectar reutilización.
La rotación usa bloqueo transaccional por sesión; dos consumos del mismo token
no crean dos sucesores. Si se pierde una respuesta de renovación y se reintenta
con el token consumido, la sesión se revoca y se debe ingresar otra vez.

### Claves externas

Crear una sola vez las claves, con una acción explícita, fuera del repositorio:

```bash
sh scripts/generar-claves-locales.sh "$HOME/.janussync-claves"
```

El script exige un directorio nuevo y externo, no sobrescribe claves y crea
RSA de 3072 bits: privada PKCS#8 y pública X.509 en PEM, ambas con permisos 600.
No se ejecuta al arrancar. Nunca subir claves, `.env` ni credenciales a Git;
además se excluyen `*.pem`, `*.key` y `.secrets/` del repositorio y del build.
Respaldar y custodiar la privada como secreto. No imprimirla ni pasarla por argv.

En `.env`, conservar la conexión MySQL existente y añadir los valores de
`.env.example`. Definir `JANUS_KEYS_DIR` con la ruta absoluta externa. En macOS,
poner en `JANUS_RUNTIME_UID` y `JANUS_RUNTIME_GID` los resultados de `id -u` e
`id -g`, para que el proceso sin privilegios pueda leer tus claves 600. El valor
predeterminado del contenedor es 10001:10001; el dueño de las claves debe
coincidir con el UID elegido. Nunca usar UID 0 para resolver permisos.

Fuera de Compose, `JANUS_JWT_PRIVATE_KEY` y `JANUS_JWT_PUBLIC_KEY` son rutas de
archivos legibles por Java. `JANUS_JWT_ISSUER` y `JANUS_JWT_AUDIENCE` deben coincidir
entre emisión y validación. La aplicación falla al arrancar si faltan claves,
no corresponden entre sí o RSA tiene menos de 3072 bits. No genera claves nuevas.
La rotación actual es coordinada: sustituir el par y reiniciar invalida los JWT
anteriores; las sesiones no revocadas pueden renovar. No hay convivencia de
varias claves ni endpoint JWKS en esta entrega. Ante compromiso, revocar también
las sesiones persistidas antes de volver a habilitar el acceso.

### Arranque e inicialización del primer administrador

Comprobar primero contenedores y puertos; este procedimiento recrea únicamente
el backend de este proyecto. MySQL de trabajo sigue fuera de Docker. V3 añade
tablas de credenciales, sesiones, hashes de refresh, límites e inicialización;
no altera V1/V2 ni crea personas o contraseñas predeterminadas.

```bash
docker compose config --quiet
docker compose build
docker compose run --rm --no-deps --entrypoint java backend \
  -Duser.timezone=America/Bogota -jar /app/janussync.jar \
  --spring.main.web-application-type=none --janus.inicializar-admin=true
docker compose up -d
```

El comando requiere TTY y solicita nombres, apellidos, correo y contraseña
confirmada de 15–128 caracteres. La contraseña se introduce oculta; no usar
argumentos, archivos SQL, logs ni variables con una contraseña inicial fija.
No necesita cuenta previa ni expone una ruta pública de inicialización.
Una marca bloqueada en MySQL y una transacción impiden ejecutar dos altas
iniciales concurrentes o repetir el proceso. Si ya hay credenciales, se rechaza.
Los errores dejan la transacción sin cambios y el comando termina con código 1.
No activar `janus.inicializar-admin` en el servicio web ordinario.

Iniciar después el Compose del frontend: usa la red `janussync-local` creada por
el backend y comunica con `http://backend:8080`. El navegador solo usa `/api` en
su propio origen; Node/Nitro actúa como proxy. No colocar claves o contraseñas
en variables `VITE_*`. El proxy no registra headers, cookies ni cuerpos.

### Cookies, CSRF y CAPTCHA

Refresh: cookie `JANUS-REFRESH`, HttpOnly, SameSite=Strict, ruta `/api/v1/auth`;
Secure por defecto. El Compose de desarrollo fija explícitamente
`JANUS_COOKIE_SECURE=false` para HTTP local. Fuera del perfil `local` se rechaza
esa excepción. En HTTPS utilizar Secure=true.

`GET /api/v1/auth/csrf` entrega un token CSRF y establece la cookie HttpOnly
`JANUS-CSRF`. El cliente envía el valor mediante `X-CSRF-TOKEN` en login, refresh
y logout. CSRF permanece habilitado también para escrituras futuras; GET no
modifica datos de negocio. Cookie de CSRF no implica HttpSession.

No hay proveedor CAPTCHA real en esta entrega. La excepción
`JANUS_ALLOW_NO_CAPTCHA_LOCAL=true` solo se acepta con `local`; el frontend real
no muestra ni envía el CAPTCHA simulado. Sin esa excepción, el login responde
503 hasta implementar un proveedor verificado por el servidor. No habilitar
la excepción local en un despliegue institucional ni aceptar tokens demo allí.

El límite persistido es de 10 intentos de login por cuenta cada 15 minutos,
50 por dirección remota cada 15 minutos y 60 renovaciones por minuto por dirección.
Detrás de este proxy Node, el backend ve su dirección: el límite por dirección
es compartido; el de cuenta sigue siendo independiente. No se confía en
`X-Forwarded-For` enviado por el cliente. Antes de producción se debe ajustar
la cuota y configurar un proxy de confianza para limitar por IP real.
Los errores no distinguen usuario ausente, contraseña incorrecta o inactividad.

| Método y ruta | Protección |
|---|---|
| GET `/api/v1/auth/csrf` | Público; no-store |
| POST `/api/v1/auth/login` | CSRF, límites, credenciales, política CAPTCHA |
| POST `/api/v1/auth/refresh` | CSRF, cookie rotativa y límite |
| POST `/api/v1/auth/logout` | CSRF y cookie o Bearer vigente; revoca aun con JWT vencido mediante cookie |
| GET `/api/v1/auth/me` | Bearer y comprobación de sesión/usuario |
| GET `/api/v1/usuarios`, `/api/v1/usuarios/{id}` | Bearer y USUARIOS_CONSULTAR actual |
| GET `/api/v1/roles`, `/api/v1/roles/permisos` | Bearer y ROLES_CONSULTAR actual |
| POST `/api/v1/roles` | Bearer y ROLES_CREAR actual |
| PATCH `/api/v1/roles/{id}` | ROLES_EDITAR para datos; ROLES_PERMISOS para permisos, ambos si modifica ambos |
| PUT `/api/v1/roles/{id}/estado` | Bearer y ROLES_ESTADO actual |
| Rutas sin regla explícita | Denegación por defecto |

Los sistemas académicos todavía no están implementados. Sus llamadas desde
el frontend real devuelven una indisponibilidad explícita, nunca mocks.
Las tablas de sesiones/hashes/límites requieren una política de retención antes
de producción; no se han añadido CRON ni tareas de sincronización. No borrar
hashes de refresh de sesiones aún vigentes, pues se perdería detección de reuso.

Referencias técnicas: [JWT en Spring Security](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html),
[CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html) y
[Argon2id](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).

## Base de consultas


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

Las integraciones académicas siguen pendientes. Usuarios, invitaciones, correo,
contraseñas y auditoría están implementados según la sección inicial. La autenticación JWT y el acceso real del frontend sí
están implementados, según la sección anterior.

La preparación histórica de consultas mediante Basic se conserva más abajo como
referencia del perfil `local` sin `jwt`. Para el acceso del producto seguir la
sección JWT anterior: el Compose ya no requiere credenciales técnicas Basic.

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

## Arranque local

Seguir la sección de autenticación JWT al comienzo de este documento. El Compose
actual usa `local,jwt`; las credenciales técnicas antiguas no autentican usuarios
del producto. El backend continúa en `127.0.0.1:8080` y el frontend en el puerto
3000. La salud se consulta con `curl --fail http://localhost:8080/actuator/health`.
Devuelve JSON con `status=UP` cuando la base propia y los demás indicadores están
operativos; puede anunciar también liveness/readiness. No comprueba plataformas
académicas. Las migraciones se validan al arrancar, no en cada lectura de salud.

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

GET `/api/v1/roles` devuelve un array de roles con `id`, `nombre`, `descripcion`,
`estado`, `inicial`, `permisos`, `totalUsuarios`, `usuariosActivos`, `creadoEn` y
`actualizadoEn`. Las cantidades provienen de asignaciones MySQL; usuarios activos
se refiere al estado de la persona, incluso si el rol está inactivo. La consulta
agrega asignaciones independientemente de los permisos para no multiplicarlas.
El endpoint exige Bearer y `ROLES_CONSULTAR` actual. No requiere migración nueva.

DTO de usuario: `id`, `nombres`, `apellidos`, `correo`, `cargo`, `estado`, `roles`,
`creadoEn`, `actualizadoEn`. No contiene contraseñas ni tokens.
Errores: 400 para entradas inválidas, 401 sin Bearer válido o con sesión revocada, 403 sin
permiso, 404 si no existe el usuario y 503 si falla la persistencia.
No se convierten errores de base en listas vacías.

El adaptador HTTP traduce los campos en español a `JanusApi` y utiliza UUID
reales. Los metadatos de acceso/programas y las operaciones administrativas
pendientes todavía no completan todo el contrato del prototipo.

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

Además de las suites Maven, el recorrido aislado reproduce el comando de consola,
HTTP a través de Node y Chrome real. Requiere ambos proyectos como carpetas
hermanas, Python 3.9+, Node 24+, OpenSSL, Docker y Chrome. En Linux definir
`JANUS_CHROME_EXECUTABLE` con la ruta del ejecutable. Utiliza sus propios
contenedores con nombres aleatorios, MySQL efímero, claves y perfil de navegador
temporales; los elimina al terminar. No usa la base ni las credenciales de `.env`.
Las fuentes de Google se bloquean solo en este navegador de prueba, para evitar
que una descarga externa impida verificar la interfaz.

```bash
# Desde backend, después de mvn -Pmysql-it verify:
docker build -t janussync-jwt-backend .
docker build --target production -t janussync-jwt-frontend ../frontend-janussync
python3 scripts/verificar-recorrido-jwt.py
```


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

El backend ya tiene `.git` y remoto `origin` hacia
`aenocmartinez/backend-janussync`. Conservar los cambios locales y trabajar en
ramas descriptivas; no publicar sin autorización.
El frontend está separado en `frontend-janussync` y seguirá en el puerto 3000.
No se hacen consultas externas, envíos de correo ni tareas de sincronización.
Academusoft será siempre la fuente de información académica y su acceso por
HTTP u Oracle quedará detrás del mismo puerto. Brightspace tendrá sus puertos
y adaptadores, sin exponer esos mecanismos al usuario.

### Validación puntual del listado de roles

Después de compilar el JAR y las imágenes actuales, el recorrido Chrome con
MySQL temporal se ejecuta sin repetir la batería JWT completa:

```bash
JANUS_TEST_BACKEND_IMAGE=janussync-roles-backend \
JANUS_TEST_FRONTEND_IMAGE=janussync-roles-frontend \
python3 scripts/verificar-recorrido-jwt.py --roles
```

El script inicializa solo su base temporal y la elimina al finalizar. No usa
credenciales ni datos de la base local. Requiere Docker, Node y Chrome.

## Gestión real de roles

Se pueden crear roles con UUID estable, editar nombre/descripción, sustituir
permisos y activar/inactivar. No existe eliminación física. POST recibe
`nombre`, `descripcion` y `permisos`; PATCH admite esos mismos campos opcionales
(los ausentes se conservan) y guarda los cambios atómicamente. PUT de estado
recibe `{"estado":"ACTIVO"}` o `INACTIVO`. Nunca se alteran asignaciones.

Nombre: 1–80 caracteres, espacios normalizados; comparación e índice único de
MySQL `utf8mb4_0900_ai_ci` (ignora mayúsculas y acentos). Descripción: hasta 500
caracteres. Se requiere al menos un permiso del catálogo existente; no hay API
para crear permisos arbitrarios. Errores ProblemDetail incluyen `code` y
`fieldErrors` cuando procede: 400 validación, 403 autorización, 404 rol ausente,
409 `DUPLICATE_NAME` o `ADMIN_REQUIRED`. No se filtran mensajes SQL.

V4 añade únicamente `bloqueo_administracion`. Todas las escrituras de roles toman
su fila con `FOR UPDATE`, usan aislamiento READ COMMITTED, vuelven a comprobar
los permisos del actor y comprueban antes de confirmar que una misma persona
activa reúne los seis permisos esenciales mediante la unión de roles activos:
USUARIOS_CONSULTAR, USUARIOS_ESTADO, ROLES_ASIGNAR, ROLES_CONSULTAR,
ROLES_PERMISOS y ROLES_ESTADO. Un fallo revierte datos y permisos juntos.
Las escrituras de usuarios y asignaciones adquiridas en V5 usan este mismo
bloqueo antes de leer y aplicar la regla, incluyendo acceso con contraseña.
Un rol inactivo conserva sus asignaciones y no concede permisos. Al reactivarlo,
las personas activas asignadas vuelven a recibirlos. JWT consulta permisos actuales
en cada petición. Se conserva CSRF en los endpoints con cookies; el transporte
frontend también envía su cabecera en escrituras Bearer.

La auditoría persistente se incorporó en V5 y se consulta desde el detalle. Las migraciones aplicadas anteriormente permanecen sin cambios.
Recorrido visual aislado: ejecutar el comando anterior con las imágenes
`janussync-gestion-roles-backend` y `janussync-gestion-roles-frontend`, sustituyendo
`--roles` por `--gestion-roles`.
