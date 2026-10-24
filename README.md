## Catálogo local de programas — 2026-10-24 (V9)

Esta entrega sustituye las búsquedas y validaciones Oracle bajo demanda descritas
más abajo: el formulario consulta **MySQL** y Academusoft conserva la autoridad
sobre los datos. No existe edición manual ni CRUD del catálogo.

`FuenteProgramasAcademicos` lee todos los programas en una transacción Oracle
exclusivamente de lectura; `CatalogoProgramasRepository` consulta y publica en
MySQL. Se conservan ID institucional como texto, código, nombre, referencias,
actividad normalizada y estado oficial original. Una lectura completa que deja
de incluir un ID lo marca como ausente, conservando registros y asignaciones;
ausencia e inactividad son condiciones distintas.

Antes de publicar se comprueban finalización, conteo, campos e IDs únicos.
La lectura completa admite hasta 10 000 registros y 60 segundos totales, además
de los límites de conexión y consulta/red existentes (5 y 10 segundos). Un
volumen mayor requiere revisar el límite; no se publica una lista truncada.
Lecturas vacías, incompletas, duplicadas o fallidas conservan el catálogo anterior
y su fecha correcta. Publicación y auditoría son atómicas. El bloqueo MySQL
nombrado coordina instancias durante la lectura **sin transacción MySQL abierta**;
una reserva persistida identifica el intento. Publicación y asignaciones usan
el bloqueo administrativo común para revalidar elegibilidad al guardar.

Variables del backend, explícitas en Compose y `.env.example`:

| Variable | Valor predeterminado | Uso |
| --- | --- | --- |
| `JANUS_PROGRAMAS_SYNC_ENABLED` | `true` | Habilita solo la actualización del catálogo. |
| `JANUS_PROGRAMAS_SYNC_CRON` | `0 0 2 * * *` | Diaria a las 02:00. |
| `JANUS_PROGRAMAS_SYNC_ZONE` | `America/Bogota` | Zona independiente del servidor. |
| `JANUS_PROGRAMAS_MAX_AGE` | `PT48H` | Antigüedad máxima para nuevas asignaciones. |
| `JANUS_PROGRAMAS_UPDATE_COOLDOWN` | `PT1M` | Pausa persistida entre intentos. |

Spring programa el trabajo y MySQL coordina las réplicas y registra el próximo
turno admitido. No sincroniza solicitudes, usuarios, cursos ni Brightspace.
No consulta Oracle durante el arranque, búsquedas o guardados. Tras una caída,
la conexión libera el bloqueo; una reserva interrumpida no impide otro intento
admitido. La vigencia visual de una reserva es 120 segundos; el bloqueo de
conexión sigue evitando solapamientos mientras su propietario esté vivo.

Carga inicial o recuperación explícita, desde `backend-janussync`:

```sh
docker compose run --rm --no-deps --entrypoint java backend \
  -Duser.timezone=America/Bogota -jar /app/janussync.jar \
  --spring.main.web-application-type=none \
  --JANUS_PROGRAMAS_SYNC_ENABLED=false --janus.actualizar-programas=true
```

Reutiliza el mismo caso de uso y coordinación; no requiere contraseña en el chat
ni imprime configuración. Si Oracle falla, devuelve salida no exitosa y mantiene
la última carga válida. La red/VPN institucional debe permitir la lectura.

`GET /api/v1/programas/estado` informa última carga correcta, último intento,
resultado, cantidad, ejecución y vigencia (ROLES_ASIGNAR). Las búsquedas y
resoluciones conservan sus rutas y paginación anteriores, ahora sobre MySQL.
`POST /api/v1/programas/actualizacion` exige **PROGRAMAS_ACTUALIZAR**, JWT y
cookie/cabecera CSRF emitidas por Spring. V9 concede el permiso al rol inicial
ADMINISTRADOR; los demás roles pueden recibirlo mediante la gestión existente.
La auditoría distingue actor humano de Sistema y registra inicio y resultado.

En **Usuarios → Detalle → Gestionar roles y programas**, la fecha aparece junto
al selector y existe una única acción contextual «Actualizar programas» para
actores autorizados. Un fallo de renovación conserva selecciones y catálogo
utilizable. Sin carga válida o con 48 horas cumplidas no se añaden programas;
se conservan consulta y retirada, respetando las reglas del rol. La validación
se repite en la transacción de guardado, sin Oracle. Detalle, sesión y auditoría
mantienen la invalidación existente. No se activa demo ni fallback.

