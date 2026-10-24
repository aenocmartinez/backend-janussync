## 2026-10-24 — Catálogo MySQL de programas (V9)

Carga inicial **real**, mediante el comando de consola y la configuración efectiva
vigente: **433 programas**, 433 presentes en la fuente y **424 activos** para
nuevas vinculaciones. Última carga correcta local:
**2026-10-24 10:50:23.466934 America/Bogota** (15:50:23.466934Z).
V9 aplicada; Flyway 1–9 correctas. Una interrupción previa de la VPN produjo
SQLRecoverableException 18714 y puerto inaccesible desde el Mac; tras restablecer
la conexión, la lectura completa y la publicación finalizaron correctamente.
No se cambiaron credenciales ni `.env`; no se escribieron datos en Oracle.

Las huellas anteriores y posteriores coinciden para usuarios, roles, credenciales,
asignaciones de roles y programas. V9 añade únicamente el permiso administrativo
PROGRAMAS_ACTUALIZAR y su concesión al rol ADMINISTRADOR, además de las tablas del
catálogo. La ejecución inicial deja auditoría persistida con actor Sistema.

Verificación específica aprobada: **22 pruebas backend**, sin fallos:

- ProgramasTest (3), ProgramacionCatalogoTest (1) y ArquitecturaTest (2).
- CatalogoProgramasMySqlIT (9) y ProgramasMySqlIT (7), con MySQL 8.4 temporal.
  Fuente doble explícita para fallos/incompletas/vacías/duplicados, ausencia e
  inactividad con referencias conservadas, rollback de publicación y auditoría,
  lectura anterior o nueva sin estado parcial, concurrencia manual/programada
  entre reservas, deduplicación persistida, límite de antigüedad y revalidación
  bajo bloqueo al guardar. Búsqueda/asignación verifican cero llamadas a fuente.
- Endpoint manual: permiso vigente con mismo JWT, ausencia de CSRF, cabecera
  incorrecta, cookie/cabecera obtenidas del flujo real Spring, resultado correcto,
  pausa entre intentos y auditoría del actor. Se corrigió la validación para que
  actúe en el filtro CSRF estándar **antes** de autenticar Bearer; la estrategia
  de autenticación puede renovar el token y hace inadecuado compararlo después
  dentro del controlador. Se mantiene el comportamiento previo de los demás
  endpoints y la protección de login/renovación/cierre.
- Programación diaria: 02:00 America/Bogota equivale a 07:00Z y el interruptor
  deshabilitado no registra la tarea. No ejecuta lectura inicial al arrancar.

Frontend: TypeScript, compilación y **9 pruebas** programs-http aprobadas:
contrato, paginación, versión, estados de vigencia, retirada de ID conservado,
búsquedas tardías, fecha y acción contextual única, sin fallback al fallar.
No se repitieron baterías de funciones ajenas. README documenta variables,
comando de carga, horario, coordinación, límites y recuperación.

Chrome validó también el recorrido con JWT real y MySQL temporal: carga de
433 programas desde Oracle real y posterior desconexión deliberada de Oracle.
Búsqueda local, selección múltiple, actualización manual HTTP 503 conservando
cantidad/fecha/vigencia/selecciones, guardado, recarga, retirada y alcance actual
en Mi cuenta. La pausa del script aislado es PT1S para probar el fallo tras la
carga; el entorno local conserva PT1M. Sin escrituras Oracle ni cambios en
asignaciones de trabajo.

Despliegue local completado: JanusSync 0.1.0-SNAPSHOT con V9; imágenes backend
7b9f5a72ba2f y frontend 0677ec03a4c0, ambos healthy. Se recrearon únicamente
esos servicios y se verificó que los contenedores ajenos conservaron sus IDs.
Proxy /api/v1/auth/csrf respondió 200; consulta del catálogo sin JWT, 401.
Programación efectiva true / 0 0 2 * * * / America/Bogota, vigencia PT48H,
pausa PT1M. Brightspace continúa mock. Todas las variables anteriores efectivas
se conservaron. Sin commits, push, merge ni cambios de credenciales.

## 2026-10-24 — Roles y programas con Academusoft real

Completado el recorrido existente, sin nuevo módulo ni menú. Se añade puerto
ProgramaAcademicoRepository, adaptador Oracle de solo lectura, búsqueda paginada
y resolución de IDs. MySQL continúa como datasource principal; Oracle se abre
solo para consultar catálogo/validar adiciones. No se modificó Brightspace.

