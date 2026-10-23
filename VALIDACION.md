# Verificación de la primera entrega del backend

## Usuarios, contraseñas, SMTP y auditoría — 23 de octubre de 2026

- Ramas actuales y cambios locales conservados, sin upstream que permita pull
  sobre estas ramas. Sin stash, reset, commits, push ni merge.
- Gestión real de usuarios: creación, datos, estado y asignaciones. Se preservan
  roles inactivos existentes, se rechazan nuevas asignaciones inactivas y se
  bloquean programas no verificables porque Academusoft no está integrado.
- V5 incorpora enlaces opacos con hash, propósito, vencimiento y consumo único;
  estados de envío y auditoría transaccional para usuarios y roles. No se
  alteraron migraciones anteriores ni se inventó historial previo a V5.
- Invitaciones P7D y recuperación PT60M configurables; pausa entre envíos PT1M.
  Correo sin transacciones MySQL abiertas, HTML/texto y SMTP STARTTLS obligatorio.
  Aceptación, fallo e incertidumbre se distinguen sin afirmar entrega efectiva.
- Inactivación y contraseña revocan sesiones y enlaces. Reactivar no recupera
  sesiones. Ingresar coordina lectura de credencial y apertura de sesión con el
  mismo bloqueo administrativo: no puede abrir una sesión con una contraseña
  anterior mientras se confirma su cambio. JWT y refresh conservan su protocolo.
- La protección del último administrador evalúa unión de roles activos de una
  persona activa con contraseña establecida; una invitación pendiente no basta.
- Comprobaciones seleccionadas aprobadas: 35 Value Objects, 10 HTTP, 2 arquitectura,
  3 SMTP, 17 MySQL de usuarios, 10 MySQL de roles y 1 de migraciones; 23 frontend,
  TypeScript y compilación. Se ejecutaron adicionalmente verificaciones puntuales
  tras los ajustes, sin repetir pruebas académicas ajenas al alcance.
- Chrome con MySQL y SMTP temporales verificó creación, invitación vía STARTTLS,
  establecimiento, ingreso, recarga, logout, recuperación, restablecimiento,
  cambio y nuevo ingreso, además de eventos persistidos en el detalle. Certificado
  y confianza TLS de prueba limitados a los contenedores temporales.
- Solo se recrearon servicios JanusSync. V1–V5 aplicadas; datos previos comparados
  sin cambios: usuarios, roles, permisos, asignaciones, fechas y número de
  credenciales. Tablas nuevas locales sin cuentas/enlaces/eventos de prueba.
- MAIL_USERNAME y MAIL_PASSWORD siguen pendientes; MAIL_ENABLED=false. No se
  envió a Mailtrap ni se inspeccionó su bandeja. README y .env.example documentan
  variables, habilitación, recreación del backend y prueba mínima sin modificar
  cuentas. CAPTCHA de producción e integración académica continúan pendientes.

Prueba manual: http://localhost:3000/usuarios. Configurar SMTP antes de solicitar
invitaciones; abrir sus enlaces con la sesión del destinatario cerrada. El
historial es contextual y no tiene entrada principal nueva. Las entradas
siguientes documentan estados históricos anteriores.

## Gestión real de roles — 23 de octubre de 2026

- Ramas `funcionalidad/autenticacion-jwt` y todos los cambios locales conservados.
  Las ramas actuales carecen de upstream y hay trabajo sin confirmar; no se hizo
  pull eligiendo otra referencia, stash, reset, commit, push ni merge.
- Crear roles con UUID estable; editar datos/permisos atómicamente; activar e
  inactivar con confirmación del impacto. Los cuatro roles iniciales se conservan
  y también pueden editarse. No hay eliminación física ni efectos sobre asignaciones.
- Nombre obligatorio hasta 80 caracteres con espacios (incluidos Unicode)
  normalizados; unicidad bajo la colación MySQL e índice existente. Descripción
  hasta 500 caracteres y al menos un permiso válido del catálogo existente.
- V4 crea exclusivamente la fila de bloqueo de administración. Transacciones
  READ COMMITTED y FOR UPDATE serializan las escrituras; se reautoriza al actor
  dentro del bloqueo y se protege la unión de roles activos de una misma persona
  activa. Los fallos revierten también cualquier edición de datos incluida.
