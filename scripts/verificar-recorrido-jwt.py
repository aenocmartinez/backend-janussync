# Prueba aislada: requiere target/janussync.jar, imagen janussync-jwt-frontend y Chrome.
import sys,os,subprocess,tempfile,time,json,secrets,urllib.request,urllib.error,http.cookiejar,pty,select,pathlib,shutil
ROOT=pathlib.Path(__file__).resolve().parents[2]
PREFIX='janussync-jwt-recorrido-'+secrets.token_hex(4)
SOLO_GESTION='--gestion-roles' in sys.argv
SOLO_ROLES='--roles' in sys.argv or SOLO_GESTION
FRONTEND_IMAGE=os.environ.get('JANUS_TEST_FRONTEND_IMAGE','janussync-jwt-frontend')
BACKEND_IMAGE=os.environ.get('JANUS_TEST_BACKEND_IMAGE','janussync-jwt-backend')
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
        result=subprocess.run(['docker','exec',mysql,'mysqladmin','ping','--silent'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        if result.returncode==0: break
        time.sleep(1)
    common=['--network',PREFIX,'--user',f'{os.getuid()}:{os.getgid()}','-v',f'{ROOT}/backend-janussync/target/janussync.jar:/app/janussync.jar:ro','-v',f'{keys}:/run/keys:ro',
        '-e','SPRING_PROFILES_ACTIVE=local,jwt','-e',f'DB_HOST={mysql}','-e','DB_NAME=prueba','-e','DB_USER=prueba','-e','DB_PASSWORD','-e','DB_SSL_MODE=REQUIRED',
        '-e','JANUS_JWT_PRIVATE_KEY=/run/keys/jwt-private.pem','-e','JANUS_JWT_PUBLIC_KEY=/run/keys/jwt-public.pem','-e','JANUS_COOKIE_SECURE=false','-e','JANUS_ALLOW_NO_CAPTCHA_LOCAL=true']
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
    if prompts or proc.returncode: raise RuntimeError('La inicialización por consola no completó correctamente')
    print('Inicialización real por consola: correcta, contraseña oculta')
    backend=PREFIX+'-backend';names.append(backend)
    docker('run','-d','--name',backend,*common,BACKEND_IMAGE,env=env)
    for attempt in range(60):
        result=subprocess.run(['docker','exec',backend,'curl','--silent','--fail','http://localhost:8080/actuator/health'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        if result.returncode==0: break
        time.sleep(1)
    else: raise RuntimeError('El backend de prueba no inició')
    front=PREFIX+'-frontend';names.append(front)
    docker('run','-d','--name',front,'--network',PREFIX,'-p','127.0.0.1::3000','-e',f'JANUS_BACKEND_URL=http://{backend}:8080',FRONTEND_IMAGE)
    port=docker('port',front,'3000/tcp').rsplit(':',1)[1]
    origin='http://127.0.0.1:'+port
    jar=http.cookiejar.CookieJar();client=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
    def request(path,body=None,bearer=None,csrf=None):
        headers={}
        if bearer: headers['Authorization']='Bearer '+bearer
        if csrf: headers['X-CSRF-TOKEN']=csrf
        if body is not None: headers['Content-Type']='application/json'
        req=urllib.request.Request(origin+path,data=None if body is None else json.dumps(body).encode(),headers=headers)
        try:
            with client.open(req,timeout=20) as response: return response.status,response.read(),response.headers
        except urllib.error.HTTPError as response: return response.code,response.read(),response.headers
    for attempt in range(40):
        try:
            status,_,_=request('/acceso')
            if status==200: break
        except (urllib.error.URLError, OSError, __import__("http.client").client.RemoteDisconnected): pass
        time.sleep(.5)
    else: raise RuntimeError('El frontend de prueba no inició')
    if not SOLO_ROLES:
        status,raw,_=request('/api/v1/auth/csrf');assert status==200
        csrf=json.loads(raw)['token']
        status,raw,headers=request('/api/v1/auth/login',{'correo':'recorrido@pruebas.invalid','contrasena':admin_password},csrf=csrf)
        assert status==200, 'Login por proxy falló: '+str(status)
        access=json.loads(raw)['accessToken'];assert 'HttpOnly' in headers.get('Set-Cookie','')
        status,raw,_=request('/api/v1/auth/me',bearer=access);assert status==200
        status,raw,_=request('/api/v1/usuarios',bearer=access);assert status==200 and json.loads(raw)['total']==1
        status,raw,_=request('/api/v1/auth/csrf');assert status==200
        csrf=json.loads(raw)['token']
        status,raw,_=request('/api/v1/auth/refresh',{},csrf=csrf);assert status==200, 'Refresh falló: '+str(status)
        renewed=json.loads(raw)['accessToken']
        status,_,_=request('/api/v1/auth/logout',{});assert status==403
        status,_,_=request('/api/v1/auth/logout',{},csrf=csrf);assert status==204
        assert request('/api/v1/auth/me',bearer=access)[0]==401
        assert request('/api/v1/auth/me',bearer=renewed)[0]==401
        assert request('/api/v1/auth/refresh',{},csrf=csrf)[0]==401
        print('Proxy Node de producción: login, consulta, renovación, CSRF, logout y rechazo de JWT anteriores correctos')
        sql="""INSERT INTO usuarios(id,nombres,apellidos,correo,estado) VALUES('00000000-0000-0000-0000-000000000002','Segunda','Persona','segunda@pruebas.invalid','ACTIVO');
        INSERT INTO credenciales SELECT '00000000-0000-0000-0000-000000000002',hash_contrasena,actualizado_en FROM credenciales LIMIT 1;
        INSERT INTO usuarios_roles VALUES('00000000-0000-0000-0000-000000000002','ADMINISTRADOR');"""
        docker('exec','-e','MYSQL_PWD',mysql,'mysql','-uprueba','prueba','-e',sql,env=dict(env,MYSQL_PWD=password))
    browser=subprocess.run([shutil.which('node'),str(ROOT/'frontend-janussync/scripts'/('verificar-gestion-roles-navegador.mjs' if SOLO_GESTION else 'verificar-roles-navegador.mjs' if SOLO_ROLES else 'verificar-jwt-navegador.mjs'))],env=dict(os.environ,TEST_JANUS_ORIGIN=origin,TEST_JANUS_PASSWORD=admin_password),timeout=150)
    assert browser.returncode==0, 'Falló el recorrido en Chrome'

finally:
    for name in reversed(names): subprocess.run(['docker','rm','-f',name],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    if network_created: subprocess.run(['docker','network','rm',PREFIX],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    shutil.rmtree(keys)