Fuente acreditada: ACADEMICO.PROGRAMA, PK PROG_ID, código PROG_CODIGOPROGRAMA y
nombre PROG_NOMBRE. Comentario oficial PROG_ESTADO: 1 activo, 0 inactivo; solo 1
es elegible para nuevas asignaciones. Metadatos y muestras acotadas confirman
433 programas, 433 IDs, 381 códigos distintos, 421 nombres distintos; 424 activos,
8 inactivos y 1 sin estado. Modalidad, metodología y jornada se consultan por sus
IDs de referencia. UNIDADPROGRAMA tiene su propia clave UNPR_ID y relaciones de
unidad/localidad; no se confunde con la identidad del programa ni se añade una
asignación por sede. No se leyeron estudiantes. Laravel anterior fue solo leído.

Se acreditó el **adaptador Java real**, usando configuración efectiva en memoria:
páginas de 5 sin solapamiento, búsqueda 303 con 4 coincidencias diferenciadas,
búsqueda BACTER, resolución de IDs 1/4/inexistente/manipulado y reconocimiento
oficial del programa 4 como no activo. Solo SELECT y control de transacción de
lectura; no hubo escrituras Oracle. El login frío con 3 s agotó el límite
(ORA-18714); se acreditó con 5 s. Las consultas de programas tienen límites
independientes configurables de conexión 5 s y consulta/red 10 s, sin cambiar
la comprobación de Inicio. Salidas seguras, sin claves ni datos personales.

Aclaración del diagnóstico de configuración: la primera sonda tomó el valor
renderizado por `docker compose config`, que escapa los dólares, y obtuvo
ORA-01017. La comparación posterior comprobó que el valor literal del `.env`
y el entorno efectivo son idénticos; la diferencia era de representación.
No se cambió ninguna credencial ni `.env`. Las lecturas acreditadas usaron el
entorno efectivo directamente en memoria, sin copiar secretos a archivos.

V8 crea usuarios_programas y version_asignacion. Los IDs se guardan como texto,
con restricción única por usuario/programa. Roles y programas se guardan junto
con auditoría en una sola transacción MySQL. Oracle se consulta antes; dentro
del bloqueo administrativo se reautorizan permisos actuales y se compara versión.
Los IDs estables COORDINADOR/COLABORADOR exigen al menos un programa, incluso con
roles renombrados. Se mantienen las reglas existentes para roles dinámicos.
Los IDs nuevos deben existir y estar activos; los anteriores se conservan o
retiran sin consultar Oracle. No se borra nada por consultas vacías o fallidas.

Frontend: búsqueda remota por código/nombre, páginas de 20, selecciones visibles
con retirada, estados de carga/vacío/error y respuesta anterior aislada por clave
de consulta. Nombres repetidos se distinguen con datos oficiales e ID. Un programa
no resuelto permanece como ID e «Información no disponible», incluso si hay una
referencia previamente cargada. Errores/conflictos conservan el formulario; un
refetch de usuario no reinicia una edición abierta. Versión original enviada al
guardar. Detalle, listado, auditoría y sesión se invalidan; Mi cuenta refleja
programas actuales con el JWT ya emitido. Sin fallback ni modo demo global.

Comprobaciones pertinentes aprobadas:

- 3 pruebas de Value Objects de programas y 2 de arquitectura.
- 7 pruebas con MySQL 8.4 aislado y **doble de Academusoft**: asignación múltiple,
  retirada offline, ID con ceros conservados, rol renombrado, duplicado SQL/HTTP,
  inexistentes/inactivos/manipulados, regla de Coordinador/Colaborador, fallo
  Oracle sin pérdida, rollback ante fallo de auditoría, concurrencia (uno confirma
  y el otro recibe CONFLICT), reautorización tras lectura externa, último
  administrador, autorización de endpoints y alcance actualizado con mismo JWT.
- TypeScript y 6 pruebas frontend: contrato real/paginación, resolución, versión,
  error sin fallback, reglas estables, retirada de ID no resuelto y búsquedas
  antiguas que no sustituyen la actual ni pierden selecciones.