Prueba aislada del recorrido y la carga real:

```sh
python3 scripts/verificar-catalogo-programas.py
```

Usa MySQL temporal y credenciales efectivas solo en memoria, realiza únicamente
lecturas Oracle, y después vuelve Oracle inaccesible para verificar búsqueda,
guardado y conservación ante actualización fallida. El interruptor explícito
`JANUS_TEST_CATALOGO_FIXTURE=true` es exclusivamente una alternativa del script
para MySQL temporal; no acredita carga real ni modifica el modo de la aplicación.

## Gestionar roles y programas con Academusoft — 2026-10-24

Recorrido existente: **Usuarios → Detalle del usuario → Gestionar roles y
programas → Guardar roles y programas**. No hay menú ni CRUD de programas.
El formulario de creación reutiliza el mismo selector; editar datos personales
no modifica asignaciones. «Mi cuenta» muestra el alcance actualizado.

Fuente acreditada mediante lecturas acotadas en Oracle de pruebas:
`ACADEMICO.PROGRAMA`, clave primaria institucional `PROG_ID` (NUMBER), código
`PROG_CODIGOPROGRAMA`, nombre `PROG_NOMBRE`. El comentario oficial de
`PROG_ESTADO` documenta `1` activo / `0` inactivo. Solo `1` es elegible para una
nueva asignación; NULL u otro estado no se interpreta como activo.
Las referencias de modalidad, metodología y jornada provienen de
`ACADEMICO.MODALIDAD`, `ACADEMICO.METODOLOGIA` y `ACADEMICO.JORNADA`, unidas por
sus identificadores presentes en PROGRAMA. El selector muestra estos datos,
código e ID para distinguir nombres/códigos repetidos.

La inspección encontró 433 registros: 424 activos, 8 inactivos y 1 sin estado;
433 IDs diferentes, 381 códigos distintos y 421 nombres distintos. Son cifras
de la revisión, no un catálogo incorporado al código. UNIDADPROGRAMA relaciona
programas con unidades/localidades mediante su propio UNPR_ID; no se confunde
ese identificador de relación con PROG_ID ni se introduce asignación por sede.
El Laravel anterior (`/Users/abi/workspace/UCMC/janussync`) se inspeccionó solo
como referencia. Usaba una vista de estudiantes y una estructura de cursos;
no se ejecutó ni se obtiene el catálogo leyendo estudiantes.

`ProgramaAcademicoRepository` expresa búsqueda paginada y resolución de IDs en
el dominio. `ProgramaAcademicoOracleRepository` implementa ambas consultas con
parámetros, `ROW_NUMBER` y orden estable por nombre/PROG_ID. Una ausencia devuelve
lista vacía; un fallo devuelve 503/ACADEMUSOFT_UNAVAILABLE. No hay fallback ni
adaptador HTTP vacío. MySQL sigue siendo el datasource principal de Spring y
Flyway; la conexión Oracle independiente abre transacciones exclusivamente de
lectura bajo demanda. No se realizan escrituras académicas.

API:

- `GET /api/v1/programas?busqueda=...&pagina=1&tamano=20`: solo activos,
  búsqueda por código/nombre, hasta 50 por página; permiso ROLES_ASIGNAR.
- `GET /api/v1/programas/resolver?ids=...`: hasta 100 IDs, incluidos inactivos,
  para leer asignaciones existentes. Permisos ROLES_ASIGNAR, USUARIOS_CONSULTAR
  o PERFIL_CONSULTAR. Un ID ausente no borra ninguna asignación.
- `PUT /api/v1/usuarios/{id}/roles`: `roles`, `programas` y
  `versionAsignacion` obligatoria. Duplicados rechazados. La respuesta de usuario
  y `/auth/me` incluyen `programas` y `versionAsignacion`.

V8 agrega `usuarios_programas` (clave única usuario/ID institucional) y
`usuarios.version_asignacion`. Los IDs se conservan como texto, sin conversiones
arbitrarias a UUID/número ni eliminación de ceros. No se guarda ni edita un
catálogo paralelo en MySQL. Ante un ID no resuelto, la interfaz conserva el ID
y muestra «Información no disponible».