- Frontend reutiliza formulario y confirmaciones. Evita envíos duplicados,
  conserva entradas tras errores y muestra errores de campo/duplicados/reglas.
  Actualiza usuarios, roles y sesión; retirar permisos propios cambia las acciones
  y navegación. Los roles iniciales renombrados muestran su nombre vigente en el
  menú personal, conservando las abreviaturas de los nombres originales.
- Comprobaciones pertinentes aprobadas: 35 casos de Value Objects, 10 HTTP,
  2 arquitectura, 10 MySQL de roles y la prueba puntual de migraciones existente;
  17 de frontend, TypeScript y lint de los archivos HTTP modificados.
- MySQL aislado verificó duplicados (incluida concurrencia), edición, permisos,
  estados, preservación de asignaciones, unión de roles, reversión del último
  administrador, dos inactivaciones concurrentes y el efecto inmediato sobre
  JWT ya emitidos. Endpoints y campos tienen comprobaciones independientes.
- Chrome con JWT/MySQL temporales verificó creación, duplicados sin perder
  formulario, edición e ID estable, permisos, confirmaciones de estado, rechazo
  del último administrador y actualización de sesión. El recorrido de lectura
  también pasó con búsqueda, filtro, detalle y acciones ahora disponibles.
- Recorrido reproducible de esta entrega, desde backend y con las imágenes
  construidas: `JANUS_TEST_BACKEND_IMAGE=janussync-gestion-roles-backend
  JANUS_TEST_FRONTEND_IMAGE=janussync-gestion-roles-frontend python3
  scripts/verificar-recorrido-jwt.py --gestion-roles` (una sola línea).
- Solo se recrearon backend y frontend JanusSync. Backend `sha256:2050ff71ede1`,
  frontend `sha256:efe9ffcdb5c1`. V1–V4 aplicadas; salud UP y proxy comunicado.
  La comparación antes/después conserva exactamente los roles, permisos,
  asignaciones, fechas y cantidad de usuarios de la base de trabajo. No se crearon
  cuentas ni sesiones de prueba allí. Claves y configuración local conservadas.

Prueba manual: `http://localhost:3000/roles`; crear un rol, abrirlo, editar sus
propiedades/permisos y probar inactivación/reactivación confirmando el impacto.
El último administrador no puede perder los permisos esenciales. Auditoría
persistente e historial no existen aún; se mantienen pendientes sin simularlos.
La gestión de asignaciones/usuarios corresponde a otra entrega. El catálogo de
roles sigue filtrándose y paginándose en el cliente sobre datos HTTP reales.
Las entradas siguientes documentan estados históricos anteriores.

## Corrección de Roles y permisos — 23 de octubre de 2026

La pantalla llamaba `rolesListQuery` → `api.roles.list`. El adaptador HTTP tenía
esa operación asignada a `pendiente`, que lanzaba `ApiError("FORBIDDEN", "Esta
función todavía no está disponible en el servicio real.")` antes de enviar HTTP.
`ErrorState` añadía «No fue posible cargar la información» y «Reintentar».
La causa no era una denegación del endpoint.

- Listado y detalle conectados a GET `/api/v1/roles`, con JWT y permiso actual
  `ROLES_CONSULTAR`. Campos traducidos a JanusApi; cantidades y fechas reales.
- Búsqueda por nombre, filtro de estado, paginación del catálogo en el cliente,
  detalle y enlace a usuarios conservados. Sin fallback simulado.
- Crear, editar, gestionar permisos y cambiar estado deshabilitados con explicación.
  Rutas directas de escritura e historial pendiente no ofrecen «Reintentar».
- Validación acotada: 10 pruebas HTTP, 2 de arquitectura, 3 de integración MySQL
  (cantidades, usuarios/roles inactivos, contrato y retirada de permiso con el
  mismo JWT), 6 del adaptador/estado visual y TypeScript: todas aprobadas.
- Chrome con imágenes de producción, JWT y MySQL 8.4 temporales: GET real 200,
  cuatro roles, permisos/cantidades/fechas, búsqueda, filtro, detalle, enlace a
  usuarios y acciones pendientes comprobados. Ejecutable con `--roles` en
  `backend-janussync/scripts/verificar-recorrido-jwt.py`; no repite la suite JWT.
- Solo se recrearon backend y frontend JanusSync. Imágenes ejecutándose:
  backend `sha256:915aac35ed1b`, frontend `sha256:a0fa065a3b58`.
  Ambos healthy; salud UP, frontend 200, proxy de roles sin JWT 401.
