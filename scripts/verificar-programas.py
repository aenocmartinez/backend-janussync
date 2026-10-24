# Prueba aislada de Inicio: JAR, imágenes janussync-integraciones y Chrome; sin credenciales externas.
import sys,os,subprocess,tempfile,time,json,secrets,urllib.request,urllib.error,http.cookiejar,pty,select,pathlib,shutil
ROOT=pathlib.Path(__file__).resolve().parents[2]
PREFIX='janussync-programas-recorrido-'+secrets.token_hex(4)
FRONTEND_IMAGE=os.environ.get('JANUS_TEST_FRONTEND_IMAGE','janussync-programas-frontend')
BACKEND_IMAGE=os.environ.get('JANUS_TEST_BACKEND_IMAGE','janussync-programas-backend')
keys=pathlib.Path(tempfile.mkdtemp(prefix='janussync-jwt-claves-'))
password=secrets.token_urlsafe(32)
admin_password=secrets.token_urlsafe(24)
names=[]
network_created=False
def docker(*args,env=None):
    result=subprocess.run(['docker',*args],stdout=subprocess.PIPE,stderr=subprocess.PIPE,env=env)
    if result.returncode: raise RuntimeError('Falló Docker: '+args[0])
    return result.stdout.decode().strip()
try:
    for kind,args in [('private',['genpkey','-algorithm','RSA','-pkeyopt','rsa_keygen_bits:3072','-out',str(keys/'jwt-private.pem')]),('public',['pkey','-in',str(keys/'jwt-private.pem'),'-pubout','-out',str(keys/'jwt-public.pem')])]:
        subprocess.run(['openssl',*args],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,check=True)
    for p in keys.iterdir(): p.chmod(0o600)
    docker('network','create',PREFIX)
    network_created=True
    env=dict(os.environ,MYSQL_PASSWORD=password,MYSQL_ROOT_PASSWORD=secrets.token_urlsafe(32),DB_PASSWORD=password)
    mysql=PREFIX+'-mysql';names.append(mysql)
    docker('run','-d','--name',mysql,'--network',PREFIX,'--tmpfs','/var/lib/mysql','-e','MYSQL_ROOT_PASSWORD','-e','MYSQL_DATABASE=prueba','-e','MYSQL_USER=prueba','-e','MYSQL_PASSWORD','mysql:8.4',env=env)
    for attempt in range(60):
        result=subprocess.run(['docker','exec','-e','MYSQL_PWD',mysql,'mysql','-uprueba','prueba','-e','SELECT 1'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,env=dict(env,MYSQL_PWD=password))
        if result.returncode==0: break
        time.sleep(1)
    backend=PREFIX+'-backend'
    front=PREFIX+'-frontend';names.append(front)
    docker('run','-d','--name',front,'--network',PREFIX,'-p','127.0.0.1::3000','-e',f'JANUS_BACKEND_URL=http://{backend}:8080',FRONTEND_IMAGE)
    port=docker('port',front,'3000/tcp').rsplit(':',1)[1]
    origin='http://127.0.0.1:'+port
    common=['--network',PREFIX,'--user',f'{os.getuid()}:{os.getgid()}','-v',f'{ROOT}/backend-janussync/target/janussync.jar:/app/janussync.jar:ro','-v',f'{keys}:/run/keys:ro',
        '-e','SPRING_PROFILES_ACTIVE=local,jwt','-e',f'DB_HOST={mysql}','-e','DB_NAME=prueba','-e','DB_USER=prueba','-e','DB_PASSWORD','-e','DB_SSL_MODE=REQUIRED',
        '-e','JANUS_JWT_PRIVATE_KEY=/run/keys/jwt-private.pem','-e','JANUS_JWT_PUBLIC_KEY=/run/keys/jwt-public.pem','-e','JANUS_COOKIE_SECURE=false','-e','JANUS_ALLOW_NO_CAPTCHA_LOCAL=true',
        '-e','MAIL_ENABLED=false','-e',f'JANUS_FRONTEND_PUBLIC_URL={origin}']

    if os.environ.get('TEST_JANUS_BRIGHTSPACE_MOCK') == 'true':
        common += ['-e','BRIGHTSPACE_MODE=mock']

    # Use the effective backend connection only in memory. No secrets in arguments/files.
    actual=json.loads(docker('inspect','backend-janussync-backend-1'))[0]
    efectiva=dict(v.split('=',1) for v in actual['Config']['Env'])
    for nombre in ['ACADEMUSOFT_ORACLE_URL','ACADEMUSOFT_ORACLE_USERNAME','ACADEMUSOFT_ORACLE_PASSWORD']:
        env[nombre]=efectiva[nombre]; common+=['-e',nombre]
    common+=['-e','BRIGHTSPACE_MODE=mock']

    # Ejecutar el comando real de inicialización en una consola, sin imprimir ni guardar la contraseña.
    master,slave=pty.openpty()
    bootstrap=PREFIX+'-bootstrap';names.append(bootstrap)
    proc=subprocess.Popen(['docker','run','--rm','-it','--name',bootstrap,*common,'--entrypoint','java',BACKEND_IMAGE,'-Duser.timezone=America/Bogota','-jar','/app/janussync.jar','--spring.main.web-application-type=none','--janus.inicializar-admin=true'],stdin=slave,stdout=slave,stderr=slave,env=env)
    os.close(slave)
    buffer=b''
    prompts=[(b'Nombres:',b'Persona\n'),(b'Apellidos:',b'Recorrido\n'),(b'Correo:',b'recorrido@pruebas.invalid\n'),('Contraseña ('.encode(),admin_password.encode()+b'\n'),('Confirmar contraseña:'.encode(),admin_password.encode()+b'\n')]
    deadline=time.monotonic()+90
    while prompts and time.monotonic()<deadline:
        if select.select([master],[],[],1)[0]:
            try: buffer+=os.read(master,65536)
            except OSError: break
            if prompts[0][0] in buffer:
                _,answer=prompts.pop(0);os.write(master,answer);buffer=b''
        if proc.poll() is not None: break
    proc.wait(timeout=30);os.close(master)
    if prompts or proc.returncode:
        diagnostico=buffer.decode(errors='replace')
        for secreto in [password,admin_password,env.get('MAIL_PASSWORD','')]:
            if secreto: diagnostico=diagnostico.replace(secreto,'[protegido]')
        pathlib.Path('/tmp/janussync-bootstrap-error.txt').write_text(diagnostico)
        raise RuntimeError('La inicialización por consola no completó correctamente; diagnóstico redactado en /tmp')
    print('Inicialización real por consola: correcta, contraseña oculta')
    names.append(backend)
    docker('run','-d','--name',backend,*common,BACKEND_IMAGE,env=env)
    for attempt in range(60):
        result=subprocess.run(['docker','exec',backend,'curl','--silent','--fail','http://localhost:8080/actuator/health'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        if result.returncode==0: break
        time.sleep(1)
    else: raise RuntimeError('El backend de prueba no inició')
    browser=subprocess.run([shutil.which('node'),str(ROOT/'frontend-janussync/scripts/verificar-programas-navegador.mjs')],env=dict(os.environ,TEST_JANUS_ORIGIN=origin,TEST_JANUS_PASSWORD=admin_password,TEST_JANUS_DEMO_ISOLATION=os.environ.get("TEST_JANUS_DEMO_ISOLATION","false")),timeout=180)
    assert browser.returncode==0, 'Falló Inicio en Chrome'

finally:
    for name in reversed(names): subprocess.run(['docker','rm','-f',name],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    if network_created: subprocess.run(['docker','network','rm',PREFIX],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    shutil.rmtree(keys)