Las reglas de Coordinador/Colaborador siguen ligadas a los IDs estables
COORDINADOR/COLABORADOR, también si se renombra el rol. Exigen al menos un
programa. Los roles dinámicos mantienen las reglas existentes, sin nueva
clasificación. Oracle valida solo los IDs nuevos; se permite conservar o retirar
los existentes sin exigir disponibilidad actual. Si no quedan roles por programa,
la interfaz avisa que retirará sus vinculaciones antes de guardar.

Las lecturas Oracle se completan **antes** de abrir la transacción MySQL.
Dentro del bloqueo administrativo existente se vuelve a autorizar al actor,
se verifica la versión y se guardan roles/programas/auditoría atómicamente,
conservando la protección del último administrador. Un conflicto devuelve 409
sin sobrescribir cambios y mantiene las selecciones del formulario. Los cambios
invalidan detalle/listados/auditoría/sesión; `/auth/me` lee el alcance actual,
sin permisos ni programas obsoletos dentro del JWT.

Configuración: reutiliza ACADEMUSOFT_MECANISMO y las tres variables externas
ACADEMUSOFT_ORACLE_*; ningún secreto llega al frontend. Límites de lectura
independientes, explícitos en Compose y `.env.example`:
ACADEMUSOFT_PROGRAMAS_CONNECTION_TIMEOUT=PT5S y
ACADEMUSOFT_PROGRAMAS_QUERY_TIMEOUT=PT10S (login/conexión y consulta/lectura).
La configuración exige valores positivos, conexión ≤ ejecución ≤ 60 segundos.
No se alteran los límites ni el adaptador de comprobación de Inicio.

La configuración efectiva y el valor literal del `.env` local fueron comprobados
y coinciden; no se modificaron credenciales ni `.env`. Atención técnica:
`docker compose config` puede escapar los dólares al renderizar. Su salida no
es una fuente para copiar contraseñas a conexiones de prueba. Las comprobaciones
reales usaron el entorno efectivo del contenedor únicamente en memoria.

Prueba aislada del recorrido (JAR e imágenes actualizados):

```sh
python3 scripts/verificar-programas.py
```

Crea MySQL temporal y administrador de prueba por consola; usa el adaptador real
con lecturas Oracle de programas y el frontend HTTP real. Las pruebas de fallo,
inactivos, manipulación, rollback y concurrencia usan un doble de Academusoft
con MySQL real aislado. No se alteran datos de trabajo para las pruebas.

## Brightspace simulado local — 2026-10-24

`BRIGHTSPACE_MODE=real|mock` selecciona exclusivamente el adaptador de comprobación
Brightspace. El valor predeterminado y el de `.env.example` es `real`; el `.env`
local actual selecciona `mock`. Compose transmite la variable explícitamente.
`mock` requiere el perfil `local` y se rechaza si también se activa `prod` o
`production`; cualquier valor desconocido impide arrancar. No existe fallback.

El adaptador simulado no construye el cliente HTTP, no lee credenciales y no
contacta al proveedor. Solo devuelve el resultado determinista `SIMULADO` para
la comprobación de Inicio; no implementa sincronización, cursos, inscripciones
ni calificaciones. El frontend sigue utilizando JanusApi HTTP, con JWT y permisos
actuales de MySQL; **no activar `VITE_JANUS_DEMO`**. Academusoft, administración,
correo y autenticación conservan sus implementaciones reales.

La API conserva `simulated: true|false` en estado y última comprobación. En local,
la presentación interpreta el éxito `SIMULADO` como «Disponible» con los estilos
habituales, sin avisos técnicos de simulación. Antes de comprobar muestra
«Sin comprobar» y respeta la vigencia del resultado. Diagnóstico muestra
«Comprobación completada correctamente» y «Comprobación funcional de Brightspace»,
sin afirmar una petición real ni una respuesta del proveedor. El código visible
es OK; el código persistido y auditado continúa siendo SIMULADO. Esta presentación
operativa local no acredita disponibilidad real del proveedor.
«Comprobar ahora» conserva autorización, exclusión entre peticiones, pausa
persistente (`JANUS_CHECK_COOLDOWN`, predeterminado `PT1M`) y auditoría en MySQL;
la descripción auditada identifica el origen. No hay comprobaciones automáticas.