- Base local preservada: comparación de roles, fechas, permisos, asignaciones y
  cantidad de usuarios sin diferencias; Flyway V1–V3 correctas, sin migración nueva.
  El primer administrador ya está inicializado. No se crearon usuarios ni sesiones
  de prueba en esta base; el navegador autenticado se validó en la base temporal.
- Ramas y cambios locales conservados. Sin commits, push ni merge.

Prueba manual en `http://localhost:3000/roles`: recargar con la cuenta propia,
comprobar los cuatro roles, buscar, filtrar y abrir un detalle. Las escrituras
permanecen pendientes. Las entradas anteriores documentan el estado histórico.

## Puesta en marcha local JWT — 23 de octubre de 2026

- Rama `funcionalidad/autenticacion-jwt` y cambios locales conservados. Sin commits,
  push o merge. No se repitieron las suites ya aprobadas ni se cambió código.
- Se añadieron exclusivamente las variables JWT faltantes al `.env` ignorado por
  Git, conservando la conexión MySQL. Archivo protegido con permisos 600.
- No había claves configuradas ni un par en las ubicaciones locales previstas.
  Se generó un par RSA persistente en `/Users/abi/.janussync-claves`, fuera de
  ambos repositorios y sus contextos Docker, con archivos 600. Par validado sin
  imprimir claves. Se configuró UID/GID local 501:20 para lectura sin privilegios.
- La conexión coincide con la del contenedor anterior. Se comprobó desde el
  anfitrión y Docker que el esquema estaba vacío antes del arranque. MySQL local
  informa versión 9.6.0. No se borró ni reinicializó la base.
- Se reutilizó la imagen JWT validada `sha256:382e2864944b`, etiquetada para Compose,
  y se recreó únicamente el servicio backend. Perfiles efectivos: `local,jwt`.
- Flyway aplicó V1, V2 y V3 correctamente. Salud HTTP 200/UP y contenedor healthy.
  Red compartida `janussync-local`, claves montadas en solo lectura.
- Administrador inicial pendiente: marca de inicialización=0, usuarios=0 y
  credenciales=0. No se creó ninguna cuenta, contraseña o sesión de demostración.
- Proxy local comprobado: CSRF 200, falta de CSRF en refresh 403, refresh sin
  sesión 401, consulta de sesión y usuarios sin Bearer 401. Cookie CSRF HttpOnly,
  SameSite=Strict y Secure=false explícito para HTTP local. Access PT5M, refresh P7D.

El paso manual pendiente es ejecutar el comando de inicialización de README en
una terminal interactiva. La contraseña se solicita oculta; no se transmite por
chat. Después se puede ingresar sin reiniciar los servicios. El ingreso con esa
cuenta y su sesión real quedan pendientes de esa acción del usuario.


## Entrega JWT — 23 de octubre de 2026 (America/Bogota)

- Rama `funcionalidad/autenticacion-jwt`, basada en `main` actualizado con
  `git pull --ff-only`. Se conservaron los cambios documentales locales.
- `mvn -B -q -Pmysql-it verify`: 46 pruebas unitarias/HTTP/arquitectura y 20 de
  integración MySQL (15 de autenticación, 5 de persistencia). Cero fallos,
  errores u omisiones. MySQL 8.4 efímero; V1/V2/V3 aplicadas desde cero.
- JWT: credenciales reales con Argon2id, cookies de renovación HttpOnly, claims
  mínimos, vencimiento, firma alterada, algoritmo no permitido, emisor/audiencia
  incorrectos, claims ausentes, renovación rotativa, reuso y consumo concurrente.
- Logout y reuso rechazan JWT anteriores aún vigentes. También se verifica el
  logout mediante Bearer cuando falta la cookie refresh. Inactividad del usuario,
  inactivación de roles y cambios de permisos afectan el siguiente request.
- CSRF real de cookie/header para login, refresh y logout; pruebas de ausencia y
  valor incorrecto. Denegación por defecto y ausencia de HttpSession.
- Límites de intentos, normalización del correo con el Value Object existente,
  inicialización única y concurrente, y rechazo de excepciones inseguras fuera
  de local verificados.
- Comando real de consola probado en TTY contra MySQL temporal: contraseña
  oculta, administrador creado sin cuenta previa. No se inicializó el esquema
  de trabajo ni se generaron credenciales del producto para el usuario.
- Recorrido HTTP mediante Node de producción: login, consulta real, renovación,
  CSRF, logout y rechazo posterior de JWT y refresh correctos.
