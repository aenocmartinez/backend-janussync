# Conexión institucional Brightspace mediante OAuth 2.0

Esta entrega permite autorizar una conexión compartida de solo lectura. El inicio
de sesión JWT de JanusSync sigue siendo independiente. **Validación real completada el 2026-10-30:** después del consentimiento del
usuario `whoami` respondió OK por OAuth con un access token vigente.
No se forzó ni se acredita una renovación real en esta comprobación.
Este equipo quedó en `BRIGHTSPACE_MODE=real`, sin definiciones duplicadas.
No se usaron contraseñas de Brightspace desde JanusSync.

## Documentación del proveedor consultada el 2026-10-30

- [OAuth y endpoints oficiales](https://docs.valence.desire2learn.com/basic/oauth2.html):
  autorización `https://auth.brightspace.com/oauth2/auth` e intercambio/renovación
  `https://auth.brightspace.com/core/connect/token`.
- [Ejemplo oficial Authorization Code](https://community.d2l.com/brightspace/kb/articles/21863-how-to-get-started-with-oauth-2-0):
  `response_type=code`, `client_id`, `redirect_uri`, `scope` y `state`;
  intercambio POST de formulario con `grant_type=authorization_code`, `code`,
  `redirect_uri` y autenticación HTTP Basic del cliente.
- [Refresh tokens](https://community.d2l.com/brightspace/kb/articles/1196-how-to-obtain-an-oauth-2-0-refresh-token):
  habilitarlos en el registro. Son de un solo uso; renovar antes de caducar.
  Se usa `grant_type=refresh_token` y `refresh_token` con el mismo cliente.
- [Consulta de identidad](https://docs.valence.desire2learn.com/res/user.html#get--d2l-api-lp-(version)-users-whoami):
  `GET /d2l/api/lp/{version}/users/whoami`, Bearer, scope mínimo
  `users:own_profile:read` (también permite `users:profile:read`).

No se confirmó soporte PKCE en estas fuentes; no se envían parámetros PKCE
supuestos. No se pide `offline_access`, no documentado como requisito en las
fuentes anteriores. La cuenta consentidora necesita permisos propios en D2L:
los scopes no sustituyen los permisos. Se respeta `expires_in` de la respuesta,
no se supone que el access token dure exactamente los 3600 segundos registrados.

## Configuración exclusivamente en backend

Mantener el `.env` existente. Completar localmente estas variables, sin copiarlas
al chat ni al frontend:

| Variable | Valor o uso |
| --- | --- |
| `BRIGHTSPACE_MODE` | Mantener `mock` hasta completar autorización e identidad |
| `BRIGHTSPACE_BASE_URL` | Origen HTTPS del tenant, sin rutas ni query |
| `BRIGHTSPACE_API_VERSION` | Versión LP compatible con ese tenant y `whoami` |
| `BRIGHTSPACE_OAUTH_CLIENT_ID` | Client ID del registro OAuth actual |
| `BRIGHTSPACE_OAUTH_CLIENT_SECRET` | Secret del mismo registro |
| `BRIGHTSPACE_OAUTH_REDIRECT_URI` | Exactamente `https://janussync.test/callback` |
| `BRIGHTSPACE_OAUTH_SCOPES` | `users:own_profile:read`, registrado y autorizado en D2L |
| `JANUS_OAUTH_KEY_FILE` | `/run/keys/brightspace-oauth.key`, ruta dentro del contenedor |
| `JANUS_KEYS_DIR` | Directorio externo existente que Compose monta en `/run/keys` |
| `JANUS_CONNECTION_TIMEOUT` / `JANUS_CHECK_TIMEOUT` | Límites existentes, por defecto PT3S / PT10S |

No reutilizar APP_ID/APP_KEY/USER_ID/USER_KEY como credenciales OAuth. El adaptador
Valence anterior se conserva para no borrar cambios locales, pero `real` ya
selecciona OAuth; no hay fallback a Valence ni al mock. Los scopes de esta entrega
se limitan a identidad. No se crean usuarios, cursos, matrículas ni contenido.

En este equipo ya se creó `brightspace-oauth.key` con permisos 600 dentro del
`JANUS_KEYS_DIR` existente, y se añadió **solo** `JANUS_OAUTH_KEY_FILE` al `.env`.
No se sobrescribió una clave existente. Para preparar otro equipo, desde backend,
este comando lee únicamente la ruta configurada y crea la clave sin imprimirla:

```bash
python3 - <<'PYKEY'
from pathlib import Path
import os, base64
line = next(x for x in Path('.env').read_text().splitlines() if x.startswith('JANUS_KEYS_DIR='))
directory = Path(line.split('=', 1)[1].strip().strip('"').strip("'")).expanduser().resolve()
assert directory.is_dir()
assert Path.cwd().resolve() != directory and Path.cwd().resolve() not in directory.parents
fd = os.open(directory / 'brightspace-oauth.key', os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
with os.fdopen(fd, 'w') as f:
    f.write(base64.b64encode(os.urandom(32)).decode() + '\n')
PYKEY
```

Después establecer `JANUS_OAUTH_KEY_FILE=/run/keys/brightspace-oauth.key` en `.env`.
El comando falla si ya existe el archivo: no regenerarlo con tokens persistidos.
Conservar respaldo seguro de esta clave junto a la recuperación de la base;
sin ella los tokens no se pueden descifrar y se requiere nueva autorización.
No versionarla ni ponerla dentro de un contexto de build. Las claves JWT no cambian.

## Autorizar y después activar explícitamente

HTTPS/hosts/confianza local deben estar preparados según
[HTTPS-LOCAL.md](../frontend-janussync/HTTPS-LOCAL.md).
Desde backend, con configuración OAuth completa **y todavía modo mock**:

```bash
docker compose -f compose.yaml -f compose.https.yaml up --build -d backend
```

Desde frontend:

```bash
docker compose -f compose.yaml -f compose.https.yaml up --build -d
```

1. Entrar en JanusSync con permisos `INTEGRACIONES_CONSULTAR` y
   `INTEGRACIONES_COMPROBAR` y abrir Inicio → diagnóstico de Brightspace.
2. Pulsar **Autorizar Brightspace**. El POST usa JWT y CSRF. JanusSync devuelve
   solo la URL oficial de autorización y una cookie temporal de correlación.
3. Ingresar/consentir personalmente en Brightspace. JanusSync no automatiza ni
   recibe tu contraseña del proveedor.
4. El callback consume el intento, intercambia el código en backend, consulta
   `whoami` y revalida la sesión/permiso iniciadores. Solo entonces persiste la
   autorización. Retorna siempre a `/inicio?plataforma=brightspace`, sin código,
   token ni mensajes del proveedor en la URL. Un rechazo nunca anuncia éxito.
5. Confirmar **Autorización completada**. Mientras siga `mock`, la comprobación
   permanece simulada y se informa expresamente; eso no acredita conectividad real.
6. Cambiar **explícitamente** `BRIGHTSPACE_MODE=real` en `.env` y recrear backend:

   ```bash
   docker compose -f compose.yaml -f compose.https.yaml up -d --force-recreate backend
   ```

7. Pulsar **Comprobar ahora** y confirmar un nuevo resultado `OK` de la consulta
   autenticada, con su fecha. El callback validó identidad, pero no genera un
   resultado de comprobación manual. Un éxito previo a la autorización nueva no
   se presenta como evidencia vigente; los resultados simulados siguen separados.

No activar `VITE_JANUS_DEMO`. La API real del frontend, JWT, correo, Oracle y
MySQL externo se mantienen en sus modos actuales. Si falta configuración, la
acción queda deshabilitada y la conexión no se sustituye automáticamente.

## Reautorizar, fallos y regreso al mock

Usar de nuevo **Autorizar Brightspace** ante revocación, expiración sin refresh,
un cambio de cliente/scopes/tenant o una renovación incierta. Cada intento dura
cinco minutos y solo funciona en el navegador que lo inició. Un segundo inicio
sustituye la cookie temporal; volver atrás o reutilizar un callback no lo reactiva.
La conexión anterior se conserva si la nueva autorización falla.

- `AUTORIZACION_PENDIENTE`: falta completar un intento válido.
- `AUTORIZACION_REVOCADA`: token rechazado, refresh inválido o ausencia de renovación.
- `PERMISOS_INSUFICIENTES`: D2L respondió 403 o rechazó scopes; revisar registro y rol.
- `CONFIGURACION_INVALIDA`: cliente/secret/configuración rechazados.
- `SIN_COMUNICACION` / `TIEMPO_AGOTADO`: fallo de transporte, sin afirmar autorización.
- Error durante un refresh enviado: se exige reautorizar, sin reintentar un token
  de un solo uso cuya recepción por el proveedor sea incierta.

Para volver explícitamente al mock, establecer `BRIGHTSPACE_MODE=mock` y recrear
solo backend con el comando anterior. Solo está permitido con perfil `local`,
sin `prod`/`production`. Esto no borra ni revoca el consentimiento D2L ni las
credenciales cifradas. Para revocar el consentimiento en el proveedor, seguir
[las instrucciones oficiales](https://docs.valence.desire2learn.com/basic/oauth2.html#revoking-an-application-s-consent).
No revertir/borrar V11: es aditiva y puede permanecer al volver al mock.

## Persistencia y límites de confianza

`AutorizacionExternaPort` y su repositorio no dependen de Spring, HTTP ni D2L.
La coordinación vive en application; HTTP OAuth/Bearer, AES-GCM y MySQL viven en
infrastructure. Se reutilizan permisos, comprobadores, diagnóstico, proxy API,
CSRF y consultas de sesión existentes.

V11 agrega `oauth_intentos` y `oauth_conexion`. Solo se guardan hashes de state
y navegador, usuario/sesión iniciadores, expiración/configuración, diagnóstico
controlado y tokens cifrados. Access/refresh usan AES-256-GCM con IV aleatorio y
contexto distinto por columna. No se persisten datos personales de `whoami`.
Los intentos antiguos se eliminan oportunistamente al iniciar otro, sin cron.

La cookie `__Secure-JANUS-OAUTH` usa Secure, HttpOnly, SameSite=Lax,
Path=/callback, sin Domain y Max-Age=300; las cookies JWT/CSRF mantienen Strict.
El callback GET público exige state+cookie propios, consume el intento incluso
ante rechazo y comprueba permisos vigentes antes del intercambio y de la activación.
No se usa HttpSession ni se desactiva CSRF globalmente.

La exclusión de callback/refresh usa `GET_LOCK` de MySQL, ligada a la base, entre
instancias; el consumo de intentos usa bloqueo transaccional de fila. Se mantiene
una conexión física para el lock y se usan escrituras autocommit para la marca
`RENOVANDO` antes de llamar a D2L. Un crash no permite reenviar ese refresh.
Si otra instancia está operando, se rechaza temporalmente la comprobación; no
hay bucles ni cola de reintentos. Si el proveedor omite un nuevo refresh se
conserva el anterior, como exige el contrato; si lo devuelve, se reemplaza.

URLs de proveedor y retorno son fijas/configuradas, nunca derivadas de encabezados
Forwarded. TLS se valida normalmente. Nginx no registra accesos ni errores de
upstream de `/callback`; no habilitar trazas de cuerpos/cabeceras HTTP del cliente
Java ni exportar HAR con credenciales. Respuestas, auditoría y errores usan códigos
controlados, nunca cuerpos del proveedor, códigos OAuth ni tokens.

## Verificaciones automáticas y prueba pendiente

Las pruebas usan dobles del proveedor y MySQL efímero de Testcontainers, nunca
la cuenta D2L ni MySQL externo. Cubren intentos inválidos/vencidos/reutilizados,
correlación de navegador, rechazo del proveedor, permisos/revalidación, identidad,
cifrado autenticado, refresh omitido/rotado/revocado/ambiguo, concurrencia entre
instancias y persistencia de la marca de renovación. Se comprueban JWT/CSRF/logout
con el contexto Spring real y el callback limpio sin cookies JWT.

Con Java 21 y Docker, desde backend:

```bash
./mvnw -Pmysql-it \
  -Dtest=AutorizarConexionTest,CifradoCredencialesTest,BrightspaceOAuthAdapterTest,AutorizacionCallbackTest,BrightspaceModoTest,ArquitecturaTest \
  '-Dit.test=AutorizacionExternaMySqlIT,AutenticacionJwtIT,PersistenciaMySqlIT#migracionesCarganCuatroRolesSinCredenciales' verify
```

Desde frontend:

```bash
npm run typecheck
npm test -- src/test/brightspace-authorization.test.tsx src/test/http-integrations.test.tsx src/test/http-auth.test.ts src/test/server-proxy.test.ts
```

Queda pendiente **la autorización interactiva y “Comprobar ahora” contra Brightspace**.
No confundir las pruebas aisladas con una conexión acreditada al proveedor.