V7 conserva las filas reales de V6 y separa reservas, fechas y resultados mediante
la clave `(plataforma, origen)`. El resultado real anterior no se presenta como
vigente durante la simulación; vuelve a consultarse al seleccionar `real` y puede
aparecer desactualizado según su fecha. Las credenciales locales se conservan,
sin incorporarlas a Git, al frontend ni al contexto Docker.

Para volver al proveedor, editar **solo el `.env` local** a `BRIGHTSPACE_MODE=real`
y recrear el backend (las mismas imágenes admiten ambos modos):

```sh
docker compose up -d --no-build --no-deps --force-recreate backend
```

No requiere cambios en dominio, casos de uso ni frontend. La autenticación real
Brightspace sigue pendiente de confirmación del administrador del proveedor;
seleccionar `real` mostrará su fallo real mientras continúe el rechazo.

Prueba manual: abrir `http://localhost:3000/inicio`, ingresar con la cuenta real,
solicitar «Comprobar ahora» en Brightspace, comprobar «Disponible», abrir
Diagnóstico y recargar. Academusoft conserva su diagnóstico real independiente.

Validación aislada del recorrido (imágenes preparadas y JAR compilado):

```sh
TEST_JANUS_BRIGHTSPACE_MOCK=true \
JANUS_TEST_BACKEND_IMAGE=janussync-brightspace-mock-backend \
JANUS_TEST_FRONTEND_IMAGE=janussync-brightspace-operativo-frontend \
python3 scripts/verificar-integraciones.py
```

## Investigación de Brightspace — 2026-10-23

Se verificó la configuración efectiva del contenedor contra Compose: coinciden
las seis variables Brightspace. No se modificaron `.env`, claves, mecanismo ni
versión LP `1.49`. La URL es un origen HTTPS sin credenciales ni parámetros.

La consulta autenticada `GET /d2l/api/lp/1.49/users/whoami` devolvió **HTTP 403**,
respuesta HTML con el marcador **Invalid Token**. Se repitió desde la red del backend
usando Java y se confirmó `AUTENTICACION_RECHAZADA`. La diferencia respecto al reloj
indicado por el proveedor fue de 0 segundos al medirla. No se registró el cuerpo,
la URI firmada, firmas ni credenciales.

Se consultó el SDK PHP exacto del `composer.lock` de Laravel: `1.6.1`, revisión
`8898a557496af0c3be7a8237a69afd949de42747`. `formatSignature` utiliza método en
mayúsculas, ruta en minúsculas decodificada y timestamp Unix separados por `&`;
`D2LSigner` aplica HMAC-SHA256 y Base64 URL sin relleno. Para WhoAmI, cuya ruta no
contiene caracteres escapados, Java y PHP generan las mismas firmas. Se verificó
con un vector sintético persistido en pruebas y también con el entorno real,
comparando en memoria y mostrando únicamente igualdad. Laravel usaba LP `1.0`;
se mantuvo `1.49` porque el rechazo no aporta evidencia para cambiar de versión.
No se ejecutó Laravel ni se realizaron acciones en él.

Se corrigió la clasificación que antes agrupaba todos los 403:
- `Invalid Token`: `AUTENTICACION_RECHAZADA`.
- `Timestamp out of range`: `RELOJ_DESAJUSTADO`.
- Mensaje explícito de autorización insuficiente: `PERMISOS_INSUFICIENTES`.
- 403 sin marcador reconocido: `ACCESO_DENEGADO`, sin afirmar la causa.

