#!/bin/sh
# Acción explícita de preparación. Nunca se invoca desde el arranque.
set -eu
[ "$#" -eq 1 ] || { echo "Uso: sh scripts/generar-claves-locales.sh /directorio/nuevo/fuera-del-repositorio" >&2; exit 1; }
task_dir="$1"
case "$task_dir" in /*) ;; *) echo "Usa una ruta absoluta." >&2; exit 1;; esac
task_repo="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
python3 - "$task_dir" "$task_repo" <<'CHECK'
import pathlib,sys
p=pathlib.Path(sys.argv[1]).resolve()
r=pathlib.Path(sys.argv[2]).resolve()
if p==r or r in p.parents:
    sys.exit("Las claves deben quedar fuera del repositorio.")
CHECK
umask 077
# Fallar si existe: no reemplazar claves ni cambiar permisos de directorios previos.
mkdir "$task_dir"
chmod 700 "$task_dir"
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "$task_dir/jwt-private.pem" 2>/dev/null
openssl pkey -in "$task_dir/jwt-private.pem" -pubout -out "$task_dir/jwt-public.pem" 2>/dev/null
chmod 600 "$task_dir/jwt-private.pem" "$task_dir/jwt-public.pem"
echo "Claves creadas. Configura JANUS_KEYS_DIR y el UID/GID de lectura en .env."