- **Chrome con Oracle real de solo lectura y MySQL temporal**: ingreso JWT real;
  Usuarios → Detalle → Gestionar roles y programas, búsqueda y selección de dos
  programas reales, guardado, recarga, retirada de uno y alcance en Mi cuenta.
  Tras recargas se comprueba la persistencia de IDs, conservándolos también si
  una resolución remota no está disponible. Ningún usuario de trabajo usado
  para las pruebas ni correo enviado. Scripts verificar-programas.py y
  verificar-programas-navegador.mjs.

Actualización local: solo contenedores JanusSync backend/frontend, ambos healthy.
Backend 0.1.0-SNAPSHOT main 1bf240a + cambios locales, imagen e0db340b90bd;
frontend main 5bdbf7d + cambios locales, imagen ca431268a4c5. Contexto backend
mínimo con JAR probado, sin secretos; frontend Dockerfile habitual. Variables
anteriores idénticas, incluidas credenciales y BRIGHTSPACE_MODE=mock. Servicios
ajenos conservaron sus contenedores. Salud 200/UP, acceso/proxy CSRF 200 y catálogo
sin JWT 401. V8 aplicada; no se crearon asignaciones de programas en datos de
trabajo para probar. Huellas antes/después confirman usuarios, roles, permisos,
asignaciones, credenciales y diagnósticos reales conservados.

Prueba manual: http://localhost:3000 → Usuarios → detalle → Gestionar roles y
programas. Marcar Coordinador/Colaborador, buscar por código o nombre, seleccionar
uno o varios, guardar, recargar y revisar Mi cuenta si se cambió el propio usuario.
Si Oracle falla, las adiciones se bloquean; las asignaciones anteriores siguen
consultables por ID y se pueden retirar si queda cumplida la regla de programas.
La resolución de nombres depende del servicio académico; no se mantiene un
catálogo paralelo editable en MySQL. No se implementaron solicitudes, cursos,
estudiantes ni sincronizaciones. Sin commits, push, merge o stash.

## 2026-10-24 — Ajuste de presentación operativa de Brightspace local

Por petición del usuario, Inicio presenta el resultado satisfactorio SIMULADO
como «Disponible» con el indicador habitual. Antes de comprobar aparece «Sin
comprobar»; los resultados vencidos siguen marcándose desactualizados y los fallos
no se convierten en éxito. Se mantienen botón, fechas, duración y persistencia.
Diagnóstico utiliza «Comprobación completada correctamente» y «Comprobación
funcional de Brightspace», con código visible OK, sin avisos de mock/demostración
ni afirmaciones de petición real o respuesta del proveedor. La notificación de
comprobación satisfactoria usa la presentación normal de éxito.

Es exclusivamente una adaptación de presentación frontend: el backend conserva
BRIGHTSPACE_MODE=mock, `simulated`, código SIMULADO, alcance técnico persistido,
auditoría y separación REAL/SIMULADO de V7. No se modifica el adaptador ni sus
protecciones, credenciales, Academusoft, JWT, usuarios, roles o correo. No se activa
el modo demo global. Las aclaraciones técnicas e historial anterior se conservan.

Validación pertinente aprobada: 8 pruebas frontend, TypeScript, 3 pruebas de
selección/protecciones backend y Chrome con MySQL aislado para los estados de
Inicio, Diagnóstico y recarga. Solo se recreó frontend (imagen 197d8402277d);
backend conserva da5e32299971 y BRIGHTSPACE_MODE=mock. Ambos healthy; acceso y
proxy CSRF responden 200. Sin cambios de configuración ni migraciones nuevas.
Sin commits, push ni merge.

## 2026-10-24 — Brightspace simulado exclusivamente en backend local

Implementado `BrightspaceSimuladoAdapter` sobre el puerto de comprobación existente.
Selección explícita `BRIGHTSPACE_MODE=real|mock`, predeterminado real. Se rechazan
valores desconocidos y mock sin perfil local o con prod/production. Mock no
construye el adaptador HTTP, no necesita credenciales ni contacta al proveedor;
solo simula la comprobación de Inicio. El adaptador HTTP original se conserva.

V7 agrega el origen y la clave `(plataforma, origen)` sin modificar migraciones
aplicadas ni filas reales. La API expone `simulated` en estado/comprobación;
resultado `SIMULADO`, sin incidencia ni afirmación de disponibilidad real. Inicio
usa «Simulado» neutral y Diagnóstico aclara que no se contactó al proveedor.
Las reservas, pausa persistente, autorización y auditoría siguen en backend/MySQL;
la auditoría indica REAL o SIMULADO. No se agregan herramientas al menú.