El rechazo observado corresponde a autenticación, no acredita una falta de permisos
sobre WhoAmI. Las firmas coincidentes no prueban que los pares estén vigentes,
asociados a la misma aplicación o autorizados en ese entorno. El proveedor debe
revisar esos puntos y, si corresponde, renovar el contexto de usuario mediante su
procedimiento autorizado. No se reemplazaron claves ni se inició OAuth o un acceso
interactivo. Referencia oficial: [diagnóstico de 403](https://docs.valence.desire2learn.com/basic/apicall.html#disposition-and-error-handling).

Academusoft conserva `OK` en MySQL. La consulta que permite declararlo disponible
es exclusivamente `SELECT 1 FROM DUAL` con autenticación y timeouts: no valida
vistas académicas ni sincronizaciones. No se repitió Oracle durante esta revisión.

## Comprobación de Academusoft y Brightspace

Inicio consulta `GET /api/v1/integraciones` (`INTEGRACIONES_CONSULTAR`).
«Comprobar ahora» utiliza `POST /api/v1/integraciones/{ACADEMUSOFT|BRIGHTSPACE}/comprobaciones`
(`INTEGRACIONES_COMPROBAR`). Se conservan JWT, autorización actual por petición y
CSRF en los flujos con cookies; el frontend envía también CSRF en sus escrituras.
El GET solo lee MySQL: abrir Inicio, sondear o recargar no consulta plataformas externas.
No hay comprobaciones periódicas ni sincronizaciones.

Cada plataforma conserva en MySQL su último resultado, inicio/fin UTC, duración,
código seguro y alcance. V6 agrega exclusivamente `estados_integraciones`.
La auditoría administrativa registra la comprobación y el código sin respuestas externas.
Los estados son sin configurar, sin comprobar, disponible y con problemas;
la antigüedad se distingue como resultado desactualizado,
sin afirmar que la plataforma esté caída. Un fallo al leer JanusSync produce un error
propio de consulta y nunca una lista vacía ni una alternativa simulada.

### Configuración local de conexiones

Variables de `.env`, pasadas explícitamente por Compose; no escribir secretos en
esta documentación ni en el frontend. `.env`, claves y secretos están excluidos
de Git y del contexto de construcción. Tras completar la configuración:

```bash
docker compose up -d --no-build --no-deps --force-recreate backend
```

| Variable | Uso / valor predeterminado |
| --- | --- |
| `ACADEMUSOFT_MECANISMO` | `ORACLE`; selección técnica interna, sin selector en la interfaz |
| `ACADEMUSOFT_ORACLE_URL` | Vacío; URL JDBC Thin, sin usuario ni contraseña incorporados |
| `ACADEMUSOFT_ORACLE_USERNAME` / `ACADEMUSOFT_ORACLE_PASSWORD` | Vacías; cuenta autorizada de solo lectura |
| `BRIGHTSPACE_BASE_URL` | Vacía; origen HTTPS, sin ruta, credenciales, query ni fragmento |
| `BRIGHTSPACE_API_VERSION` | Vacía; versión LP habilitada por el proveedor |
| `BRIGHTSPACE_APP_ID` / `BRIGHTSPACE_APP_KEY` | Vacías; aplicación Valence ID-Key autorizada |
| `BRIGHTSPACE_USER_ID` / `BRIGHTSPACE_USER_KEY` | Vacías; contexto de usuario de esa aplicación |
| `JANUS_CONNECTION_TIMEOUT` | `PT3S`, máximo para conectar |
| `JANUS_CHECK_TIMEOUT` | `PT10S`, máximo del recorrido completo por plataforma; máximo configurable 60 s |
| `JANUS_CHECK_COOLDOWN` | `PT1M`, intervalo mínimo entre solicitudes de una misma plataforma |
| `JANUS_CHECK_STALE_MINUTES` | `15`, vigencia informativa del resultado |

La reserva persistida permite ver la comprobación en curso sin retirar el resultado
anterior. Un bloqueo MySQL `GET_LOCK` por plataforma evita ejecuciones simultáneas
entre pestañas e instancias; se libera en `finally` y al perder la conexión del proceso.
No se mantiene ninguna transacción MySQL abierta durante la llamada externa.
El indicador en curso vence tras el timeout más 30 s si el proceso se interrumpe.
El ejecutor externo está limitado a una tarea por plataforma, sin cola; las tareas
canceladas no permiten acumular nuevas conexiones si un driver tarda en finalizar.

### Qué comprueba cada plataforma

**Academusoft:** conexión adicional Oracle, creada bajo demanda y separada del
DataSource MySQL/Flyway. Ejecuta únicamente `SELECT 1 FROM DUAL`, con una fila,
conexión marcada de solo lectura y límites de conexión, lectura y consulta.
Verifica comunicación y autenticación, **no** permisos sobre las vistas académicas,
estudiantes, docentes, programas ni funciones de sincronización. Una URL ausente
no impide arrancar el backend ni administrar usuarios y roles.
No se ha supuesto la versión de Oracle a partir del valor `11g` de ejemplo del Laravel:
la compatibilidad del servidor efectivo debe verificarse cuando exista acceso.

**Brightspace:** `GET /d2l/api/lp/{BRIGHTSPACE_API_VERSION}/users/whoami`,
firmado mediante Valence ID-Key HMAC-SHA256, con claves de aplicación y usuario.
Solo acepta HTTPS con certificados válidos y sin redirecciones; acota el cuerpo a
16 KiB y valida JSON de identidad (`Identifier`, `FirstName`, `LastName`, `UniqueName`,
`ProfileIdentifier`). Descarta el cuerpo sin persistir datos personales; no registra
URI firmada, firmas, claves ni excepciones externas. Un HTTP 200 de HTML de acceso
no es éxito. No renueva credenciales ni inicia autenticaciones interactivas.

La revisión de solo lectura de `/Users/abi/workspace/UCMC/janussync` encontró el
SDK PHP `brightspace/valence-sdk-php` (`^1.6`), claves ID-Key por entorno y la versión
`1.0` fijada en el código de comprobación, además de otras versiones en operaciones
ajenas a esta entrega. No hay `.env` ni configuración cacheada en esa copia; los
valores de ejemplo y predeterminados no acreditan configuración efectiva y no se
trasladaron como credenciales vigentes. ID-Key está deprecado y LP `1.0` es obsoleto
según la documentación vigente: el administrador del proveedor debe confirmar una
versión compatible y la vigencia de las claves. Migrar a OAuth requiere el contrato
y autorización correspondientes; esta entrega no inventa una configuración OAuth.

Referencias oficiales: [autenticación ID-Key](https://docs.valence.desire2learn.com/basic/legacyauth.html),
[WhoAmI y versiones LP](https://docs.valence.desire2learn.com/res/user.html),
[timeouts JDBC Oracle](https://docs.oracle.com/en/database/oracle/oracle-database/26/jajdb/oracle/jdbc/OracleConnection.html)
y [compatibilidad JDBC](https://www.oracle.com/database/technologies/faq-jdbc.html).

### Resultado de las comprobaciones locales

Después de revisar Laravel, se completó exclusivamente el `.env` local con la
información adicional proporcionada por el usuario: Oracle de pruebas (solo esquema
`ACADEMICO`) y un juego completo ID-Key rotulado en `GuiaBrightspace.txt`. No se usaron
las demás cuentas Oracle ni se mezclaron claves de aplicaciones diferentes.
El bloque de ejemplo de esa guía contenía un identificador mal formado; ahora los
cuatro identificadores y claves ID-Key deben tener el formato de 22 caracteres
admitido por Valence, antes de realizar una petición. La versión LP local es `1.49`;
el proveedor debe confirmar su habilitación efectiva.

Comprobaciones mínimas reales del 2026-10-23, con los mismos adaptadores Java:
- Academusoft: `SIN_COMUNICACION`; además, el puerto de pruebas agotó el tiempo
  de conexión desde el equipo. Revisar VPN/ruta/firewall autorizado; no se verificó
  autenticación Oracle ni acceso a vistas.
- Brightspace: `AUTENTICACION_RECHAZADA`, también con el juego ID-Key completo de
  la guía. Revisar vigencia de los pares de claves, su asociación, habilitación de
  la aplicación y permisos del proveedor. No se confirma acceso autenticado exitoso.

Estas comprobaciones técnicas no crean usuarios, sesiones ni resultados simulados
en la base de trabajo. En Inicio, «Comprobar ahora» guarda el resultado real bajo
la identidad del administrador autenticado. Un arranque no ejecuta comprobaciones.

### Verificación de esta entrega

Con Java 21/Maven y Docker, desde backend:

```bash
./mvnw -Pmysql-it -Dtest=ConexionesExternasTest,ArquitecturaTest '-Dit.test=IntegracionesMySqlIT,PersistenciaMySqlIT#migracionesCarganCuatroRolesSinCredenciales' verify
```

El navegador se verifica con base efímera y administrador creado por la consola,
sin credenciales externas ni cambios en los datos de trabajo:

```bash
docker build -t janussync-integraciones-backend .
docker build -t janussync-integraciones-frontend ../frontend-janussync
python3 scripts/verificar-integraciones.py
```

Las pruebas de adaptadores usan dobles aislados; no prueban acceso a los proveedores.
Para prueba manual: ingresar en `http://localhost:3000`, abrir Inicio, comprobar cada
plataforma, abrir Diagnóstico y recargar. La última comprobación debe conservarse.
«Sin configurar» requiere completar el entorno; «Disponible» acredita solo el alcance
indicado en el diagnóstico, nunca una sincronización completa.

---

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