- Chrome real: formulario, recarga, segunda pestaña, propagación de logout y
  cambio a otro usuario de la base temporal; sin tokens en Storage. El recorrido
  encontró y permitió corregir dos condiciones de carrera del cliente; quedaron
  pruebas de regresión. Fuentes externas bloqueadas solo en ese navegador.
- Imágenes de producción de ambos proyectos construidas. Compose válido, usando
  una ruta temporal de claves solo para validar configuración sin imprimir .env.
- Script de generación de claves: permisos 600 y rechazo de sobrescritura
  comprobados. Claves reales externas exigidas al arrancar; no se generan allí.

Las pruebas completas son reproducibles mediante Maven y
`scripts/verificar-recorrido-jwt.py`, según README. Las bases, claves, redes,
contenedores y perfil del recorrido fueron temporales y se eliminaron. Los dos
servicios de trabajo existentes se conservaron; siguen ejecutando sus imágenes
anteriores hasta configurar claves e inicializar/recrear según README.

Pendiente: proveedor CAPTCHA para producción (sin él, login bloqueado fuera de
la excepción local), retención operativa de sesiones/hashes/límites y ajuste de
cuotas/IP real detrás de proxy de confianza. CRUD, correo, auditoría funcional e
integraciones académicas permanecen fuera de esta entrega. Sin commits, push,
merge ni publicaciones.


## Revisión del 23 de octubre de 2026 (America/Bogota)

- La carpeta del backend no contiene `.git`: no hay rama, estado Git ni remoto
  que consultar o actualizar. No se inicializó un repositorio ni se inventó
  seguimiento. El frontend sí es un repositorio independiente.
- El anfitrión no dispone de Java; se usó la imagen
  `maven:3.9.11-eclipse-temurin-21` (Java 21.0.9, Maven 3.9.11).
- `mvn -B -Pmysql-it verify`: BUILD SUCCESS. Surefire: 46 pruebas; Failsafe:
  5 pruebas. Cero fallos, errores u omisiones en ambos grupos.
- Testcontainers creó MySQL 8.4 aislado: Flyway aplicó V1 y V2 desde un esquema
  vacío. Se comprobaron roles/permisos, filtros con tildes, paginación y relaciones
  sin duplicación, tratamiento literal de comodines, UUID/detalle, instantes y
  rechazo de correo duplicado. La prueba de unicidad no simula dos transacciones
  concurrentes: comprueba el índice mediante una inserción duplicada.
- No se cargó `.env` para esas pruebas ni se borró o reinicializó la base local.
- `docker compose config --quiet`: correcto. Contenedor existente saludable en
  `127.0.0.1:8080`; GET de salud devuelve UP, GET de usuarios sin credencial
  devuelve 401 y GET de roles con credencial técnica devuelve los cuatro roles.
- El código confirma consultas JDBC, dominio y casos de uso independientes de
  Spring (verificados por ArchUnit), perfiles de seguridad restrictivos y
  ausencia de usuarios/contraseñas en las migraciones.
- Comprobación adicional con el JAR generado, otra base MySQL temporal y una
  red Docker propia sin puertos publicados: salud UP con indicadores `db`,
  `diskSpace`, `livenessState`, `ping`, `readinessState` y `ssl`. Al detener solo
  ese MySQL temporal, el endpoint respondió HTTP 503, estado DOWN y `db=DOWN`.
  La conexión de esta comprobación usó TLS (`DB_SSL_MODE=REQUIRED`); el primer
  intento con SSL deshabilitado y sin recuperación de clave pública no arrancó.
  Los contenedores y la red de la comprobación se eliminaron al terminar.
- Los detalles se habilitaron únicamente en ese contenedor aislado. En la
  aplicación de trabajo siguen ocultos. `ssl=UP` no certifica HTTPS ni sistemas
  externos. Flyway valida al arrancar; la salud no vuelve a validar migraciones.
- La respuesta general también anuncia los grupos liveness/readiness. Sus
  subrutas devuelven 401 sin credencial local: solo el GET exacto de
  `/actuator/health` es público. El HEALTHCHECK Docker utiliza esa ruta general.

Solo se actualiza documentación. No hay autenticación del producto, sesiones,
CRUD de usuarios/roles, correo ni conexiones académicas reales. Los roles son
registros extensibles; su gestión mediante escrituras todavía está pendiente.

## Registro anterior de preparación

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