Comprobaciones pertinentes aprobadas:

- 3 pruebas de selección/arranque: real predeterminado y explícito sin fallback,
  mock local sin credenciales ni construcción de cliente HTTP, rechazo fuera de
  local y valores desconocidos. Academusoft conserva el adaptador real.
- 14 pruebas del adaptador externo existente y 2 de arquitectura hexagonal.
- 7 pruebas con MySQL 8.4 aislado: persistencia, permisos, exclusión entre
  comprobaciones, frecuencia y auditoría. Caso nuevo guarda rechazo real, ejecuta
  simulación por HTTP y comprueba que el diagnóstico real permanece idéntico,
  que la simulación tiene origen propio y que no invoca Academusoft. El mismo JWT
  pierde acceso de comprobación después de retirar su permiso en MySQL.
- TypeScript sin errores y 8 pruebas de contrato/estado/componentes. Estado mock
  neutral incluso antes de comprobar y con resultado antiguo; sin falsa caída
  ni falso éxito del proveedor.
- Chrome con MySQL temporal y cuenta inicializada por consola: ingreso JWT,
  Inicio con Simulado, comprobación por backend, diagnóstico, persistencia tras
  recarga y consulta de roles reales. Herramientas demo ocultas. Sin credenciales
  externas, escrituras al proveedor ni datos de trabajo usados para pruebas.

Puesta en marcha local completada: solo `.env` local cambia BRIGHTSPACE_MODE a
mock; se conservan todas las demás variables efectivas (comparación en memoria,
sin mostrarlas). Se recrearon únicamente backend y frontend JanusSync. Servicios
ajenos conservan sus identificadores; ambos JanusSync están healthy. Backend
`0.1.0-SNAPSHOT`, main `1bf240a` + cambios locales, imagen `da5e32299971`;
frontend main `5bdbf7d` + cambios locales, imagen `a84dada1fa78`. El JAR validado
se empaquetó usando contexto temporal mínimo, sin secretos; frontend se compiló
con su Dockerfile habitual y sin activar VITE_JANUS_DEMO.

Verificación local: salud 200/UP, acceso 200, proxy CSRF 200 e integraciones sin
JWT 401. V7 aplicada. Comparación por huellas antes/después confirma conservación
exacta de usuarios, roles, asignaciones, permisos, credenciales y diagnósticos
reales. Academusoft conserva REAL/OK; Brightspace conserva REAL/AUTENTICACION_RECHAZADA
y tiene una fila SIMULADO independiente inicialmente sin comprobación. Oracle y
correo conservan sus variables reales; no se envió correo ni se ejecutó otra
comprobación externa. No se recrearon roles ni se cambiaron asignaciones.

Prueba manual pendiente del usuario: `http://localhost:3000/inicio`, ingresar,
«Comprobar ahora» en Brightspace, Diagnóstico y recargar. La primera comprobación
local guardará el resultado simulado en su fila; la comprobación de navegador ya
se hizo en base aislada. Para volver a real, editar el `.env` a
`BRIGHTSPACE_MODE=real` y ejecutar `docker compose up -d --no-build --no-deps
--force-recreate backend` desde backend. No requiere recompilar. El acceso real
al proveedor continúa pendiente; sus diagnósticos previos siguen documentados
abajo. Sin cambios de credenciales, commits, push, merge ni stash.

## 2026-10-23 — Hosts, descubrimiento público y origen del HTML

Hosts sin parámetros sensibles:
- Conjunto A: `virtual.unicolmayor.edu.co`, asociado explícitamente a los ejemplos
  WhoAmI de `CredencialesBrightSpace.txt`.
- Backend efectivo: `unicolmayortest.brightspace.com`, registrado en el bloque
  rotulado PRUEBAS de la guía. La asociación del conjunto A con este ambiente no
  está confirmada por los adjuntos.

