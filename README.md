# Ferretech API (backend)

API REST de la tienda Ferretech, hecha con **Spring Boot 4 (Java 21)**, PostgreSQL y autenticación con JWT. Se despliega con Docker en una instancia **AWS EC2**. El frontend (React) está en su propio repositorio, `ferretech-frontend`.

Reemplaza al `localStorage` que usaba el frontend: ahora productos, usuarios y pedidos viven en PostgreSQL, y las reglas de negocio (stock, permisos, contraseñas) las aplica el servidor.

## Tecnologías

| Herramienta | Uso |
| --- | --- |
| Spring Boot 4.0.9 / Java 21 | API REST (`spring-boot-starter-webmvc`) |
| Spring Data JPA + PostgreSQL 17 | Persistencia |
| Spring Security + OAuth2 Resource Server | Roles y verificación de JWT (HS256) |
| BCrypt | Cifrado de contraseñas |
| Bean Validation | Validación de los datos de entrada |
| Docker Compose | Orquesta backend, base de datos y Caddy |
| Caddy 2 | Entrada pública: sirve el frontend, reenvía `/api` y entrega HTTPS automático si hay dominio |
| JUnit 5, Mockito, MockMvc y H2 | Pruebas (no necesitan Docker ni PostgreSQL) |

## Arquitectura

Todo corre en la misma EC2, en tres contenedores:

```text
Navegador ──80/443──> Caddy ──┬── /api/*  ──> backend (Spring Boot :8080) ──> db (PostgreSQL)
                              └── el resto ─> archivos del frontend (carpeta dist/)
```

- **Un solo origen:** el navegador carga la página y llama a `/api` en el mismo dominio, por lo que no hay problemas de CORS ni de contenido mixto.
- **Solo Caddy es público.** El backend publica su puerto 8080 únicamente en `127.0.0.1` (sirve para probar con `curl` o con `npm run dev`), y PostgreSQL no se publica.
- Los archivos del frontend los sube el repo `ferretech-frontend` con su script `desplegar` a la carpeta `dist/` de este proyecto.

## Estructura

```text
pom.xml · mvnw · mvnw.cmd       Maven y Maven Wrapper (no hace falta instalar Maven)
Dockerfile                      Compila y empaqueta la API (multi-stage, usuario sin privilegios)
compose.yaml                    Servicios: db, backend y caddy
Caddyfile                       Entrada pública: frontend + proxy de /api
.env.example                    Variables de entorno (copiar como .env)
dist/                           Frontend compilado (lo sube ferretech-frontend; no se versiona)
src/main/java/cl/ferretech/api/
├── FerretechApplication.java
├── config/       Propiedades, seguridad (reglas de acceso y CORS), JWT y datos iniciales
├── domain/       Entidades: Usuario, Producto, Pedido, PedidoItem, CodigoRecuperacion
├── repository/   Repositorios Spring Data
├── dto/          Objetos de entrada y salida de la API (con sus validaciones)
├── service/      Reglas de negocio: AuthService, ProductoService, PedidoService, JwtService
├── web/          Controladores REST
└── error/        ApiException y manejo global de errores
src/test/                       Pruebas unitarias y de integración
```

## API

Todas las rutas empiezan con `/api`. Los errores siempre salen como `{"mensaje": "..."}` con el código HTTP que corresponde.

| Método y ruta | Acceso | Descripción |
| --- | --- | --- |
| `GET /estado` | Público | Comprueba que la API está viva |
| `POST /auth/registro` | Público | Crea una cuenta de cliente. `409` si el usuario ya existe |
| `POST /auth/login` | Público | Ingreso de clientes. Devuelve `{ token, usuario, rol }`. `401` si es incorrecto |
| `POST /auth/admin/login` | Público | Ingreso de administradores (una cuenta de cliente no puede entrar aquí, ni al revés) |
| `POST /auth/recuperar` | Público | Genera un código de 6 dígitos para un cliente |
| `POST /auth/restablecer` | Público | Cambia la contraseña con el código |
| `GET /productos` y `GET /productos/{id}` | Público | Catálogo |
| `POST /productos` | **ADMIN** | Crea un producto (`201`) |
| `PUT /productos/{id}` | **ADMIN** | Actualiza solo los campos enviados |
| `DELETE /productos/{id}` | **ADMIN** | Da de baja el producto (`204`) |
| `POST /pedidos` | Con sesión | Registra la compra y descuenta el stock (`201`) |