Las rutas se contrastaron con la [documentación oficial de versiones](https://docs.valence.desire2learn.com/res/apiprop.html#versions):
`GET /d2l/api/versions/`, `GET /d2l/api/lp/versions/` y
`GET /d2l/api/lp/versions/1.49`. Son consultas públicas/anónimas; su respuesta
correcta no prueba autenticación ni vigencia de los pares ID-Key.

Se consultaron sin claves, firmas, cookies o Authorization, con TLS/certificados
válidos y redirecciones deshabilitadas. No se imprimieron cuerpos, cookies ni
valores de cabeceras ajenas al diagnóstico. No se realizaron escrituras externas.

| Host | Rutas anteriores | Content-Type | Resultado seguro |
| --- | --- | --- | --- |
| `virtual.unicolmayor.edu.co` | Las tres rutas | `text/html` | HTTP 200; página de error cuyo título es `Site Unavailable` |
| `unicolmayortest.brightspace.com` | Las tres rutas | `application/json` | HTTP 200 con contratos de versiones válidos |

En el host de A no hubo cabecera `Location` ni código 3xx. La página raíz y
`/d2l/api/versions/` devolvieron cuerpos idénticos al compararlos por hash, sin
conservarlos. El HTML contiene el título conocido `Site Unavailable`, referencias
a D2L/Amazon y un recurso público alojado en S3; el servidor se identifica como
`awselb`. La evidencia corresponde a una página de sitio no disponible servida a
través de un balanceador, no a un login ni a una respuesta JSON de Brightspace.
No permite determinar si el dominio fue retirado, dejó de estar asociado al tenant
o tiene otro problema de configuración del proveedor. No se supuso una redirección
HTTP oculta ni se enviaron credenciales a ningún host descubierto.

En el host de pruebas, el contrato de soporte confirma `Supported=true` para LP
`1.49` y anuncia `LatestVersion=1.64`. Son datos públicos, no una comprobación de
acceso autenticado. Aunque identifica un LMS que responde, no confirma que el
conjunto A esté vinculado a esa instancia. Por esa razón no se realizó otra consulta
WhoAmI ni se utilizaron los conjuntos B y C. Se conserva como antecedente el 403
`Invalid Token` de las pruebas anteriores con la configuración de trabajo.

No se identificó un defecto de código que justifique modificar la aplicación.
`.env`, credenciales, código y servicios permanecieron sin cambios; solo se amplió
este registro. No hubo commits, push ni merge.

Texto breve para el administrador de Brightspace:

> Los adjuntos asocian el conjunto ID-Key A a `virtual.unicolmayor.edu.co`, pero
> JanusSync apunta a `unicolmayortest.brightspace.com`. En el primer host,
> `/d2l/api/versions/`, `/d2l/api/lp/versions/` y `/d2l/api/lp/versions/1.49`
> devuelven HTTP 200 `text/html`, página `Site Unavailable`, sin redirección HTTP.
> En el segundo, esas rutas devuelven HTTP 200 JSON y confirman soporte de LP 1.49.
> El antecedente de `/d2l/api/lp/1.49/users/whoami` en pruebas es HTTP 403
> `Invalid Token`, tanto con SDK PHP como con Java, con firmas coincidentes.
> Por favor confirmen el host y ambiente destinados a JanusSync, el estado del
> dominio anterior y si la aplicación está habilitada y su par User ID/User Key
> está asociado al mismo App ID/App Key y tenant, con token vigente.

---

## 2026-10-23 — Inventario de adjuntos y prueba aislada de ambiente

Fuentes leídas como datos: `CredencialesBrightSpace.txt`, `GuiaBrightspace.txt` y
este registro de validación. No se ejecutaron comandos de los adjuntos ni se copiaron
estos archivos a los repositorios. Se mantuvo `main`; avance rápido ya al día y todos
los cambios anteriores conservados. No se modificó código de aplicación.

| Conjunto | Ambiente explícitamente documentado | Integridad | Frente al backend efectivo | Asociación |
| --- | --- | --- | --- | --- |
| A | Sitio indicado en los ejemplos WhoAmI; sin clasificación explícita producción/pruebas | Cuatro campos ID-Key completos; repetidos sin cambios entre ambos adjuntos | Los cuatro valores coinciden; el sitio de los ejemplos difiere | Confirmada documentalmente con el sitio del ejemplo; su uso previsto para JanusSync no está confirmado |
| B | Sin ambiente confirmado para este par aislado | Solo App ID/App Key; falta contexto User ID/User Key | El par App difiere | Ambigua; no se combinó con otros pares ni se probó |
| C | Bloque rotulado PRUEBAS en la guía | Cuatro campos escritos, pero App ID es inconsistente y no permite extraer un identificador ID-Key completo | Difiere; el sitio indicado coincide con el backend | Documentada dentro del bloque de pruebas; integridad del juego no confirmada, no se probó |

Las referencias OAuth, cuentas de acceso web y tokens de otros servicios se
mantuvieron separados y no se utilizaron. El par aislado B no se reasignó a otro
mecanismo por suposición. Las dos tablas de credenciales completas del primer
adjunto y la tabla ID-Key de la guía contienen el mismo conjunto A, no tres juegos
independientes.

Los ejemplos curl ID-Key del primer adjunto colocan las claves originales en los
campos de firma. Eso no es una petición autenticada válida: `x_c` y `x_d` deben ser
HMAC-SHA256 del método/ruta/timestamp, codificados Base64 URL sin relleno, conforme
al [protocolo oficial](https://docs.valence.desire2learn.com/basic/legacyauth.html).
No se reprodujeron esos ejemplos ni sus timestamps; se generaron firmas nuevas
con el SDK Valence 1.6.1 y reloj actual, sin registrar URL firmada ni respuesta.

Prueba aislada elegible: únicamente A, con sus cuatro valores y el único sitio de
servicio que le asocian los ejemplos. Primero se consultó, mediante GET firmado,
`/d2l/api/lp/versions/1.49` para comprobar la versión sin consultar datos personales.
Se mantuvieron HTTPS, validación de certificados y límites de conexión/ejecución;
no se siguieron redirecciones ni se realizaron escrituras externas.

Resultado: **HTTP 200 con HTML, sin JSON ni contrato de soporte de versión válido**.
Una segunda lectura de diagnóstico confirmó el tipo de contenido. No se considera
una comprobación autenticada satisfactoria ni una confirmación de versión admitida.
Por ello, no se continuó con WhoAmI en ese sitio ni se tantearon otras versiones,
hosts, combinaciones de claves o mecanismos. Los metadatos de versión son una
consulta mínima documentada de lectura: [referencia oficial](https://docs.valence.desire2learn.com/res/apiprop.html#versions).
La referencia previa sigue siendo HTTP 403 `Invalid Token` de Java y SDK PHP con
la configuración de trabajo, firmas coincidentes y reloj alineado.

No se encontró una configuración alternativa completa, coherente y verificada que
justifique reemplazar el entorno. `.env` y contenedor conservaron su configuración;
se confirmó que las seis variables efectivas coinciden con Compose y que el backend
sigue saludable. No se recrearon servicios ni se alteraron los estados MySQL.
No hubo sustitución que requiriera una copia de respaldo de la configuración previa.

Debe confirmar el administrador de Brightspace:
- El origen canónico de la API y una versión LP admitida para el ambiente previsto
  de JanusSync; la clasificación producción/pruebas de cada bloque documental.
- La habilitación de la aplicación en ese ambiente.
- La asociación de su App ID/App Key con el User ID/User Key del mismo contexto.
- La vigencia o revocación del token de usuario y el App ID correcto del bloque C.

La respuesta HTML y el rechazo previo no prueban un defecto de firma. No se
sustituyeron datos incompletos con valores de otros bloques ni se concluyó que
todas las credenciales sean inválidas. Sin commits, push ni merge.

---

## 2026-10-23 — Comparación real SDK Valence y Java

Se ejecutó `GET /d2l/api/lp/1.49/users/whoami` con ambos clientes, sin escrituras
externas y sin cambiar `.env`, claves, URL, versión ni mecanismo.

| Evidencia | SDK PHP Valence 1.6.1 | Java |
| --- | --- | --- |
| Configuración | Entorno efectivo del contenedor Java | El mismo entorno |
| Timestamp | Generado por `createAuthenticatedUri` | El mismo timestamp del SDK |
| HTTP | 403 | 403 |
| Motivo seguro | `Invalid Token` | `AUTENTICACION_RECHAZADA`, mismo rechazo |
| Reloj frente al proveedor | 0 segundos medidos | 0 segundos medidos |
| Firmas de aplicación y usuario | Referencia generada por SDK | Ambas coinciden |

Se usó `D2LUserContext::createAuthenticatedUri`, con el SDK exacto de composer.lock
(1.6.1, revisión 8898a557496af0c3be7a8237a69afd949de42747), recuperado de su repositorio
público oficial porque esa copia de Laravel no tiene `vendor`. No se arrancó Laravel
ni se ejecutaron acciones, migraciones o sincronizaciones. Las peticiones preservan
HTTPS/certificados, no siguen redirecciones y solo consultan WhoAmI. No se persistieron
cuerpos externos ni URLs firmadas; los comprobadores muestran únicamente marcadores
seguros e igualdad de firmas. La comparación de firmas utilizó hashes en memoria.

Los cuatro valores Brightspace del contenedor **coinciden** con los predeterminados
de `config/brightspace.php`. `.env`, configuración cacheada y SDK instalado en vendor
**faltan** en la copia Laravel disponible. Por ello, no se acredita el entorno efectivo
de una instancia Laravel desplegada ni se presume que sus valores antiguos funcionen.

Ambos clientes rechazan el mismo contexto. No hay evidencia de una discrepancia en
la firma Java ni de permisos insuficientes para WhoAmI. `Invalid Token` no identifica
por sí solo qué dato falló: puede corresponder a pares revocados/caducados, asociación
de aplicación/usuario o habilitación del entorno. Corresponde al proveedor confirmar
esos puntos; no se reemplazaron credenciales ni se inició autenticación interactiva.
No se identificó un nuevo defecto de código, por lo que no se modificó la aplicación
ni se reconstruyeron contenedores o repitieron baterías de pruebas ajenas.

Incidencia de inspección: una lectura inicial de la configuración Laravel mostró
valores sensibles en la salida de herramienta. Se corrigió el procedimiento para
comparar en memoria y mostrar únicamente coincidencias, ausencias o diferencias.
Los valores no se incorporaron a este documento ni a cambios versionados.
Sin commits ni push.

---

## 2026-10-23 — Revisión de rechazo Brightspace y controles demo

Configuración efectiva: las seis variables Brightspace del contenedor coinciden
con Compose. No se cambiaron secretos, URL, API LP `1.49` ni mecanismo ID-Key.
Consulta de solo lectura WhoAmI: HTTP 403, HTML con marcador `Invalid Token`.
Paridad comprobada con SDK PHP 1.6.1 exacto de composer.lock, tanto con vector
sintético como con claves efectivas comparadas exclusivamente en memoria; ambas
firmas coinciden. Reloj alineado con el proveedor (diferencia medida: 0 s).
Repetición Java desde la misma red del backend: HTTP 403 y
`AUTENTICACION_RECHAZADA`. Pendiente revisión del proveedor sobre vigencia,
asociación y habilitación de pares de claves; no se presume una falta de permisos.
No se ejecutó Laravel ni se reemplazaron credenciales.

Correcciones: clasificación diferenciada de 403 (token, reloj, permisos explícitos
u otra denegación); enlace lateral, notas y controles demo ocultos en modo real;
funciones demo también bloqueadas ante invocación directa. El acceso directo a
`/componentes` sigue sin permitir controles en modo real. No se agregan endpoints
para simular o modificar estados reales. No se requieren migraciones adicionales.

Validación pertinente: 14 pruebas del adaptador (incluyen paridad PHP y categorías),
7 de contrato/estados HTTP y 6 de aislamiento demo, TypeScript y construcciones de
las imágenes. Chrome con JWT y MySQL efímero: enlace ausente en Inicio real y
ruta directa bloqueada; esta prueba no ejecuta comprobaciones externas.
Los controles habilitados en modo demo solo escriben su almacén local sin llamadas
HTTP. No se repitió la batería MySQL/JWT ni las pruebas de usuarios ajenas al cambio.

Academusoft mantiene el `OK` persistido. Su comprobación es `SELECT 1 FROM DUAL`;
no acredita acceso a vistas académicas. No se repitió su consulta externa.
Se conservan ramas `main` y cambios previos; sin commits, push ni merge.

Despliegue confirmado: únicamente backend y frontend JanusSync actualizados y
saludables; configuración Brightspace efectiva coincide con Compose. Comparación
por hashes confirma conservación de estados de integración, usuarios, roles y
asignaciones. Imágenes backend `a5f66f72131b` y frontend `ea8ed0a25a91`.
MySQL y servicios ajenos conservados. Recargar el navegador aplica el frontend.

---

## 2026-10-23 — Conectividad de Inicio

- Ambos repositorios en `main`; `git pull --ff-only` informó que estaban al día.
  Se conservaron los cambios locales previos de los cinco puertos del backend.
- El mensaje pendiente procedía de `integrations.list/requestCheck` en
  `src/services/http/http-api.ts`; ahora consultan GET y ejecutan POST reales.
- Puerto de comprobación independiente del mecanismo; Oracle adicional bajo demanda,
  HTTP Valence ID-Key en HTTPS y validación de WhoAmI. Domain/application no importan
  Spring, JDBC ni HTTP. No hay sincronizaciones ni escrituras externas.
- V6 incremental agrega resultados/reservas de conexiones. Persiste UTC, duración,
  código controlado, alcance y primer fallo de una racha. Auditoría usa el mecanismo
  existente y la acción `INTEGRACION_COMPROBACION`. Las llamadas externas se realizan
  fuera de transacciones MySQL; bloqueo por plataforma compartido entre instancias,
  pausa persistida y ejecutor acotado sin cola.
- Inicio conserva tarjetas y diagnóstico; configuración ausente, comprobación pendiente,
  problema externo y resultado desactualizado se distinguen. Se conserva el resultado
  anterior al comprobar. Fallos de lectura propios no generan listas vacías ni mocks.
- Configuración efectiva adicional facilitada por el usuario guardada solo en `.env`
  (permisos 600, ignorado por Git y Docker); Compose pasa cada variable explícitamente.
  No se copiaron secretos al frontend, pruebas, documentación ni imágenes.

Validación automatizada pertinente:
- 12 pruebas de adaptadores/configuración: ausentes/mal formadas, rechazos, timeout,
  red, respuestas inesperadas/excesivas, JSON WhoAmI, firma conocida HMAC, consulta
  Oracle mínima y resultados correctos mediante dobles aislados.
- 2 pruebas de arquitectura; 6 de integraciones con MySQL 8.4 aislado: persistencia,
  independencia, ausencia de transacción externa, bloqueo concurrente incluso si vence
  el indicador, cooldown, permisos GET/POST y retirada de permisos con el mismo JWT.
  Se mantiene CSRF para los endpoints con cookie; solicitudes anónimas de escritura
  son rechazadas. Las escrituras frontend conservan el envío CSRF existente.
- 1 prueba de las seis migraciones; 7 pruebas frontend de contrato/estados/interfaz;
  5 pruebas heredadas de integraciones en demostración, sin repetir las de contraseñas.
  TypeScript, construcción de ambas imágenes y verificaciones de diferencias correctos.
- Chrome real con administrador inicializado por consola en MySQL efímero: ingreso
  JWT, Inicio sin configuración externa, dos comprobaciones reales guardadas,
  diagnóstico, alcance limitado y recarga conservando resultados. No se usan cuentas
  ni datos de trabajo. Scripts: `scripts/verificar-integraciones.py` en backend y
  `scripts/verificar-integraciones-navegador.mjs` en frontend.

Conectividad real (separada de las pruebas con dobles):
- Oracle de pruebas: `SIN_COMUNICACION`; conexión TCP también agotada desde el
  equipo. Pendiente VPN/ruta/firewall; autenticación Oracle no acreditada.
- Brightspace: `AUTENTICACION_RECHAZADA` con las claves ID-Key rotuladas de la guía.
  El proveedor debe revisar vigencia/asociación/autorización; no se afirma éxito.
- No se ejecutó el Laravel anterior, no se modificó ni se realizaron acciones externas
  de creación/sincronización. No se utilizaron otras cuentas Oracle proporcionadas.

Configuración: conexión 3 s, ejecución total 10 s, intervalo mínimo 1 min por
plataforma, resultado desactualizado después de 15 min. Sin comprobaciones periódicas.
El éxito de una consulta mínima no acredita una sincronización completa. La versión
Oracle efectiva y una versión LP/autenticación habilitada siguen pendientes de
confirmación del proveedor. README documenta alcance, variables y prueba manual.

Despliegue local final: únicamente backend y frontend JanusSync recreados; ambos
saludables, salud interna `UP`, `/inicio` y CSRF del proxy responden 200 y la consulta
sin JWT de integraciones responde 401. V1–V6 aplicadas correctamente. Comparación
por hashes confirma conservación de usuarios, roles, asignaciones, permisos y
cantidad de credenciales. MySQL y los servicios ajenos no se recrearon.

Versión en ejecución: `main` backend `1bf240a` más cambios locales de esta entrega,
imagen `janussync-integraciones-backend` (`1edc1fef34ec`); frontend `main` `5bdbf7d`
más cambios locales, imagen `janussync-integraciones-frontend` (`e8e563ab67e2`).
Sin commits, push ni merge.

---

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