La sesión se envía como `Authorization: Bearer <token>`. Sin token o con uno inválido o vencido la API responde `401`; con un token de cliente en una ruta de administrador, `403`.

```bash
# estado
curl http://localhost:8080/api/estado

# ingresar y guardar el token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"usuario":"Mauricio","contrasena":"1234"}' | sed -E 's/.*"token":"([^"]+)".*/\1/')

# comprar 2 unidades del producto 1
curl -X POST http://localhost:8080/api/pedidos \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"items":[{"productoId":1,"cantidad":2}]}'
```

### Reglas de negocio

- **Stock atómico y todo o nada:** `POST /pedidos` descuenta cada producto con una sola sentencia SQL (`... where stock >= cantidad`), así dos compras simultáneas nunca dejan el stock negativo. Si un producto no alcanza (`409`), la transacción se deshace completa y no se descuenta nada.
- **Historial estable:** cada línea del pedido guarda el nombre y el precio del producto al momento de la compra.
- **Usuario del pedido:** sale del JWT, no del cuerpo de la petición.
- **Contraseñas:** se guardan con BCrypt, nunca en texto plano.
- **Recuperación de contraseña:** el código tiene 6 dígitos, vale 10 minutos, sirve una sola vez y se invalida tras 5 intentos fallidos. Solo las cuentas de clientes pueden usarla; la de administrador no. Con `APP_RECUPERACION_MOSTRAR_CODIGO=true` (por defecto) el código viaja en la respuesta, simulando el correo.
- **Datos iniciales:** si la base está vacía, al iniciar se crean el cliente `Mauricio`, el administrador `Admin` y los 3 productos de la tienda.

## Variables de entorno

Se definen en el archivo `.env` (copiar desde `.env.example`; **no se sube a Git**). `compose.yaml` se niega a arrancar si faltan las obligatorias.

| Variable | Obligatoria | Descripción |
| --- | --- | --- |
| `POSTGRES_PASSWORD` | Sí | Contraseña de la base de datos |
| `APP_JWT_SECRET` | Sí | Secreto para firmar los JWT, **mínimo 32 caracteres**. Generar uno con `openssl rand -base64 48` |
| `POSTGRES_DB`, `POSTGRES_USER` | No | Nombre y usuario de la base (por defecto `ferretech`) |
| `SITE_ADDRESS` | No | `:80` (por defecto) = HTTP por IP. Un dominio (ej. `tienda.ejemplo.cl`) activa HTTPS automático |
| `BACKEND_PORT` | No | Puerto local del backend en `127.0.0.1` (por defecto `8080`) |
| `HTTP_PORT` | No | Puerto HTTP de Caddy (por defecto `80`). Solo conviene cambiarlo en tu PC si el 80 está ocupado |
| `APP_CORS_ORIGINS` | No | Orígenes permitidos para llamadas desde otro dominio (por defecto `*`; solo importa en desarrollo) |
| `APP_SEED_PASSWORD_CLIENTE`, `APP_SEED_PASSWORD_ADMIN` | No | Contraseñas de las cuentas iniciales (por defecto `1234`). **Cámbialas** si la EC2 queda abierta a Internet |
| `APP_RECUPERACION_MOSTRAR_CODIGO` | No | `true` muestra el código de recuperación en la respuesta |

## Ejecutar en tu PC

Requisitos: Docker Desktop.

```bash
cp .env.example .env        # en PowerShell: Copy-Item .env.example .env
# editar .env: cambiar POSTGRES_PASSWORD y APP_JWT_SECRET
docker compose up --build -d
docker compose ps
curl http://localhost:8080/api/estado
```

Con eso la API queda en `http://localhost:8080/api`, que es lo que espera `npm run dev` del frontend. Para ver los registros: `docker compose logs --tail=100 backend`. Para detener todo: `docker compose down` (agrega `-v` para borrar también la base de datos).

## Pruebas

Requisito: **Java 21**. No hace falta Maven, Docker ni PostgreSQL: las pruebas usan una base H2 en memoria (configurada en `src/test/resources/application.properties`).

```bash
./mvnw test           # Windows (PowerShell): .\mvnw.cmd test
```

Hay 79 casos en 8 archivos:

| Archivo | Casos | Tipo | Qué verifica |
| --- | --- | --- | --- |
| `AuthServiceTest` | 20 | Unitaria (Mockito) | Registro, ingreso separado por rol, código de recuperación (generación, vigencia, intentos, expiración) y cambio de contraseña |
| `ProductoServiceTest` | 9 | Unitaria (Mockito) | Alta con valores por defecto, actualización parcial, baja y errores 404 |
| `PedidoServiceTest` | 6 | Unitaria (Mockito) | Descuento de stock, total, suma de líneas repetidas, falta de stock y producto inexistente |
| `JwtServiceTest` | 3 | Unitaria | Usuario y rol dentro del token, token vencido y token firmado con otra clave |
| `AuthApiTest` | 16 | Integración (API completa) | Registro, ingreso por rol y flujo completo de recuperación, incluida la invalidación tras 5 intentos |
| `ProductoApiTest` | 13 | Integración | Lectura pública, permisos (401, 403), alta, edición y baja como administrador, y validaciones |
| `PedidoApiTest` | 9 | Integración | Exige sesión, descuenta stock, total, falta de stock, validaciones y **atomicidad** (un fallo revierte los descuentos anteriores) |
| `EstadoApiTest` | 3 | Integración | Estado, rutas no públicas y CORS |

Las pruebas de integración levantan toda la aplicación, ingresan por la API para obtener un JWT real y prueban los roles con Spring Security activo. No son transaccionales a propósito (para poder verificar que la transacción del pedido se revierte); cada una crea sus propios datos y los limpia.

## Despliegue en AWS EC2

Es el mismo procedimiento de la guía del curso (EC2 con Docker), con tres diferencias: se levantan tres contenedores, el frontend se sirve desde la misma instancia y la entrada pública es el puerto 80 (y el 443 si usas dominio) en vez del 8080.

### 1. Crear la instancia

1. **EC2 → Launch instance** con **Amazon Linux 2023** (x86_64) y al menos **4 GiB de RAM** (por ejemplo `t3.medium`): Maven compila la API dentro de la instancia. Revisa su precio.
2. Grupo de seguridad, reglas de entrada:

   | Tipo | Puerto | Origen |
   | --- | --- | --- |
   | SSH | TCP 22 | **My IP** |
   | HTTP | TCP 80 | `0.0.0.0/0` (o las IP que deban ver la tienda) |
   | HTTPS | TCP 443 | `0.0.0.0/0` (solo si usarás dominio) |

   **No abras el 8080 ni el 5432**: el backend y la base de datos no deben ser accesibles desde Internet.
3. Anota la **IPv4 pública**. Cambia si detienes e inicias la instancia; una Elastic IP la mantiene fija (tiene costo por hora).

### 2. Instalar Docker en la EC2

```powershell
ssh -i "C:\ruta\mi-clave.pem" ec2-user@IP_PUBLICA
```

```bash
sudo dnf update -y
sudo dnf install -y docker git
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user
```

Cierra la sesión con `exit` y vuelve a conectarte para activar el grupo `docker`. Luego instala el plugin Compose y Buildx exactamente como indican las secciones 3 y 4 de la guía del curso:

```bash
mkdir -p ~/.docker/cli-plugins
curl -fSL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-$(uname -m)" -o ~/.docker/cli-plugins/docker-compose
chmod +x ~/.docker/cli-plugins/docker-compose

case "$(uname -m)" in
  x86_64) buildx_arch=amd64 ;;
  aarch64) buildx_arch=arm64 ;;
  *) echo "Arquitectura no compatible: $(uname -m)"; exit 1 ;;
esac
curl -fL "https://github.com/docker/buildx/releases/download/v0.38.2/buildx-v0.38.2.linux-${buildx_arch}" -o /tmp/docker-buildx
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo install -m 755 /tmp/docker-buildx /usr/local/lib/docker/cli-plugins/docker-buildx

docker --version && docker compose version && docker buildx version
```

### 3. Desplegar el backend

```bash
cd ~
git clone URL_DE_TU_REPOSITORIO ferretech-backend
cd ferretech-backend
cp .env.example .env
nano .env        # cambiar POSTGRES_PASSWORD, APP_JWT_SECRET y las contraseñas iniciales
mkdir -p dist    # carpeta del frontend; créala TÚ antes de levantar Docker (ver nota)
docker compose config
docker compose up --build -d
docker compose ps
curl -i http://localhost:8080/api/estado
```

La primera compilación puede tardar varios minutos. Si algo falla: `docker compose logs --tail=100 backend`.

> **Importante:** crea `dist/` con `mkdir -p dist` **antes** de `docker compose up`. Si no existe, Docker la crea con el usuario `root` y luego el script del frontend no podrá copiar archivos ahí (`Permission denied`). Si ya te pasó: `sudo chown -R ec2-user:ec2-user ~/ferretech-backend/dist`.

> El script de despliegue del frontend asume que la carpeta se llama `ferretech-backend`. Si usas otro nombre, pásalo con `-Carpeta`.

### 4. Subir el frontend

Desde tu PC, en el repositorio `ferretech-frontend`:

```powershell
.\scripts\desplegar.ps1 -Clave C:\ruta\mi-clave.pem -Ip IP_PUBLICA
```

Abre `http://IP_PUBLICA`: debe verse la tienda con el catálogo. Cada vez que cambies el frontend repite este paso (no hace falta reiniciar contenedores).

### 5. HTTPS con dominio (opcional, recomendado si hay inicio de sesión)

1. Consigue un dominio o subdominio y apúntalo (registro A) a la IP de la EC2. Conviene una Elastic IP para que no cambie.
2. Abre el puerto 443 en el grupo de seguridad.
3. En `.env` pon `SITE_ADDRESS=tienda.ejemplo.cl` y ejecuta `docker compose up -d`.

Caddy obtiene y renueva solo el certificado de Let's Encrypt. No hay que cambiar nada en el frontend, porque llama a `/api` en el mismo dominio.

### Actualizar y detener

```bash
git pull
docker compose up --build -d     # actualizar el backend
docker compose down              # detener (los datos se conservan en el volumen pgdata)
```

Detén o termina la instancia desde AWS al acabar para evitar cargos (también los de almacenamiento e IP pública).

## Problemas frecuentes

- **`docker compose` dice que falta `POSTGRES_PASSWORD` o `APP_JWT_SECRET`:** no existe el archivo `.env` o le falta esa variable.
- **El backend se reinicia en bucle con "APP_JWT_SECRET debe tener al menos 32 caracteres":** el secreto es demasiado corto.
- **La página no carga en `http://IP_PUBLICA`:** revisa la regla de entrada TCP 80, `docker compose ps` y que hayas subido el frontend (la carpeta `dist/` debe tener archivos). `curl http://IP_PUBLICA/api/estado` comprueba solo la API.
- **El frontend carga pero no aparecen productos:** `docker compose logs --tail=100 backend`. Si el backend no arrancó o no logra conectarse a la base, el motivo aparece allí.
- **`Permission denied` al subir el frontend con `scp`:** `dist/` quedó con dueño `root`. Corrige con `sudo chown -R ec2-user:ec2-user ~/ferretech-backend/dist`.
- **Al recargar `/carrito` aparece "Not Found":** no ocurre con esta configuración (Caddy devuelve `index.html` para las rutas del frontend). Si pasa, el `Caddyfile` fue modificado.
- **`permission denied` al usar Docker en la EC2:** sal de SSH y vuelve a entrar después de `usermod`.
- **Compilación interrumpida en la EC2:** falta memoria (`free -h`); usa una instancia de al menos 4 GiB.
- **Las pruebas no arrancan:** necesitas Java 21 (`java -version`).

## Limitaciones conocidas

Es un proyecto de curso; antes de usarlo en producción real conviene resolver:

- **Cuentas iniciales con contraseña conocida** (`1234` por defecto): cámbialas con las variables `APP_SEED_PASSWORD_*` antes de abrir la EC2 a Internet.
- **Código de recuperación en la respuesta** (simula el correo): para producción hay que enviarlo por correo (por ejemplo con Amazon SES) y poner `APP_RECUPERACION_MOSTRAR_CODIGO=false`.
- **Esquema de la base con `ddl-auto=update`:** práctico para el curso, pero en producción corresponde usar migraciones (Flyway o Liquibase).
- **Sin límite de intentos de ingreso:** el login no bloquea ante intentos repetidos (la recuperación de contraseña sí limita los suyos).
- **Sin respaldos automáticos:** los datos viven en un volumen de Docker dentro de la EC2.
- **Pago simulado:** `POST /pedidos` registra el pedido y descuenta stock, pero no hay pasarela de pago ni consulta de historial.
