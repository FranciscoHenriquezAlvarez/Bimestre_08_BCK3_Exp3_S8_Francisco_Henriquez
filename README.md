# Semana 3

## 1. Objetivo del proyecto

Proyecto individual de Semana 3 de Desarrollo Backend III. Moderniza procesos batch legacy del Banco XYZ mediante Spring Batch y continúa el trabajo de las semanas anteriores. Procesa transacciones diarias, intereses mensuales y movimientos anuales, incorporando escalamiento multi-thread y tolerancia a fallos.

## 2. Tecnologías utilizadas

- Java 17
- Spring Boot
- Spring Batch
- Spring JDBC y `JdbcTemplate`
- PostgreSQL
- Maven
- Docker Compose
- SLF4J

## 3. Arquitectura de la solución

```text
CSV -> ItemReader -> ItemProcessor -> ItemWriter -> PostgreSQL
```

Cada `Job` organiza uno o más `Step`. Los `ItemReader` leen los CSV, los `ItemProcessor` validan y transforman los registros, y los `ItemWriter` persisten los datos mediante JDBC. El `JobRepository` usa PostgreSQL y registra Jobs, Steps y ejecuciones en las tablas `BATCH_*`.

## 4. Estructura del código

```text
proyecto/
├── .mvn/wrapper/
├── output/
│   └── estados_cuenta_anuales.csv
├── src/main/
│   ├── java/cl/duoc/bancoxyz/
│   │   ├── config/
│   │   ├── exception/
│   │   ├── model/
│   │   ├── processor/
│   │   └── BankBatchApplication.java
│   └── resources/
│       ├── input/
│       │   ├── transacciones.csv
│       │   ├── intereses.csv
│       │   └── cuentas_anuales.csv
│       ├── application.properties
│       └── schema.sql
├── .dockerignore
├── Dockerfile
├── docker-compose.yml
├── mvnw
├── mvnw.cmd
└── pom.xml
```

`config` contiene los Jobs, Steps, políticas y listeners; `processor` concentra las validaciones y transformaciones; `model` representa los registros CSV; y `resources` contiene configuración, esquema y datos de entrada.

## 5. Procesos Batch implementados

### dailyTransactionsJob

Lee `input/transacciones.csv` mediante `dailyTransactionsStep`. Valida campos, fecha, monto, tipo y duplicados; persiste las transacciones válidas en `transacciones_procesadas` y registra el resumen en `resumen_transacciones_diarias`.

### monthlyInterestJob

Lee `input/intereses.csv` mediante `monthlyInterestStep`. Valida cuentas, saldo, edad y tipo, calcula el interés de ahorro o préstamo y persiste el resultado en `intereses_mensuales`.

### annualStatementsJob

Lee `input/cuentas_anuales.csv` mediante `annualMovementsStep`, valida los movimientos y los persiste en `movimientos_anuales`. Luego `annualStatementReportStep` genera `output/estados_cuenta_anuales.csv`.

## 6. Procesamiento paralelo y escalamiento

El proyecto usa `ThreadPoolTaskExecutor` para ejecutar chunks en paralelo con hilos `Batch-Thread-`. La configuración utilizada en las ejecuciones principales es:

```properties
bank.batch.chunk-size=5
bank.batch.core-pool-size=3
bank.batch.max-pool-size=3
bank.batch.queue-capacity=25
```

| Configuración | Chunk | Threads | Duración |
| ------------- | ----: | ------: | -------: |
| A             |     2 |       2 |   169 ms |
| B             |     5 |       3 |   185 ms |
| C             |     5 |       4 |   165 ms |

Las tres configuraciones mantuvieron el mismo resultado funcional: 10 registros leídos, 7 escritos, 3 omitidos y estado `COMPLETED`.

La configuración C fue la **mejor configuración observada para el conjunto de datos de prueba**.

## 7. Manejo de errores y tolerancia a fallos

`BankDataSkipPolicy` omite registros con `InvalidDataException` o `FlatFileParseException`, y `BatchSkipListener` registra esos skips. Durante las ejecuciones finales hubo skips reales por registros inválidos.

La `RetryPolicy` reintenta únicamente `TransientDataAccessException`, con límite de 3 intentos. `FixedBackOffPolicy` espera 100 ms entre reintentos.

No se produjo un error transitorio real de PostgreSQL durante la ejecución final, por lo que la política quedó configurada pero no fue activada.

## 8. Requisitos de ejecución

- Java 17
- Docker Desktop con integración habilitada para WSL
- Docker Compose
- WSL Ubuntu

## 9. Instrucciones para ejecutar el proyecto

Todas las instrucciones siguientes se ejecutan desde una terminal Ubuntu WSL.

desde:

```bash
cd "/home/francisco/proyecto/BACKEND III/SEMANA 3/Exp1_S3_Francisco_Henriquez/proyecto"
```

### 9.1 Levantar PostgreSQL

Para comenzar desde una base de datos limpia:

```bash
docker compose down -v --remove-orphans
docker compose up -d postgres
docker compose ps
```

Comprobar que PostgreSQL esté disponible:

```bash
docker compose exec -T postgres pg_isready -U banco_user -d banco_xyz
```

El resultado debe indicar:

```text
accepting connections
```

### 9.2 Compilar el proyecto

```bash
chmod +x mvnw
./mvnw clean package
```

El resultado esperado es:

```text
BUILD SUCCESS
```

El JAR generado es:

```text
target/banco-xyz-batch-1.0.0.jar
```

### 9.3 Crear carpeta para evidencias reproducidas

Para no modificar las evidencias originales:

```bash
mkdir -p ../evidencias/reproduccion
```

### 9.4 Ejecutar dailyTransactionsJob

```bash
java -jar target/banco-xyz-batch-1.0.0.jar \
  --spring.batch.job.name=dailyTransactionsJob \
  ejecucion=301,java.lang.Long,true \
  2>&1 | tee ../evidencias/reproduccion/01_transacciones.txt
```

Resultado esperado:

```text
Read = 10
Write = 7
Skip = 3
Estado = COMPLETED
```

### 9.5 Ejecutar monthlyInterestJob

```bash
java -jar target/banco-xyz-batch-1.0.0.jar \
  --spring.batch.job.name=monthlyInterestJob \
  ejecucion=302,java.lang.Long,true \
  2>&1 | tee ../evidencias/reproduccion/02_intereses.txt
```

Resultado esperado:

```text
Read = 8
Write = 6
Skip = 2
Estado = COMPLETED
```

### 9.6 Ejecutar annualStatementsJob

```bash
java -jar target/banco-xyz-batch-1.0.0.jar \
  --spring.batch.job.name=annualStatementsJob \
  ejecucion=303,java.lang.Long,true \
  2>&1 | tee ../evidencias/reproduccion/03_estados_anuales.txt
```

Resultado esperado de `annualMovementsStep`:

```text
Read = 9
Write = 8
Skip = 1
Estado = COMPLETED
```

También deben finalizar:

```text
annualStatementReportStep = COMPLETED
annualStatementsJob = COMPLETED
```

## 10. Resultados de ejecución

| Job / Step                  | Read | Write | Skip | Estado      |
| --------------------------- | ---: | ----: | ---: | ----------- |
| `dailyTransactionsJob`      |   10 |     7 |    3 | `COMPLETED` |
| `monthlyInterestJob`        |    8 |     6 |    2 | `COMPLETED` |
| `annualMovementsStep`       |    9 |     8 |    1 | `COMPLETED` |
| `annualStatementReportStep` |    0 |     0 |    0 | `COMPLETED` |

`annualStatementReportStep` es un Step basado en Tasklet que genera el informe anual; por eso sus contadores no representan procesamiento por chunks.

## 11. Resultados en base de datos

Después de ejecutar los tres Jobs sobre una base limpia:

```text
transacciones_procesadas = 7
intereses_mensuales = 6
movimientos_anuales = 8
```

PostgreSQL también almacena los metadatos de Spring Batch en las tablas `BATCH_*`.

## 12. Archivo generado

`annualStatementsJob` genera:

```text
output/estados_cuenta_anuales.csv
```

## 13. Evidencias

Las evidencias originales del proyecto son:

```text
evidencias/01_transacciones.txt
evidencias/02_intereses.txt
evidencias/03_estados_anuales.txt
evidencias/04_reejecucion_transacciones.txt
evidencias/escala_A.txt
evidencias/escala_B.txt
evidencias/escala_C.txt
```

Las nuevas ejecuciones realizadas siguiendo este README se almacenan en:

```text
evidencias/reproduccion/
```

# 14. Reproducción de las 9 figuras del informe

Las siguientes instrucciones permiten reproducir las evidencias utilizadas en:

```text
PBY2203_Exp1_S3_Francisco_Henriquez.docx
```

Para las Figuras 1 a 6 se utiliza la ejecución normal realizada anteriormente.

## 14.1 Figura 1 - dailyTransactionsJob

Limpiar la terminal:

```bash
clear
```

Mostrar las líneas relevantes:

```bash
grep -E "TaskExecutor configurado|Registro omitido|Finaliza Step dailyTransactionsStep|RESUMEN dailyTransactionsJob" \
  ../evidencias/reproduccion/01_transacciones.txt
```

La salida debe permitir comprobar:

```text
dailyTransactionsJob
dailyTransactionsStep
leidos=10
escritos=7
omitidos=3
estado=COMPLETED
```

## 14.2 Figura 2 - monthlyInterestJob

Limpiar:

```bash
clear
```

Mostrar las líneas relevantes:

```bash
grep -E "TaskExecutor configurado|Registro omitido|Finaliza Step monthlyInterestStep|RESUMEN monthlyInterestJob" \
  ../evidencias/reproduccion/02_intereses.txt
```

La salida debe permitir comprobar:

```text
monthlyInterestJob
monthlyInterestStep
leidos=8
escritos=6
omitidos=2
estado=COMPLETED
```

## 14.3 Figura 3 - annualStatementsJob

Limpiar:

```bash
clear
```

Ejecutar:

```bash
grep -E "TaskExecutor configurado|Registro omitido|Finaliza Step annualMovementsStep|annualStatementReportStep|RESUMEN annualStatementsJob" \
  ../evidencias/reproduccion/03_estados_anuales.txt
```

La salida debe permitir comprobar:

```text
annualMovementsStep
leidos=9
escritos=8
omitidos=1
COMPLETED

annualStatementReportStep
COMPLETED

annualStatementsJob
COMPLETED
```

## 14.4 Figura 4 - Resultados en PostgreSQL

Después de ejecutar los tres Jobs, limpiar la terminal:

```bash
clear
```

Ejecutar:

```bash
docker compose exec -T postgres psql \
  -U banco_user \
  -d banco_xyz \
  -At \
  -c "
SELECT 'transacciones_procesadas: ' || COUNT(*) || ' filas'
FROM transacciones_procesadas;

SELECT 'intereses_mensuales: ' || COUNT(*) || ' filas'
FROM intereses_mensuales;

SELECT 'movimientos_anuales: ' || COUNT(*) || ' filas'
FROM movimientos_anuales;
"
```

El resultado esperado es:

```text
transacciones_procesadas: 7 filas
intereses_mensuales: 6 filas
movimientos_anuales: 8 filas
```

## 14.5 Figura 5 - Archivo anual generado

Comprobar que el archivo existe:

```bash
ls -l output/estados_cuenta_anuales.csv
```

Limpiar:

```bash
clear
```

Mostrar su contenido:

```bash
cat output/estados_cuenta_anuales.csv
```

El encabezado debe ser:

```text
cuenta_id,total_movimientos,total_depositos,total_cargos,saldo_neto
```

En la ejecución validada se obtuvo:

```text
101,2,1000.00,-500.00,500.00
102,1,1500.00,0,1500.00
103,1,2000.00,0,2000.00
104,1,0,-100.00,-100.00
105,1,2500.00,0,2500.00
106,1,3000.00,0,3000.00
108,1,2000.00,0,2000.00
```

## 14.6 Figura 6 - Procesamiento paralelo

La evidencia se obtiene de los logs de los tres Jobs.

Limpiar:

```bash
clear
```

Ejecutar:

```bash
{
  grep -m1 "TaskExecutor configurado" \
    ../evidencias/reproduccion/01_transacciones.txt

  grep "Batch-Thread-1" \
    ../evidencias/reproduccion/01_transacciones.txt \
    ../evidencias/reproduccion/02_intereses.txt \
    ../evidencias/reproduccion/03_estados_anuales.txt \
    | head -n 1

  grep "Batch-Thread-2" \
    ../evidencias/reproduccion/01_transacciones.txt \
    ../evidencias/reproduccion/02_intereses.txt \
    ../evidencias/reproduccion/03_estados_anuales.txt \
    | head -n 1

  grep "Batch-Thread-3" \
    ../evidencias/reproduccion/01_transacciones.txt \
    ../evidencias/reproduccion/02_intereses.txt \
    ../evidencias/reproduccion/03_estados_anuales.txt \
    | head -n 1
}
```

La salida debe permitir observar:

```text
TaskExecutor configurado
Batch-Thread-1
Batch-Thread-2
Batch-Thread-3
```

La asignación exacta de los registros a cada hilo puede variar entre ejecuciones.

Tomar la captura de pantalla.

## 14.7 Figura 7 - Comparación de escalamiento

La comparación utiliza `dailyTransactionsJob`.

Cada configuración debe ejecutarse desde una base de datos limpia y utilizando el mismo archivo de entrada.

Los valores de tiempo pueden ser diferentes a los registrados originalmente.

Lo que debe mantenerse en las tres pruebas es:

```text
Read = 10
Write = 7
Skip = 3
Estado = COMPLETED
```

### Configuración A

Reiniciar PostgreSQL:

```bash
docker compose down -v --remove-orphans
docker compose up -d postgres
```

Esperar a PostgreSQL:

```bash
until docker compose exec -T postgres pg_isready -U banco_user -d banco_xyz
do
  sleep 1
done
```

Ejecutar:

```bash
java -jar target/banco-xyz-batch-1.0.0.jar \
  --spring.batch.job.name=dailyTransactionsJob \
  --bank.batch.chunk-size=2 \
  --bank.batch.core-pool-size=2 \
  --bank.batch.max-pool-size=2 \
  --bank.batch.queue-capacity=25 \
  ejecucion=101,java.lang.Long,true \
  2>&1 | tee ../evidencias/reproduccion/escala_A.txt
```

### Configuración B

Reiniciar PostgreSQL:

```bash
docker compose down -v --remove-orphans
docker compose up -d postgres
```

Esperar:

```bash
until docker compose exec -T postgres pg_isready -U banco_user -d banco_xyz
do
  sleep 1
done
```

Ejecutar:

```bash
java -jar target/banco-xyz-batch-1.0.0.jar \
  --spring.batch.job.name=dailyTransactionsJob \
  --bank.batch.chunk-size=5 \
  --bank.batch.core-pool-size=3 \
  --bank.batch.max-pool-size=3 \
  --bank.batch.queue-capacity=25 \
  ejecucion=102,java.lang.Long,true \
  2>&1 | tee ../evidencias/reproduccion/escala_B.txt
```

### Configuración C

Reiniciar PostgreSQL:

```bash
docker compose down -v --remove-orphans
docker compose up -d postgres
```

Esperar:

```bash
until docker compose exec -T postgres pg_isready -U banco_user -d banco_xyz
do
  sleep 1
done
```

Ejecutar:

```bash
java -jar target/banco-xyz-batch-1.0.0.jar \
  --spring.batch.job.name=dailyTransactionsJob \
  --bank.batch.chunk-size=5 \
  --bank.batch.core-pool-size=4 \
  --bank.batch.max-pool-size=4 \
  --bank.batch.queue-capacity=25 \
  ejecucion=103,java.lang.Long,true \
  2>&1 | tee ../evidencias/reproduccion/escala_C.txt
```

### Mostrar las tres pruebas juntas

Limpiar:

```bash
clear
```

Ejecutar:

```bash
echo "CONFIGURACION A"
echo "chunk=2 | core=2 | max=2 | cola=25"
grep "Finaliza Step dailyTransactionsStep" \
  ../evidencias/reproduccion/escala_A.txt

echo
echo "CONFIGURACION B"
echo "chunk=5 | core=3 | max=3 | cola=25"
grep "Finaliza Step dailyTransactionsStep" \
  ../evidencias/reproduccion/escala_B.txt

echo
echo "CONFIGURACION C"
echo "chunk=5 | core=4 | max=4 | cola=25"
grep "Finaliza Step dailyTransactionsStep" \
  ../evidencias/reproduccion/escala_C.txt
```

La salida será equivalente a:

```text
CONFIGURACION A
chunk=2 | core=2 | max=2 | cola=25
Finaliza Step dailyTransactionsStep - duracionMs=XXX, leidos=10, escritos=7, omitidos=3, estado=COMPLETED

CONFIGURACION B
chunk=5 | core=3 | max=3 | cola=25
Finaliza Step dailyTransactionsStep - duracionMs=XXX, leidos=10, escritos=7, omitidos=3, estado=COMPLETED

CONFIGURACION C
chunk=5 | core=4 | max=4 | cola=25
Finaliza Step dailyTransactionsStep - duracionMs=XXX, leidos=10, escritos=7, omitidos=3, estado=COMPLETED
```

`XXX` corresponde al tiempo realmente obtenido en cada nueva ejecución.

## 14.8 Figura 8 - Nueva ejecución de dailyTransactionsJob

Esta prueba ejecuta nuevamente `dailyTransactionsJob` utilizando un parámetro identificador diferente. Corresponde a una nueva ejecución completa y no a una reanudación desde una fila específica del CSV.

Ejecutar:

```bash
java -jar target/banco-xyz-batch-1.0.0.jar \
  --spring.batch.job.name=dailyTransactionsJob \
  ejecucion=305,java.lang.Long,true \
  2>&1 | tee ../evidencias/reproduccion/04_reejecucion_transacciones.txt
```

El resultado esperado es:

```text
Read = 10
Write = 7
Skip = 3
Estado = COMPLETED
```

Para mostrar únicamente las líneas relevantes de la ejecución:

```bash
grep -E "Finaliza Step dailyTransactionsStep|RESUMEN dailyTransactionsJob|ejecucion.*305|status.*COMPLETED" \
  ../evidencias/reproduccion/04_reejecucion_transacciones.txt
```

La salida debe permitir comprobar que `dailyTransactionsJob` fue ejecutado nuevamente con el parámetro `ejecucion=305` y finalizó con estado `COMPLETED`.

## 14.9 Figura 9 - Verificación de reejecución y ausencia de duplicados

Después de la nueva ejecución, se verifica el registro de las ejecuciones de `dailyTransactionsJob` almacenadas por Spring Batch, el contenido final de la tabla `transacciones_procesadas` y la ausencia de registros duplicados.

Limpiar la terminal:

```bash
clear
```

Ejecutar:

```bash
docker compose exec -T postgres psql \
  -U banco_user \
  -d banco_xyz \
  -c "
SELECT
    ji.JOB_INSTANCE_ID,
    ji.JOB_NAME,
    je.JOB_EXECUTION_ID,
    je.STATUS,
    jep.PARAMETER_NAME,
    jep.PARAMETER_VALUE,
    jep.IDENTIFYING
FROM BATCH_JOB_INSTANCE ji
JOIN BATCH_JOB_EXECUTION je
    ON ji.JOB_INSTANCE_ID = je.JOB_INSTANCE_ID
LEFT JOIN BATCH_JOB_EXECUTION_PARAMS jep
    ON je.JOB_EXECUTION_ID = jep.JOB_EXECUTION_ID
WHERE ji.JOB_NAME = 'dailyTransactionsJob'
ORDER BY je.JOB_EXECUTION_ID;
"

docker compose exec -T postgres psql \
  -U banco_user \
  -d banco_xyz \
  -c "SELECT * FROM transacciones_procesadas ORDER BY id;"

docker compose exec -T postgres psql \
  -U banco_user \
  -d banco_xyz \
  -c "
SELECT id, COUNT(*) AS cantidad
FROM transacciones_procesadas
GROUP BY id
HAVING COUNT(*) > 1;
"
```

La salida debe permitir comprobar:

```text
dailyTransactionsJob - ejecucion 103 - COMPLETED
dailyTransactionsJob - ejecucion 305 - COMPLETED

transacciones_procesadas = 7 registros

Registros duplicados = 0
```

Las ejecuciones 103, 304 y 305 quedan registradas por Spring Batch con estado `COMPLETED`. Después de la reejecución, la tabla `transacciones_procesadas` mantiene 7 registros y la consulta de duplicados retorna `0 rows`, verificando que no se generaron registros duplicados.

## 15. Resumen de las evidencias reproducibles

| Figura   | Evidencia                                            |
| -------- | ---------------------------------------------------- |
| Figura 1 | Ejecución de `dailyTransactionsJob`                  |
| Figura 2 | Ejecución de `monthlyInterestJob`                    |
| Figura 3 | Ejecución de `annualStatementsJob`                   |
| Figura 4 | Resultados persistidos en PostgreSQL                 |
| Figura 5 | Contenido de `estados_cuenta_anuales.csv`            |
| Figura 6 | Procesamiento paralelo mediante `Batch-Thread-*`     |
| Figura 7 | Comparación A, B y C de escalamiento                 |
| Figura 8 | Nueva ejecución de `dailyTransactionsJob`            |
| Figura 9 | Verificación de reejecución y ausencia de duplicados |

## 16. Consideración sobre los tiempos

Los tiempos originales observados fueron:

```text
A = 169 ms
B = 185 ms
C = 165 ms
```

Estos valores corresponden a una ejecución concreta y pueden variar al repetir la prueba.

En cada nueva ejecución se deben conservar los resultados funcionales:

```text
Read = 10
Write = 7
Skip = 3
Estado = COMPLETED
```

## 17. Ejecución completa mediante Docker Compose

Como alternativa a la ejecución de la aplicación mediante `java -jar`, el proyecto puede ejecutarse completamente mediante Docker Compose.

Esta modalidad utiliza dos componentes:

```text
Docker Compose
├── postgres
│   └── PostgreSQL 16
│
└── app
    └── Spring Boot + Spring Batch + Java 17
```

El servicio `postgres` mantiene la base de datos y el servicio `app` contiene la aplicación Spring Batch.

Esta alternativa facilita el despliegue del proyecto porque el equipo donde se ejecute solamente necesita Docker y Docker Compose. Java 17 y la compilación Maven son administrados dentro de la imagen Docker de la aplicación.

### 17.1 Construir la imagen de la aplicación

Desde la carpeta del proyecto:

```bash
cd "/home/francisco/proyecto/BACKEND III/SEMANA 3/Exp1_S3_Francisco_Henriquez/proyecto"
```

Construir la imagen:

```bash
docker compose build
```

Durante este proceso Docker utiliza el `Dockerfile` para:

```text
1. Utilizar Java 17 JDK para compilar.
2. Ejecutar Maven Wrapper.
3. Generar banco-xyz-batch-1.0.0.jar.
4. Crear una imagen final utilizando Java 17 JRE.
```

El resultado esperado debe finalizar indicando que la imagen de la aplicación fue construida correctamente.

Por ejemplo:

```text
Image proyecto-app Built
```

### 17.2 Levantar PostgreSQL

Ejecutar:

```bash
docker compose up -d postgres
```

Comprobar el estado:

```bash
docker compose ps
```

El contenedor debe aparecer con estado:

```text
healthy
```

Por ejemplo:

```text
banco-xyz-postgres   postgres:16   Up (...) (healthy)
```

El servicio PostgreSQL utiliza internamente:

```text
postgres:5432
```

El puerto publicado hacia WSL es:

```text
localhost:5433
```

Cuando la aplicación también se ejecuta dentro de Docker, Docker Compose utiliza el nombre del servicio `postgres` para establecer la comunicación entre los contenedores.

La conexión utilizada por el contenedor `app` es:

```text
jdbc:postgresql://postgres:5432/banco_xyz
```

### 17.3 Ejecutar dailyTransactionsJob

Ejecutar:

```bash
docker compose run --rm app \
  --spring.batch.job.name=dailyTransactionsJob \
  ejecucion=401,java.lang.Long,true
```

El contenedor espera automáticamente hasta que PostgreSQL se encuentre disponible.

El resultado funcional esperado es:

```text
Read = 10
Write = 7
Skip = 3
Estado = COMPLETED
```

En los logs debe observarse una salida equivalente a:

```text
Finaliza Step dailyTransactionsStep - leidos=10, escritos=7, omitidos=3, estado=COMPLETED
```

y posteriormente:

```text
dailyTransactionsJob ... status: [COMPLETED]
```

Cuando el Job ya indique `COMPLETED`, presionar:

```text
Ctrl+C
```

para cerrar el contenedor temporal de la aplicación.

La opción:

```text
--rm
```

hace que el contenedor temporal sea eliminado al finalizar.

PostgreSQL permanece ejecutándose.

### 17.4 Ejecutar monthlyInterestJob

Ejecutar:

```bash
docker compose run --rm app \
  --spring.batch.job.name=monthlyInterestJob \
  ejecucion=402,java.lang.Long,true
```

El resultado funcional esperado es:

```text
Read = 8
Write = 6
Skip = 2
Estado = COMPLETED
```

En los logs debe observarse una salida equivalente a:

```text
Finaliza Step monthlyInterestStep - leidos=8, escritos=6, omitidos=2, estado=COMPLETED
```

y posteriormente:

```text
monthlyInterestJob ... status: [COMPLETED]
```

Cuando el Job indique `COMPLETED`, presionar:

```text
Ctrl+C
```

para cerrar el contenedor temporal.

### 17.5 Ejecutar annualStatementsJob

Ejecutar:

```bash
docker compose run --rm app \
  --spring.batch.job.name=annualStatementsJob \
  ejecucion=403,java.lang.Long,true
```

El resultado funcional esperado de `annualMovementsStep` es:

```text
Read = 9
Write = 8
Skip = 1
Estado = COMPLETED
```

También deben finalizar correctamente:

```text
annualStatementReportStep = COMPLETED
annualStatementsJob = COMPLETED
```

En los logs debe observarse una salida equivalente a:

```text
Finaliza Step annualMovementsStep - leidos=9, escritos=8, omitidos=1, estado=COMPLETED
```

y:

```text
annualStatementsJob ... status: [COMPLETED]
```

Cuando el Job indique `COMPLETED`, presionar:

```text
Ctrl+C
```

para cerrar el contenedor temporal.

### 17.6 Verificar los resultados en PostgreSQL

Después de ejecutar los tres Jobs, consultar la base de datos:

```bash
docker compose exec -T postgres psql \
  -U banco_user \
  -d banco_xyz \
  -At \
  -c "
SELECT 'transacciones_procesadas: ' || COUNT(*) || ' filas'
FROM transacciones_procesadas;

SELECT 'intereses_mensuales: ' || COUNT(*) || ' filas'
FROM intereses_mensuales;

SELECT 'movimientos_anuales: ' || COUNT(*) || ' filas'
FROM movimientos_anuales;
"
```

El resultado esperado es:

```text
transacciones_procesadas: 7 filas
intereses_mensuales: 6 filas
movimientos_anuales: 8 filas
```

### 17.7 Verificar el archivo anual

El servicio `app` utiliza un volumen para compartir la carpeta:

```text
./output
```

con:

```text
/app/output
```

dentro del contenedor.

Por esta razón, el archivo generado por `annualStatementsJob` permanece disponible en el proyecto después de finalizar el contenedor.

Comprobar:

```bash
ls -l output/estados_cuenta_anuales.csv
```

Mostrar su contenido:

```bash
cat output/estados_cuenta_anuales.csv
```

El archivo debe comenzar con:

```text
cuenta_id,total_movimientos,total_depositos,total_cargos,saldo_neto
```

### 17.8 Detener PostgreSQL

Después de finalizar las pruebas, detener los servicios:

```bash
docker compose down
```

Este comando detiene y elimina los contenedores, pero mantiene el volumen de PostgreSQL.

Si se desea eliminar también la base de datos y comenzar nuevamente desde cero:

```bash
docker compose down -v --remove-orphans
```

El parámetro:

```text
-v
```

elimina el volumen de PostgreSQL y, por lo tanto, todos los datos almacenados.

### 17.9 Resumen de ejecución mediante Docker Compose

La ejecución completa mediante Docker Compose queda formada por:

```text
docker compose build
        ↓
construye la aplicación con Java 17
        ↓
docker compose up -d postgres
        ↓
PostgreSQL queda healthy
        ↓
docker compose run --rm app dailyTransactionsJob
        ↓
10 / 7 / 3 / COMPLETED
        ↓
docker compose run --rm app monthlyInterestJob
        ↓
8 / 6 / 2 / COMPLETED
        ↓
docker compose run --rm app annualStatementsJob
        ↓
9 / 8 / 1 / COMPLETED
        ↓
PostgreSQL
7 / 6 / 8
        ↓
output/estados_cuenta_anuales.csv
```

Esta modalidad no reemplaza las instrucciones de ejecución y reproducción de evidencias indicadas anteriormente. Corresponde a una alternativa adicional para facilitar la construcción y ejecución completa de la solución mediante contenedores Docker.

## 18. SEMANA 4 - BACKEND FOR FRONTEND (BFF)

### Objetivo y estrategia

El patron Backend for Frontend adapta la informacion y las operaciones al tipo de cliente que las consume. Para este proyecto se seleccionaron endpoints separados por canal dentro de la misma aplicacion Spring Boot: Web, Mobile y ATM. Esta estrategia entrega contratos especificos, reutiliza PostgreSQL y la logica existente, y evita introducir microservicios o componentes adicionales fuera del alcance academico.

La capa BFF consulta solamente datos persistidos por los Jobs previos. No lee los CSV ni modifica la arquitectura Spring Batch.

### Estructura BFF

```text
cl.duoc.bancoxyz.bff/
├── BffBankingService.java
├── web/
│   ├── WebBffController.java
│   └── dto/
├── mobile/
│   ├── MobileBffController.java
│   └── dto/
└── atm/
    ├── AtmBffController.java
    └── dto/
```

### Canales y endpoints

| Canal  | Endpoint                                                 | Datos entregados                                                  |
| ------ | -------------------------------------------------------- | ----------------------------------------------------------------- |
| Web    | `GET /api/bff/web/accounts/{accountId}`                  | Cuenta, nombre, tipo, saldo inicial, tasa, interes y saldo final. |
| Web    | `GET /api/bff/web/accounts/{accountId}/annual-statement` | Movimientos, depositos, cargos y saldo neto anual.                |
| Mobile | `GET /api/bff/mobile/accounts/{accountId}`               | Cuenta, nombre, saldo y tipo.                                     |
| Mobile | `GET /api/bff/mobile/accounts/{accountId}/transactions`  | Ultimos cinco movimientos por fecha descendente.                  |
| ATM    | `GET /api/bff/atm/accounts/{accountId}/balance`          | Cuenta y saldo.                                                   |
| ATM    | `POST /api/bff/atm/accounts/{accountId}/withdraw`        | Retiro y saldo actualizado.                                       |

Web entrega el contrato mas completo; Mobile elimina tasa, saldo inicial e interes; ATM entrega solamente los datos necesarios para consultar y retirar efectivo.

### Seguridad por canal

Cada ruta BFF requiere el header `X-Channel-Token`. Los tokens se configuran mediante variables de entorno o `application.properties`:

| Canal  | Variable           | Valor local por defecto |
| ------ | ------------------ | ----------------------- |
| Web    | `BFF_WEB_TOKEN`    | `web-token-xyz`         |
| Mobile | `BFF_MOBILE_TOKEN` | `mobile-token-xyz`      |
| ATM    | `BFF_ATM_TOKEN`    | `atm-token-xyz`         |

Un token ausente, o de un canal distinto, responde `401 Unauthorized`. Una cuenta inexistente responde `404`; los retiros con monto no valido o saldo insuficiente responden `400`.

## Reproducción de evidencias por consola

Los siguientes comandos corresponden a las ejecuciones registradas en `evidencias/01_bff_web.txt` a `evidencias/06_bff_verificacion_final.txt`. Utilizan los datos ya persistidos por los Jobs Batch; no reinicializan la base de datos ni vuelven a ejecutar los Jobs.

### Preparación del entorno

```bash
cd "/home/francisco/proyecto/BACKEND III/SEMANA 4/Exp1_S4_Francisco_Henriquez/proyecto"
docker compose ps
```

La evidencia registró PostgreSQL healthy en el puerto `5433`. Si la API BFF no está disponible, iniciarla sin lanzar los Jobs Batch:

```bash
java -jar target/banco-xyz-batch-1.0.0.jar --spring.batch.job.enabled=false
```

### BFF Web

```bash
curl -i -H 'X-Channel-Token: web-token-xyz' \
  http://127.0.0.1:8080/api/bff/web/accounts/101

curl -i -H 'X-Channel-Token: web-token-xyz' \
  http://127.0.0.1:8080/api/bff/web/accounts/101/annual-statement
```

Las respuestas registradas fueron `HTTP 200`. El detalle Web de la cuenta 101 incluyó `initialBalance` 8000.00, `interestRate` 0.0200, `calculatedInterest` 160.00 y `finalBalance` 8160.00. El estado anual registró 14 movimientos, depósitos por 21000.00, cargos por -1300.00 y saldo neto de 19700.00.

### BFF Mobile

```bash
curl -i -H 'X-Channel-Token: mobile-token-xyz' \
  http://127.0.0.1:8080/api/bff/mobile/accounts/101

curl -i -H 'X-Channel-Token: mobile-token-xyz' \
  http://127.0.0.1:8080/api/bff/mobile/accounts/101/transactions
```

Ambas respuestas registraron `HTTP 200`. El contrato Mobile de la cuenta 101 incluyó solamente cuenta, nombre, saldo y tipo. La consulta de movimientos devolvió cinco registros, ordenados desde `2024-12-22` hasta `2024-07-09`.

### BFF ATM y persistencia

Antes de retirar, consultar las cuentas reales con saldo suficiente:

```bash
docker compose exec -T postgres psql -U banco_user -d banco_xyz -c "
SELECT cuenta_id, nombre, tipo, saldo_inicial, tasa, saldo_final
FROM intereses_mensuales
WHERE saldo_final >= 10000
ORDER BY saldo_final DESC
LIMIT 10;
"
```

La evidencia seleccionó la cuenta 117, cuyo saldo era 12240.00. La siguiente secuencia registró el saldo antes y después, el detalle Web y la persistencia del retiro:

```bash
curl -i -H 'X-Channel-Token: atm-token-xyz' \
  http://127.0.0.1:8080/api/bff/atm/accounts/117/balance

curl -i -H 'X-Channel-Token: web-token-xyz' \
  http://127.0.0.1:8080/api/bff/web/accounts/117

curl -i -X POST -H 'X-Channel-Token: atm-token-xyz' \
  -H 'Content-Type: application/json' -d '{"amount":10000}' \
  http://127.0.0.1:8080/api/bff/atm/accounts/117/withdraw

curl -i -H 'X-Channel-Token: atm-token-xyz' \
  http://127.0.0.1:8080/api/bff/atm/accounts/117/balance

curl -i -H 'X-Channel-Token: web-token-xyz' \
  http://127.0.0.1:8080/api/bff/web/accounts/117

docker compose exec -T postgres psql -U banco_user -d banco_xyz -c "
SELECT cuenta_id, nombre, tipo, saldo_inicial, tasa, saldo_final
FROM intereses_mensuales
WHERE cuenta_id = 117;
"
```

Todas las consultas y el retiro registraron `HTTP 200`. El retiro de 10000.00 dejó saldo 2240.00, tanto en ATM como en PostgreSQL. El detalle Web posterior mantuvo `calculatedInterest` en 240.00, calculado desde saldo inicial 12000.00 y tasa 0.0200, mientras `finalBalance` disminuyó a 2240.00.

### Seguridad y validaciones ATM

```bash
curl -i http://127.0.0.1:8080/api/bff/web/accounts/101

curl -i -H 'X-Channel-Token: web-token-xyz' \
  http://127.0.0.1:8080/api/bff/mobile/accounts/101

curl -i -H 'X-Channel-Token: mobile-token-xyz' \
  http://127.0.0.1:8080/api/bff/mobile/accounts/101

curl -i -H 'X-Channel-Token: web-token-xyz' \
  http://127.0.0.1:8080/api/bff/atm/accounts/117/balance

curl -i -X POST -H 'X-Channel-Token: atm-token-xyz' \
  -H 'Content-Type: application/json' -d '{"amount":5000}' \
  http://127.0.0.1:8080/api/bff/atm/accounts/117/withdraw

curl -i -X POST -H 'X-Channel-Token: atm-token-xyz' \
  -H 'Content-Type: application/json' -d '{"amount":-10000}' \
  http://127.0.0.1:8080/api/bff/atm/accounts/117/withdraw

curl -i -X POST -H 'X-Channel-Token: atm-token-xyz' \
  -H 'Content-Type: application/json' -d '{"amount":10000}' \
  http://127.0.0.1:8080/api/bff/atm/accounts/117/withdraw

curl -i -H 'X-Channel-Token: web-token-xyz' \
  http://127.0.0.1:8080/api/bff/web/accounts/999
```

Las evidencias registraron `401` sin token y con token de canal cruzado; `200` con el token Mobile correcto; `400` para monto 5000, monto negativo y saldo insuficiente; y `404` para la cuenta 999.

### Verificación final

```bash
docker compose ps

docker compose exec -T postgres psql -U banco_user -d banco_xyz -c "
SELECT 'transacciones_procesadas' AS tabla, COUNT(*) AS registros
FROM transacciones_procesadas
UNION ALL
SELECT 'intereses_mensuales', COUNT(*)
FROM intereses_mensuales
UNION ALL
SELECT 'movimientos_anuales', COUNT(*)
FROM movimientos_anuales;
"

find "../evidencias" -maxdepth 1 -type f -printf "%f\n" | sort
find "../evidencias/semana 03" -maxdepth 1 -type f -printf "%f\n" | sort
```

La verificación registrada confirmó PostgreSQL healthy, 387 transacciones procesadas, 50 intereses mensuales y 289 movimientos anuales. Los seis archivos de evidencia de Semana 04 están directamente en `evidencias/`; los archivos históricos de `evidencias/semana 03` se conservan separados.

# Semana 5 - Spring Security y HTTPS

## Objetivo y arquitectura

La aplicacion conserva una sola instancia Spring Boot, una base PostgreSQL y los BFF existentes. La seguridad de Semana 4 basada en `X-Channel-Token` fue reemplazada por Spring Security, HTTP Basic y roles por canal. No se modificaron controladores, DTO, servicio bancario ni procesos Spring Batch.

```text
Cliente HTTPS -> Spring Boot :8443 -> Spring Security -> BFF -> PostgreSQL
                                      ROLE_WEB    -> /api/bff/web/**
                                      ROLE_MOBILE -> /api/bff/mobile/**
                                      ROLE_ATM    -> /api/bff/atm/**
```

Se usa `BCryptPasswordEncoder` para construir usuarios en memoria, `SessionCreationPolicy.STATELESS` para no mantener sesiones y HTTP Basic como mecanismo de autenticacion. Las contrasenas se obtienen desde `application.properties` y se pueden sobrescribir mediante `WEB_PASSWORD`, `MOBILE_PASSWORD` y `ATM_PASSWORD`.

| Usuario      | ROLE          | Web | Mobile | ATM |
| ------------ | ------------- | --- | ------ | --- |
| `webuser`    | `ROLE_WEB`    | SI  | NO     | NO  |
| `mobileuser` | `ROLE_MOBILE` | NO  | SI     | NO  |
| `atmuser`    | `ROLE_ATM`    | NO  | NO     | SI  |

Un HTTP 401 significa que faltan credenciales o son invalidas. Un HTTP 403 significa que el usuario ya fue autenticado, pero su rol no puede utilizar el canal solicitado.

## HTTPS local

Toda la aplicacion escucha solamente en `https://localhost:8443`. El certificado de laboratorio es autofirmado, usa un keystore PKCS12, alias `bff-local`, CN `localhost` y SAN para `localhost` e `127.0.0.1`. No se usa certificado de cliente, JWT ni OAuth2.

### Preparar PostgreSQL desde cero

Desde la ruta real del proyecto:

```bash
cd "/home/francisco/proyecto/BACKEND III/SEMANA 5/Exp1_S5_Francisco_Henriquez/proyecto"

docker compose down -v --remove-orphans
docker compose up -d postgres

until docker compose exec -T postgres pg_isready -U banco_user -d banco_xyz
do
  sleep 1
done

docker compose ps
```

Este flujo elimina el volumen local de PostgreSQL, inicia una base limpia y espera hasta que PostgreSQL acepte conexiones. Permite reproducir el saldo inicial utilizado para la prueba ATM. `docker compose down -v --remove-orphans` elimina los datos locales almacenados en el volumen.

```bash
chmod +x mvnw
chmod +x scripts/generar-certificado.sh
./scripts/generar-certificado.sh
keytool -list -v -keystore src/main/resources/keystore.p12 -storetype PKCS12 -storepass changeit
./mvnw clean package
```

El script crea `src/main/resources/keystore.p12`, que se encuentra excluido del control de versiones. Para un certificado autofirmado local, `curl` usa `-k` exclusivamente en laboratorio.

### Preparar datos para los BFF

Si PostgreSQL se inicio desde cero, ejecutar los tres Jobs Batch antes de probar los BFF Web, Mobile y ATM.

```bash
java -jar target/banco-xyz-batch-1.0.0.jar \
  --spring.main.web-application-type=none \
  --spring.batch.job.name=dailyTransactionsJob \
  ejecucion=501,java.lang.Long,true

java -jar target/banco-xyz-batch-1.0.0.jar \
  --spring.main.web-application-type=none \
  --spring.batch.job.name=monthlyInterestJob \
  ejecucion=502,java.lang.Long,true

java -jar target/banco-xyz-batch-1.0.0.jar \
  --spring.main.web-application-type=none \
  --spring.batch.job.name=annualStatementsJob \
  ejecucion=503,java.lang.Long,true
```

Los resultados esperados finales son `transacciones_procesadas = 387`, `intereses_mensuales = 50` y `movimientos_anuales = 289`.

```bash
docker compose exec -T postgres psql \
  -U banco_user \
  -d banco_xyz \
  -c "
SELECT 'transacciones_procesadas' AS tabla, COUNT(*) AS registros
FROM transacciones_procesadas
UNION ALL
SELECT 'intereses_mensuales', COUNT(*)
FROM intereses_mensuales
UNION ALL
SELECT 'movimientos_anuales', COUNT(*)
FROM movimientos_anuales;
"

docker compose exec -T postgres psql \
  -U banco_user \
  -d banco_xyz \
  -c "
SELECT cuenta_id, saldo_final
FROM intereses_mensuales
WHERE cuenta_id = 102;
"
```

El resultado esperado para la cuenta 102 es `102 | 10200.00`; este saldo inicial permite reproducir el retiro documentado: `10200.00 - 10000.00 = 200.00`.

### Levantar API HTTPS

```bash
java -jar target/banco-xyz-batch-1.0.0.jar \
  --spring.batch.job.enabled=false
```

## Pruebas verificadas

Web autorizado, sin credenciales y con password incorrecta:

```bash
curl -k -sS -i -u webuser:web123 https://localhost:8443/api/bff/web/accounts/101
curl -k -sS -i https://localhost:8443/api/bff/web/accounts/101
curl -k -sS -i -u webuser:incorrecta https://localhost:8443/api/bff/web/accounts/101
```

Mobile autorizado, movimientos y rol Web rechazado:

```bash
curl -k -sS -i -u mobileuser:mobile123 https://localhost:8443/api/bff/mobile/accounts/101
curl -k -sS -i -u mobileuser:mobile123 https://localhost:8443/api/bff/mobile/accounts/101/transactions
curl -k -sS -i -u webuser:web123 https://localhost:8443/api/bff/mobile/accounts/101
```

ATM y retiro HTTPS sobre la cuenta verificada 102:

```bash
curl -k -sS -i -u atmuser:atm123 https://localhost:8443/api/bff/atm/accounts/102/balance
curl -k -sS -i -u webuser:web123 https://localhost:8443/api/bff/atm/accounts/102/balance
curl -k -sS -i -u mobileuser:mobile123 https://localhost:8443/api/bff/atm/accounts/102/balance
curl -k -sS -i -u atmuser:atm123 -X POST -H 'Content-Type: application/json' -d '{"amount":10000}' https://localhost:8443/api/bff/atm/accounts/102/withdraw
```

El POST ATM queda excluido de CSRF solamente bajo `/api/bff/**`, porque es una API REST stateless autenticada mediante HTTP Basic. La prueba real registro el saldo de la cuenta 102 desde 10200.00 hasta 200.00.

En Postman, para este certificado local, usar temporalmente `Settings -> General -> SSL certificate verification -> OFF`; en cada solicitud seleccionar `Authorization -> Basic Auth` y utilizar una de las credenciales de la tabla. No se debe enviar `X-Channel-Token`.

## Evidencias de Semana 5

| Evidencia                                 | Contenido                                                                                              |
| ----------------------------------------- | ------------------------------------------------------------------------------------------------------ |
| `evidencias/01_compilacion_https.txt`     | Compilacion, PostgreSQL y puerto HTTPS                                                                 |
| `evidencias/02_certificado_https.txt`     | Certificado PKCS12 y SAN                                                                               |
| `evidencias/03_seguridad_web.txt`         | Respuestas Web 200 y 401                                                                               |
| `evidencias/04_seguridad_mobile.txt`      | Respuestas Mobile 200 y 403                                                                            |
| `evidencias/05_seguridad_atm.txt`         | Respuestas ATM 200 y 403                                                                               |
| `evidencias/06_autorizacion_roles.txt`    | Matriz real de roles                                                                                   |
| `evidencias/07_atm_retiro_https.txt`      | Retiro HTTPS y persistencia PostgreSQL                                                                 |
| `evidencias/08_verificacion_final.txt`    | Estado final, puerto y conteos PostgreSQL                                                              |
| `evidencias/09_pruebas_automatizadas.txt` | Suite automatizada: pruebas unitarias, Spring Security/BFF e integracion Batch con PostgreSQL temporal |

### Pruebas automatizadas

El proyecto incorpora pruebas unitarias Batch, integración Spring Security y BFF, e integraciones Batch de regresión con PostgreSQL temporal. La suite protege resultados Batch y permisos por canal ya validados.

La ejecucion validada finalizo con 51 pruebas ejecutadas, 0 fallos, 0 errores y 0 pruebas omitidas.

```bash
cd "/home/francisco/proyecto/BACKEND III/SEMANA 5/Exp1_S5_Francisco_Henriquez/proyecto"
./mvnw test
./mvnw clean test
```

Las integraciones Batch usan Testcontainers y requieren Docker disponible para levantar PostgreSQL temporal.

# Semana 6 - Microservicios y seguridad con Spring Cloud

## Evolución arquitectónica

Semana 5 concentraba los controladores Web, Mobile y ATM y `BffBankingService` en una sola aplicación. Para esta entrega el proyecto se reorganizó en módulos Spring Boot independientes dentro de `./proyecto`:

```text
config-server:8888 -> configuración centralizada (config-repo)
eureka-server:8761 -> descubrimiento
core-banking-service:8081 -> PostgreSQL, consultas, movimientos y retiro
bff-web-service:8082 -> DTO Web, ROLE_WEB, JWT y Circuit Breaker
bff-mobile-service:8083 -> DTO Mobile, ROLE_MOBILE, JWT y Circuit Breaker
bff-atm-service:8084 -> saldo/retiro, ROLE_ATM, JWT y Circuit Breaker
```

El Core conserva el modelo, el esquema, los archivos de entrada y la configuración Batch de las semanas anteriores. Los BFF ya no acceden a PostgreSQL: resuelven `core-banking-service` mediante Eureka. Mobile conserva la consulta SQL `LIMIT 5` desde el Core; ATM valida monto múltiplo de 10.000, saldo disponible y persiste el nuevo saldo con una transacción.

## Seguridad

Cada BFF expone `POST /auth/login` con usuarios `webuser/web123`, `mobileuser/mobile123` y `atmuser/atm123`. Las contraseñas se codifican con BCrypt, las sesiones son STATELESS y los JWT llevan el rol del usuario. Los endpoints de cada canal requieren respectivamente `ROLE_WEB`, `ROLE_MOBILE` y `ROLE_ATM`; el filtro devuelve 401 para token ausente/inválido y 403 para rol incorrecto. HTTP Basic no se utiliza como mecanismo final.

Config Server usa `./config-repo`; todos los módulos tienen respaldo local de desarrollo y consumen `spring.config.import` cuando Config Server está disponible. Cada llamada BFF -> Core tiene un Circuit Breaker Resilience4j y fallback HTTP 503 controlado.

## Reproducción completa de evidencias — Semana 06

Este procedimiento corresponde a la Opción A y fue utilizado para generar las evidencias entregadas.

Se ejecuta en una sola consola WSL y mantiene los servicios en segundo plano mediante `nohup`.

### 1. Definir las rutas de trabajo

```bash
ROOT="/home/francisco/proyecto/BACKEND III/SEMANA 6/Exp3_S6_Grupo11"
PROY="$ROOT/proyecto"
EVID="$ROOT/evidencias/semana 06"

mkdir -p "$EVID"

cd "$PROY"

echo "PROYECTO:"
pwd

echo
echo "EVIDENCIAS:"
echo "$EVID"
```

Resultado esperado:

```text
PROYECTO:
/home/francisco/proyecto/BACKEND III/SEMANA 6/Exp3_S6_Grupo11/proyecto

EVIDENCIAS:
/home/francisco/proyecto/BACKEND III/SEMANA 6/Exp3_S6_Grupo11/evidencias/semana 06
```

```bash
ls -ld "$EVID"
```

### 2. Detener instancias anteriores

```bash
pkill -f 'config-server/target/config-server-1.0.0.jar' 2>/dev/null || true
pkill -f 'eureka-server/target/eureka-server-1.0.0.jar' 2>/dev/null || true
pkill -f 'core-banking-service/target/core-banking-service-1.0.0.jar' 2>/dev/null || true
pkill -f 'bff-web-service/target/bff-web-service-1.0.0.jar' 2>/dev/null || true
pkill -f 'bff-mobile-service/target/bff-mobile-service-1.0.0.jar' 2>/dev/null || true
pkill -f 'bff-atm-service/target/bff-atm-service-1.0.0.jar' 2>/dev/null || true

sleep 3

ss -ltnp | grep -E ':(8081|8082|8083|8084|8761|8888)\b' || true
```

Para un inicio limpio no deberia aparecer ninguno de estos puertos.

### 3. Reiniciar PostgreSQL desde una base limpia

```bash
cd "$PROY"

docker compose down -v --remove-orphans
docker compose up -d postgres
docker ps

until docker exec banco-xyz-postgres \
    pg_isready -U banco_user -d banco_xyz >/dev/null 2>&1
do
    echo "Esperando PostgreSQL..."
    sleep 2
done

echo "PostgreSQL OK"
```

docker compose down -v --remove-orphans elimina el volumen PostgreSQL y los datos de ejecuciones anteriores. De esta forma las pruebas de Semana 6 comienzan desde una base limpia y reproducible.

El archivo `docker-compose.yml` también contiene los servicios necesarios para la ejecución completa mediante Docker. Sin embargo, `docker compose up -d postgres` utilizado en esta reproducción inicia únicamente PostgreSQL; los demás componentes se ejecutan mediante los comandos `java -jar` documentados en esta sección. La ejecución completa mediante Docker Compose se presenta posteriormente como una alternativa.

### 4. Compilar el proyecto y generar evidencia 18 - Build real

Este comando ejecuta `clean package` sobre el proyecto multimodulo, compila los seis componentes y genera los JAR que se utilizaran en los pasos siguientes. La misma ejecucion se almacena como evidencia `18_build.txt`.

```bash
cd "$PROY"

{
    echo "DIRECTORIO DE EJECUCION:"
    pwd
    echo
    echo "COMANDO:"
    echo "./mvnw -f pom.xml clean package"
    echo
    ./mvnw -f pom.xml clean package
} 2>&1 | tee "$EVID/18_build.txt"

grep -E "BUILD SUCCESS|BUILD FAILURE" "$EVID/18_build.txt"
tail -30 "$EVID/18_build.txt"
```

El resultado esperado es `BUILD SUCCESS`.

### 5. Verificar los seis JAR

```bash
ls -lh \
  config-server/target/config-server-1.0.0.jar \
  eureka-server/target/eureka-server-1.0.0.jar \
  core-banking-service/target/core-banking-service-1.0.0.jar \
  bff-web-service/target/bff-web-service-1.0.0.jar \
  bff-mobile-service/target/bff-mobile-service-1.0.0.jar \
  bff-atm-service/target/bff-atm-service-1.0.0.jar
```

Deben aparecer los seis artefactos.

### 6. Crear función de espera

```bash
wait_url() {
    URL="$1"
    NAME="$2"

    for i in {1..90}
    do
        if curl -fsS "$URL" >/dev/null 2>&1
        then
            echo "$NAME OK"
            return 0
        fi

        sleep 1
    done

    echo "ERROR: $NAME no inicio correctamente"
    return 1
}
```

### 7. Levantar Config Server

```bash
cd "$PROY"

nohup java -jar config-server/target/config-server-1.0.0.jar \
    > /tmp/s6_config.log 2>&1 &

echo $! > /tmp/s6_config.pid
wait_url "http://localhost:8888/actuator/health" "Config Server"
```

Resultado esperado: `Config Server OK`.

### 8. Levantar Eureka Server

```bash
nohup java -jar eureka-server/target/eureka-server-1.0.0.jar \
    > /tmp/s6_eureka.log 2>&1 &

echo $! > /tmp/s6_eureka.pid
wait_url "http://localhost:8761/actuator/health" "Eureka Server"
```

Resultado esperado: `Eureka Server OK`.

### 9. Levantar Core Banking

```bash
nohup java -jar core-banking-service/target/core-banking-service-1.0.0.jar \
    > /tmp/s6_core.log 2>&1 &

echo $! > /tmp/s6_core.pid
wait_url "http://localhost:8081/actuator/health" "Core Banking"
```

Resultado esperado: `Core Banking OK`.

### 10. Ejecutar nuevamente los Jobs Batch

```bash
cd "$PROY"

nohup java -jar core-banking-service/target/core-banking-service-1.0.0.jar \
  --server.port=0 \
  --eureka.client.enabled=false \
  --spring.batch.job.enabled=true \
  --spring.batch.job.name=dailyTransactionsJob \
  ejecucion=601,java.lang.Long,true \
  > /tmp/s6_batch_daily.log 2>&1 &

DAILY_PID=$!

echo "PID dailyTransactionsJob: $DAILY_PID"

for i in {1..60}
do
    if grep -qE "dailyTransactionsJob.*(estado=COMPLETED|status: \[COMPLETED\])" \
        /tmp/s6_batch_daily.log 2>/dev/null
    then
        echo "dailyTransactionsJob COMPLETED"
        break
    fi

    if grep -qE "dailyTransactionsJob.*(estado=FAILED|status: \[FAILED\])|APPLICATION FAILED TO START" \
        /tmp/s6_batch_daily.log 2>/dev/null
    then
        echo "ERROR: dailyTransactionsJob FAILED"
        tail -40 /tmp/s6_batch_daily.log
        break
    fi

    if [ "$i" -eq 60 ]
    then
        echo "ERROR: timeout esperando dailyTransactionsJob"
        tail -40 /tmp/s6_batch_daily.log
    fi

    sleep 2
done

grep -E "dailyTransactionsJob|COMPLETED|FAILED" /tmp/s6_batch_daily.log | tail -20

kill "$DAILY_PID" 2>/dev/null || true
wait "$DAILY_PID" 2>/dev/null || true

nohup java -jar core-banking-service/target/core-banking-service-1.0.0.jar \
  --server.port=0 \
  --eureka.client.enabled=false \
  --spring.batch.job.enabled=true \
  --spring.batch.job.name=monthlyInterestJob \
  ejecucion=602,java.lang.Long,true \
  > /tmp/s6_batch_monthly.log 2>&1 &

MONTHLY_PID=$!

echo "PID monthlyInterestJob: $MONTHLY_PID"

for i in {1..60}
do
    if grep -qE "monthlyInterestJob.*(estado=COMPLETED|status: \[COMPLETED\])" \
        /tmp/s6_batch_monthly.log 2>/dev/null
    then
        echo "monthlyInterestJob COMPLETED"
        break
    fi

    if grep -qE "monthlyInterestJob.*(estado=FAILED|status: \[FAILED\])|APPLICATION FAILED TO START" \
        /tmp/s6_batch_monthly.log 2>/dev/null
    then
        echo "ERROR: monthlyInterestJob FAILED"
        tail -40 /tmp/s6_batch_monthly.log
        break
    fi

    if [ "$i" -eq 60 ]
    then
        echo "ERROR: timeout esperando monthlyInterestJob"
        tail -40 /tmp/s6_batch_monthly.log
    fi

    sleep 2
done

grep -E "monthlyInterestJob|COMPLETED|FAILED" /tmp/s6_batch_monthly.log | tail -20

kill "$MONTHLY_PID" 2>/dev/null || true
wait "$MONTHLY_PID" 2>/dev/null || true

nohup java -jar core-banking-service/target/core-banking-service-1.0.0.jar \
  --server.port=0 \
  --eureka.client.enabled=false \
  --spring.batch.job.enabled=true \
  --spring.batch.job.name=annualStatementsJob \
  ejecucion=603,java.lang.Long,true \
  > /tmp/s6_batch_annual.log 2>&1 &

ANNUAL_PID=$!

echo "PID annualStatementsJob: $ANNUAL_PID"

for i in {1..60}
do
    if grep -qE "annualStatementsJob.*(estado=COMPLETED|status: \[COMPLETED\])" \
        /tmp/s6_batch_annual.log 2>/dev/null
    then
        echo "annualStatementsJob COMPLETED"
        break
    fi

    if grep -qE "annualStatementsJob.*(estado=FAILED|status: \[FAILED\])|APPLICATION FAILED TO START" \
        /tmp/s6_batch_annual.log 2>/dev/null
    then
        echo "ERROR: annualStatementsJob FAILED"
        tail -40 /tmp/s6_batch_annual.log
        break
    fi

    if [ "$i" -eq 60 ]
    then
        echo "ERROR: timeout esperando annualStatementsJob"
        tail -40 /tmp/s6_batch_annual.log
    fi

    sleep 2
done

grep -E "annualStatementsJob|COMPLETED|FAILED" /tmp/s6_batch_annual.log | tail -20

kill "$ANNUAL_PID" 2>/dev/null || true
wait "$ANNUAL_PID" 2>/dev/null || true
```

Cada Job Batch se ejecuta mediante una instancia temporal del `core-banking-service` con un puerto aleatorio. El procedimiento espera explicitamente que el log informe `COMPLETED` o `FAILED`, con un tiempo maximo de espera de 120 segundos. Una vez obtenido el resultado, la instancia temporal se detiene automaticamente.

Se utiliza `--server.port=0` para mantener el contexto web requerido por la configuracion de seguridad sin entrar en conflicto con el Core Banking principal que utiliza el puerto 8081. Adicionalmente, `--eureka.client.enabled=false` evita registrar estas instancias temporales en Eureka.

### 11. Comprobar datos generados por los Jobs Batch

```bash
docker exec -i banco-xyz-postgres \
  psql -U banco_user -d banco_xyz \
  -c "
SELECT 'transacciones_procesadas' AS tabla, COUNT(*) AS registros
FROM transacciones_procesadas
UNION ALL
SELECT 'intereses_mensuales', COUNT(*)
FROM intereses_mensuales
UNION ALL
SELECT 'movimientos_anuales', COUNT(*)
FROM movimientos_anuales;
"

docker exec -i banco-xyz-postgres \
  psql -U banco_user -d banco_xyz \
  -c "
SELECT cuenta_id,nombre,tipo,saldo_inicial,tasa,saldo_final
FROM intereses_mensuales
WHERE cuenta_id=104;
"

docker exec -i banco-xyz-postgres \
  psql -U banco_user -d banco_xyz \
  -c "
SELECT COUNT(*) AS movimientos_cuenta_104
FROM movimientos_anuales
WHERE cuenta_id=104;
"
```

Estas consultas son solamente `SELECT` y no modifican ningun dato.

### 12. Levantar BFF Web

```bash
cd "$PROY"

nohup java -jar bff-web-service/target/bff-web-service-1.0.0.jar \
    > /tmp/s6_web.log 2>&1 &

echo $! > /tmp/s6_web.pid
wait_url "http://localhost:8082/actuator/health" "BFF Web"
```

### 13. Levantar BFF Mobile

```bash
nohup java -jar bff-mobile-service/target/bff-mobile-service-1.0.0.jar \
    > /tmp/s6_mobile.log 2>&1 &

echo $! > /tmp/s6_mobile.pid
wait_url "http://localhost:8083/actuator/health" "BFF Mobile"
```

### 14. Levantar BFF ATM

```bash
nohup java -jar bff-atm-service/target/bff-atm-service-1.0.0.jar \
    > /tmp/s6_atm.log 2>&1 &

echo $! > /tmp/s6_atm.pid
wait_url "http://localhost:8084/actuator/health" "BFF ATM"
```

### 15. Esperar registro en Eureka, evidencias de infraestructura y JWT

```bash
for i in {1..60}; do
    APPS=$(
        curl -sS \
            -H "Accept: application/xml" \
            http://localhost:8761/eureka/apps
    )

    if echo "$APPS" | grep -q '<name>CORE-BANKING-SERVICE</name>' \
        && echo "$APPS" | grep -q '<name>BFF-WEB-SERVICE</name>' \
        && echo "$APPS" | grep -q '<name>BFF-MOBILE-SERVICE</name>' \
        && echo "$APPS" | grep -q '<name>BFF-ATM-SERVICE</name>'; then
        echo "Los cuatro servicios estan registrados en Eureka."
        break
    fi

    echo "Esperando registro completo en Eureka..."
    sleep 2
done

curl -sS \
    -H "Accept: application/xml" \
    http://localhost:8761/eureka/apps \
    | grep -E '<name>|<status>|<port'

{
    echo "Comando: curl http://localhost:8888/core-banking-service/default"
    echo
    curl -sS http://localhost:8888/core-banking-service/default
    echo
} > "$EVID/01_config_server.txt"

{
    echo "Comando: curl -i http://localhost:8081/api/config/origin"
    echo
    curl -sS -i http://localhost:8081/api/config/origin
    echo
} > "$EVID/01b_consumo_config_server.txt"

{
    echo "Comando: curl -i http://localhost:8761/actuator/health"
    echo
    curl -sS -i http://localhost:8761/actuator/health
    echo
} > "$EVID/02_eureka_server.txt"

curl -sS \
    -H "Accept: application/xml" \
    http://localhost:8761/eureka/apps > "$EVID/03_servicios_registrados.xml"

{
    echo "Comando: curl -H 'Accept: application/xml' http://localhost:8761/eureka/apps"
    echo
    cat "$EVID/03_servicios_registrados.xml"
} > "$EVID/03_servicios_registrados.txt"

curl -sS \
    -D /tmp/s6_atm_login_headers \
    -o /tmp/s6_atm_login_body \
    -X POST \
    -H "Content-Type: application/json" \
    -d '{"username":"atmuser","password":"atm123"}' \
    http://localhost:8084/auth/login

ATM_TOKEN=$(python3 -c 'import json; print(json.load(open("/tmp/s6_atm_login_body"))["accessToken"])')

WEB_TOKEN=$(
    curl -sS \
        -X POST \
        -H "Content-Type: application/json" \
        -d '{"username":"webuser","password":"web123"}' \
        http://localhost:8082/auth/login \
        | python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])'
)

MOBILE_TOKEN=$(
    curl -sS \
        -X POST \
        -H "Content-Type: application/json" \
        -d '{"username":"mobileuser","password":"mobile123"}' \
        http://localhost:8083/auth/login \
        | python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])'
)

echo "WEB: ${#WEB_TOKEN}"
echo "MOBILE: ${#MOBILE_TOKEN}"
echo "ATM: ${#ATM_TOKEN}"
```

### Evidencias funcionales, seguridad y ATM

```bash
{
    echo "Usuario: atmuser"
    echo "Endpoint: POST /auth/login"
    echo "HTTP: $(head -n 1 /tmp/s6_atm_login_headers | tr -d '\r' | awk '{print $2}')"
    echo "Comando: curl -X POST -H 'Content-Type: application/json' -d '{\"username\":\"atmuser\",\"password\":\"atm123\"}' http://localhost:8084/auth/login"
    echo
    echo "Body real (JWT oculto):"
    python3 -c 'import json; d=json.load(open("/tmp/s6_atm_login_body")); d["accessToken"]="[JWT emitido]"; print(json.dumps(d,ensure_ascii=False))'
} > "$EVID/04_jwt_login.txt"

{
    echo "Usuario: webuser / ROLE_WEB"
    echo "Endpoint: GET /api/bff/web/accounts/104"
    echo
    curl -sS -i \
        -H "Authorization: Bearer $WEB_TOKEN" \
        http://localhost:8082/api/bff/web/accounts/104
} > "$EVID/05_web_200.txt"

{
    echo "Usuario: mobileuser / ROLE_MOBILE"
    echo "Endpoint: GET /api/bff/mobile/accounts/104"
    echo
    curl -sS -i \
        -H "Authorization: Bearer $MOBILE_TOKEN" \
        http://localhost:8083/api/bff/mobile/accounts/104
} > "$EVID/06_mobile_200.txt"

{
    echo "Usuario: atmuser / ROLE_ATM"
    echo "Endpoint: GET /api/bff/atm/accounts/104/balance"
    echo
    curl -sS -i \
        -H "Authorization: Bearer $ATM_TOKEN" \
        http://localhost:8084/api/bff/atm/accounts/104/balance
} > "$EVID/07_atm_200.txt"

{
    echo "Prueba: acceso Web sin JWT"
    echo
    curl -sS -i http://localhost:8082/api/bff/web/accounts/104
} > "$EVID/08_sin_token_401.txt"

{
    echo "Usuario autenticado: webuser / ROLE_WEB"
    echo "Endpoint solicitado: BFF Mobile"
    echo
    curl -sS -i \
        -H "Authorization: Bearer $WEB_TOKEN" \
        http://localhost:8083/api/bff/mobile/accounts/104
} > "$EVID/09_rol_incorrecto_403.txt"

{
    echo "Usuario: mobileuser / ROLE_MOBILE"
    echo "Endpoint: GET /api/bff/mobile/accounts/104/transactions"
    echo
    curl -sS -i \
        -H "Authorization: Bearer $MOBILE_TOKEN" \
        http://localhost:8083/api/bff/mobile/accounts/104/transactions
} > "$EVID/10_mobile_ultimos_5.txt"

curl -sS \
    -H "Authorization: Bearer $MOBILE_TOKEN" \
    http://localhost:8083/api/bff/mobile/accounts/104/transactions \
    | python3 -c 'import json,sys; d=json.load(sys.stdin); print("Cantidad de movimientos:",len(d))'

{
    echo "Cuenta: 104"
    echo "Usuario: atmuser"
    echo
    curl -sS -i \
        -H "Authorization: Bearer $ATM_TOKEN" \
        http://localhost:8084/api/bff/atm/accounts/104/balance
} > "$EVID/11_atm_saldo_inicial.txt"

{
    echo "Cuenta: 104"
    echo "Monto: 10000"
    echo "Usuario: atmuser"
    echo
    curl -sS -i -X POST \
        -H "Authorization: Bearer $ATM_TOKEN" \
        -H "Content-Type: application/json" \
        -d '{"amount":10000}' \
        http://localhost:8084/api/bff/atm/accounts/104/withdraw
} > "$EVID/12_atm_retiro.txt"

{
    echo "Cuenta: 104"
    echo "Saldo final por BFF y verificacion PostgreSQL:"
    echo
    curl -sS -i \
        -H "Authorization: Bearer $ATM_TOKEN" \
        http://localhost:8084/api/bff/atm/accounts/104/balance
    echo
    echo "CONSULTA POSTGRESQL:"
    echo "SQL: SELECT cuenta_id,saldo_final FROM intereses_mensuales WHERE cuenta_id=104;"
    docker exec -i banco-xyz-postgres \
        psql -U banco_user -d banco_xyz \
        -c "SELECT cuenta_id,saldo_final FROM intereses_mensuales WHERE cuenta_id=104;"
} > "$EVID/13_atm_saldo_final.txt"
```

El escenario esperado es saldo inicial `10200.00`, retiro `10000`, saldo final y PostgreSQL `200.00`.

### Circuit Breaker, recuperación y validación

```bash
kill "$(cat /tmp/s6_core.pid)"

for i in {1..30}; do
    if ! ss -ltn | grep -q ':8081 '; then
        echo "Core Banking detenido."
        break
    fi
    sleep 1
done

ss -ltnp | grep ':8081' || echo "Puerto 8081 libre - Core detenido correctamente"

{
    echo "Core Banking detenido."
    echo
    curl -sS -i \
        -H "Authorization: Bearer $WEB_TOKEN" \
        http://localhost:8082/api/bff/web/accounts/104
} > "$EVID/14_circuit_breaker_web.txt"

{
    echo "Core Banking detenido."
    echo
    curl -sS -i \
        -H "Authorization: Bearer $MOBILE_TOKEN" \
        http://localhost:8083/api/bff/mobile/accounts/104
} > "$EVID/15_circuit_breaker_mobile.txt"

{
    echo "Core Banking detenido."
    echo
    curl -sS -i \
        -H "Authorization: Bearer $ATM_TOKEN" \
        http://localhost:8084/api/bff/atm/accounts/104/balance
} > "$EVID/16_circuit_breaker_atm.txt"

nohup java -jar core-banking-service/target/core-banking-service-1.0.0.jar \
    > /tmp/s6_core.log 2>&1 &

echo $! > /tmp/s6_core.pid
wait_url "http://localhost:8081/actuator/health" "Core Banking recuperado"

for i in {1..60}; do
    if curl -fsS \
        -H "Accept: application/xml" \
        http://localhost:8761/eureka/apps/CORE-BANKING-SERVICE \
        | grep -q '<status>UP</status>'; then
        echo "CORE-BANKING-SERVICE nuevamente UP en Eureka."
        break
    fi
    sleep 2
done

RECOVERY_CODE=""

for i in {1..30}; do
    RECOVERY_CODE=$(
        curl -sS \
            -o /tmp/s6_recovery_body \
            -w "%{http_code}" \
            -H "Authorization: Bearer $WEB_TOKEN" \
            http://localhost:8082/api/bff/web/accounts/104
    )

    echo "Intento $i -> HTTP $RECOVERY_CODE"

    [ "$RECOVERY_CODE" = "200" ] && {
        echo "BFF Web recuperado correctamente."
        break
    }

    sleep 2
done

{
    echo "Core Banking reiniciado."
    echo
    echo "CORE-BANKING-SERVICE EN EUREKA:"
    curl -sS \
        -H "Accept: application/xml" \
        http://localhost:8761/eureka/apps/CORE-BANKING-SERVICE
    echo
    echo "LLAMADA WEB DESPUES DE LA RECUPERACION:"
    curl -sS -i \
        -H "Authorization: Bearer $WEB_TOKEN" \
        http://localhost:8082/api/bff/web/accounts/104
} > "$EVID/17_recuperacion_core.txt"

{
    echo "Puertos y procesos activos durante la prueba:"
    echo
    ss -ltnp | grep -E ':(8761|8888|8081|8082|8083|8084)\b'
} > "$EVID/19_servicios_activos.txt"

echo "=== ARCHIVOS DE EVIDENCIA ==="
find "$EVID" -maxdepth 1 -type f -printf '%f\n' | sort

echo
echo "=== RESULTADOS HTTP ==="
grep -H "HTTP/1.1" "$EVID"/*.txt

echo
echo "=== RESULTADO ATM ==="
grep -E '"balance"|"withdrawnAmount"|"finalBalance"|200.00' \
    "$EVID/11_atm_saldo_inicial.txt" \
    "$EVID/12_atm_retiro.txt" \
    "$EVID/13_atm_saldo_final.txt"

echo
echo "=== BUILD ==="
grep -E "Reactor Summary|BUILD SUCCESS|BUILD FAILURE" \
    "$EVID/18_build.txt"

echo
echo "=== EUREKA ==="
grep -E '<name>|<status>|<port' \
     "$EVID/03_servicios_registrados.xml"
```

Los resultados esperados son operaciones `200`, ausencia de token `401`, rol incorrecto `403`, los tres fallbacks `503` y nuevamente `200` tras recuperación. Los archivos esperados son `01_config_server.txt`, `01b_consumo_config_server.txt`, `02_eureka_server.txt`, `03_servicios_registrados.txt`, `03_servicios_registrados.xml`, `04_jwt_login.txt`, `05_web_200.txt`, `06_mobile_200.txt`, `07_atm_200.txt`, `08_sin_token_401.txt`, `09_rol_incorrecto_403.txt`, `10_mobile_ultimos_5.txt`, `11_atm_saldo_inicial.txt`, `12_atm_retiro.txt`, `13_atm_saldo_final.txt`, `14_circuit_breaker_web.txt`, `15_circuit_breaker_mobile.txt`, `16_circuit_breaker_atm.txt`, `17_recuperacion_core.txt`, `18_build.txt` y `19_servicios_activos.txt`.

### Ejecución alternativa completa mediante Docker Compose

Esta es una alternativa para facilitar la reproducción completa de la arquitectura. No se ejecuta después de la Opción A, no reemplaza el procedimiento detallado usado para generar las evidencias y no genera evidencias nuevas.

```bash
cd "/home/francisco/proyecto/BACKEND III/SEMANA 6/Exp3_S6_Grupo11/proyecto"

docker compose down -v --remove-orphans
docker compose build
docker compose up -d

docker compose ps -a
```

El resultado esperado es PostgreSQL, Config Server, Eureka, Core Banking y los tres BFF en ejecución saludable; `batch-init` debe finalizar con `Exited (0)`. La representación exacta puede variar según la versión de Docker Compose, pero el estado funcional debe ser equivalente.

```bash
curl -sS http://localhost:8888/actuator/health
curl -sS http://localhost:8761/actuator/health
curl -sS http://localhost:8081/actuator/health
curl -sS http://localhost:8082/actuator/health
curl -sS http://localhost:8083/actuator/health
curl -sS http://localhost:8084/actuator/health

docker compose logs batch-init
```

El comando `docker compose logs batch-init` permite revisar el resultado de los tres Jobs y observar los estados `COMPLETED` impresos por el script.

Para detener la Opción B usar `docker compose down`. Para eliminar también PostgreSQL usar `docker compose down -v --remove-orphans`.

# Semana 07 - Tolerancia a fallos y arquitectura orientada a eventos

## Objetivo y arquitectura

Semana 07 evoluciona el retiro ATM: el endpoint oficial `POST /api/bff/atm/accounts/{id}/withdraw` deja de ejecutar una operacion sincronica y pasa a publicar `WithdrawalRequested` en Kafka. El BFF responde `202 Accepted` con `transactionId` y `PENDING`; Core Banking procesa, persiste y publica `WithdrawalCompleted` o `WithdrawalRejected`. La ruta `/withdraw/async` y el retiro HTTP sincronico hacia Core ya no existen. Se mantiene Spring Cloud Config, Eureka, LoadBalancer, JWT por canal, PostgreSQL, Spring Batch y los Circuit Breakers de Resilience4j.

El flujo usa arquitectura Event-Driven con Kafka porque permite aceptar la solicitud ATM rapidamente y procesarla de forma desacoplada. No se implementa Saga: el retiro solo requiere una validacion y una actualizacion dentro del Core Banking.

El BFF ATM produce `WithdrawalRequested` y usa `accountId` como Kafka key. Asi, los eventos de una misma cuenta conservan orden dentro de una particion. Core Banking consume con el grupo `core-banking-withdrawal-group`, configurado con concurrencia 3. Los tres topics tienen 3 particiones y factor de replicacion 1.

- `bank.withdrawal.requested`: BFF ATM -> Core Banking.
- `bank.withdrawal.completed`: resultado exitoso publicado por Core Banking.
- `bank.withdrawal.rejected`: resultado rechazado publicado por Core Banking.

Los eventos compartidos viven en el modulo Maven `shared-events`; contiene solo los contratos de los tres eventos y los nombres de topics. Esta decision evita duplicar DTOs entre BFF y Core sin agregar un servicio nuevo.

Cada evento contiene `eventId`, `transactionId`, `accountId`, `amount` y `occurredAt`. Los eventos de resultado agregan solamente saldo final o motivo de rechazo cuando corresponde.

## Selección y justificación de arquitectura y patrón

Para Semana 07 se selecciona una arquitectura Event-Driven utilizando Apache Kafka como plataforma de mensajería asíncrona. El retiro ATM desacopla la solicitud recibida por BFF ATM del procesamiento realizado por Core Banking: se publica `WithdrawalRequested` y, una vez procesado, se emite `WithdrawalCompleted` o `WithdrawalRejected`.

Kafka fue la alternativa seleccionada frente a JMS para este caso de uso porque la actividad requiere trabajar explícitamente con eventos, topics, particiones y consumer groups. Sus tres particiones y la concurrencia 3 del grupo `core-banking-withdrawal-group` permiten demostrar procesamiento distribuido y escalabilidad. Esta elección no descarta JMS; Kafka se ajusta mejor a la demostración práctica de arquitectura orientada a eventos requerida en esta semana.

El patrón Event Sourcing se aplica de forma acotada exclusivamente al flujo de retiro ATM. La tabla `banking_events` conserva un historial inmutable para trazabilidad y auditoría de las secuencias `WithdrawalRequested` → `WithdrawalCompleted` y `WithdrawalRequested` → `WithdrawalRejected`. Banco XYZ no se migra por completo a Event Sourcing: el saldo operacional heredado continúa como proyección del estado actual.

Saga fue evaluado, pero no se implementa porque el retiro actual no coordina una transacción distribuida entre múltiples microservicios ni requiere acciones compensatorias entre servicios independientes. Las consultas que necesitan una respuesta inmediata pueden continuar por HTTP síncrono; Resilience4j protege esas comunicaciones entre los BFF y Core Banking donde corresponde.

## Estructura incorporada en Semana 07

La estructura relevante para comprender el flujo de retiro asíncrono es la siguiente:

```text
proyecto/
├── shared-events/          Contratos WithdrawalRequested, WithdrawalCompleted,
│                           WithdrawalRejected y nombres de topics
├── bff-atm-service/        Recibe el retiro ATM, lo publica y responde 202/PENDING
├── core-banking-service/   Consume, procesa, registra eventos e idempotencia
├── bff-web-service/        Consultas síncronas del canal Web
├── bff-mobile-service/     Consultas síncronas del canal Mobile
├── config-server/          Configuración centralizada
├── eureka-server/          Descubrimiento de servicios
└── docker-compose.yml      PostgreSQL, Kafka, Config Server, Eureka, Core y BFF
```

Core Banking utiliza `banking_events` como Event Store, `processed_events` para evitar reprocesos y `withdrawal_transactions` para consultar el estado de cada retiro. Docker Compose integra PostgreSQL, Kafka, Config Server, Eureka, Core Banking y los tres BFF de la solución.

## Diagrama de arquitectura

El diagrama de arquitectura correspondiente a Semana 07 es elaborado utilizando Archify. El diagrama representará BFF ATM, Core Banking, PostgreSQL, Kafka y sus tres topics; los eventos `WithdrawalRequested`, `WithdrawalCompleted` y `WithdrawalRejected`; el productor, consumidor, consumer group, tres particiones, Event Store, idempotencia y Resilience4j en las comunicaciones HTTP síncronas.

## Event Store e idempotencia

El alcance de Event Sourcing es acotado al flujo nuevo. `banking_events` almacena de forma inmutable los eventos `WithdrawalRequested`, `WithdrawalCompleted` o `WithdrawalRejected`. `intereses_mensuales.saldo_final` sigue siendo la proyeccion operacional heredada.

`processed_events` registra cada `eventId` procesado. Si Kafka reentrega el mismo evento, Core Banking lo ignora antes de descontar saldo. `withdrawal_transactions` conserva `PENDING`, `COMPLETED` o `REJECTED` y permite consultar el resultado. La persistencia, la validacion y la actualizacion del saldo se ejecutan dentro de una transaccion JDBC.

## Endpoints nuevos

El rol `ATM` y JWT se mantienen obligatorios.

```text
POST /api/bff/atm/accounts/{accountId}/withdraw
GET  /api/bff/atm/accounts/transactions/{transactionId}
```

La solicitud responde `202 Accepted` con `transactionId` y `PENDING`. El endpoint de consulta retorna el estado final cuando Core procesa el evento. No existe una variante `/withdraw/async` ni una llamada HTTP sincronica de retiro hacia Core.

## Ejecucion

### Paso 00 - Preparar entorno limpio

Este paso no genera una evidencia numerada. Debe ejecutarse desde el directorio indicado: `docker compose down -v --remove-orphans` afecta únicamente los recursos asociados a este Compose y elimina los datos locales persistidos por Docker Compose para este proyecto. Úselo solo cuando se desea reproducir las evidencias desde cero: elimina la base PostgreSQL anterior y, si Compose llegara a persistir Kafka en volúmenes del proyecto, también esa persistencia.

La preparación contempla elimina únicamente archivos `.txt` de evidencias de Semana 07, mediante el comando `rm`.

> **Solo si Docker Desktop/WSL presenta error con `docker-credential-desktop.exe`:** antes de ejecutar comandos Docker en la terminal, usar la configuración temporal siguiente. Es una solución local de entorno; no modifica Docker Compose ni la configuración global de Docker.
>
> ```bash
> mkdir -p /tmp/docker-config-s7
> printf '{}\n' > /tmp/docker-config-s7/config.json
> export DOCKER_CONFIG=/tmp/docker-config-s7
> docker info >/dev/null && echo "Docker OK"
> ```

Resultado esperado: `Docker OK`.

```bash
cd "/home/francisco/proyecto/BACKEND III/SEMANA 7/Exp3_S7_Grupo11/proyecto"

EVID="/home/francisco/proyecto/BACKEND III/SEMANA 7/Exp3_S7_Grupo11/evidencias/semana 07"
mkdir -p "$EVID"

set -o pipefail

docker compose down -v --remove-orphans
rm -f "$EVID"/*.txt

docker compose up -d postgres kafka

wait_url() {
    local url="$1"
    local nombre="$2"

    for i in {1..60}; do
        if curl -fsS "$url" > /dev/null; then
            echo "$nombre disponible."
            return 0
        fi
        sleep 2
    done

    echo "ERROR: $nombre no estuvo disponible a tiempo." >&2
    return 1
}

POSTGRES_OK=0
for i in {1..30}; do
    if docker compose exec -T postgres \
        pg_isready -U banco_user -d banco_xyz; then
        echo "PostgreSQL disponible."
        POSTGRES_OK=1
        break
    fi
    sleep 2

done

if [ "$POSTGRES_OK" -eq 0 ]; then
    echo "ERROR: PostgreSQL no estuvo disponible a tiempo."
fi

KAFKA_OK=0
for i in {1..30}; do
    if docker compose exec -T kafka \
        /opt/kafka/bin/kafka-topics.sh \
        --bootstrap-server localhost:9092 \
        --list > /dev/null; then
        echo "Kafka disponible."
        KAFKA_OK=1
        break
    fi
    sleep 2

done

if [ "$KAFKA_OK" -eq 0 ]; then
    echo "ERROR: Kafka no estuvo disponible a tiempo."
fi

docker compose ps
```

Después del levantamiento completo del Paso 03, `batch-init` ejecuta los Jobs reales `dailyTransactionsJob`, `monthlyInterestJob` y `annualStatementsJob`. Esos Jobs crean el esquema definido por `schema.sql`, las tablas Batch y los datos de `intereses_mensuales`, incluidos los datos deterministas de la cuenta 104. Las tablas `banking_events`, `processed_events` y `withdrawal_transactions` también provienen de `schema.sql` y estarán vacías antes de las operaciones de los pasos 11 y 13.

### Paso 01 - Build del proyecto

Compila los ocho módulos, ejecuta `verify` y deja el resumen Maven completo en `01_build.txt`.

```bash
{
    echo "=== BUILD SEMANA 07 ==="
    echo "Comando: ./mvnw clean verify"
    echo
    ./mvnw clean verify
} 2>&1 | tee "$EVID/01_build.txt"
```

Resultado esperado: `BUILD SUCCESS`.

### Paso 02 - Tests

Ejecuta el objetivo Maven de pruebas sin una segunda limpieza del árbol ya construido y guarda el detalle de clases y resultados.

```bash
{
    echo "=== TESTS SEMANA 07 ==="
    echo "Comando: ./mvnw test"
    echo
    ./mvnw test
} 2>&1 | tee "$EVID/02_tests.txt"
```

Resultado esperado: tests sin fallos ni errores.

### Paso 03 - Levantamiento completo con Docker Compose

Construye las imágenes actuales, inicia todos los servicios y espera sus healthchecks HTTP. `batch-init` finaliza con `Exited (0)` porque es un inicializador, no un servicio permanente.

```bash
docker compose up -d --build

wait_url "http://localhost:8888/actuator/health" "Config Server"
wait_url "http://localhost:8761/actuator/health" "Eureka"
wait_url "http://localhost:8081/actuator/health" "Core Banking"
wait_url "http://localhost:8082/actuator/health" "BFF Web"
wait_url "http://localhost:8083/actuator/health" "BFF Mobile"
wait_url "http://localhost:8084/actuator/health" "BFF ATM"

{
    echo "=== SERVICIOS DOCKER COMPOSE ==="
    docker compose ps -a
    echo
    echo "=== BATCH INIT ==="
    docker compose logs --no-color batch-init
} 2>&1 | tee "$EVID/03_docker_servicios.txt"
```

Resultado esperado: `postgres` y `kafka` saludables, todos los servicios HTTP saludables y `batch-init` terminado correctamente.

### Paso 04 - Config Server

Consulta la configuración real de `core-banking-service` servida desde `./config-repo`.

```bash
{
    echo "=== CONFIG SERVER ==="
    echo "GET /core-banking-service/default"
    echo
    curl -sS -i http://localhost:8888/core-banking-service/default
} | tee "$EVID/04_config_server.txt"
```

Resultado esperado: HTTP `200` y `propertySources` con `file:config-repo/core-banking-service.yml`.

### Paso 05 - Servicios registrados en Eureka

Espera hasta que los cuatro microservicios de negocio estén registrados y luego registra la respuesta XML.

```bash
for i in {1..60}; do
    APPS=$(curl -sS -H "Accept: application/xml" http://localhost:8761/eureka/apps)

    if echo "$APPS" | grep -q '<name>CORE-BANKING-SERVICE</name>' \
        && echo "$APPS" | grep -q '<name>BFF-WEB-SERVICE</name>' \
        && echo "$APPS" | grep -q '<name>BFF-MOBILE-SERVICE</name>' \
        && echo "$APPS" | grep -q '<name>BFF-ATM-SERVICE</name>'; then
        echo "Los cuatro servicios están UP en Eureka."
        break
    fi

    if [ "$i" -eq 60 ]; then
        echo "ERROR: registro incompleto en Eureka." >&2
        break
    fi

    sleep 2
done

{
    echo "=== EUREKA: SERVICIOS REGISTRADOS ==="
    curl -sS \
        -H "Accept: application/xml" \
        http://localhost:8761/eureka/apps \
        | grep -E '<name>|<status>|<port'
} | tee "$EVID/05_eureka_servicios.txt"
```

Resultado esperado: `CORE-BANKING-SERVICE`, `BFF-WEB-SERVICE`, `BFF-MOBILE-SERVICE` y `BFF-ATM-SERVICE` con estado `UP`.

### Paso 06 - Topics Kafka

Lista los topics reales desde el contenedor Kafka.

```bash
{
    echo "=== TOPICS KAFKA ==="
    docker compose exec -T kafka \
        /opt/kafka/bin/kafka-topics.sh \
        --bootstrap-server localhost:9092 \
        --list
} | tee "$EVID/06_kafka_topics.txt"
```

Resultado esperado: `bank.withdrawal.requested`, `bank.withdrawal.completed` y `bank.withdrawal.rejected`.

### Paso 07 - Particiones Kafka

Describe el topic de solicitudes para comprobar sus tres particiones.

```bash
{
    echo "=== PARTICIONES KAFKA ==="
    docker compose exec -T kafka \
        /opt/kafka/bin/kafka-topics.sh \
        --bootstrap-server localhost:9092 \
        --describe \
        --topic bank.withdrawal.requested
} | tee "$EVID/07_kafka_particiones.txt"
```

Resultado esperado: `PartitionCount: 3` y particiones `0`, `1` y `2`.

### Paso 08 - Consumer group Kafka

Muestra las asignaciones, offsets, lag y consumidores del grupo real de Core Banking.

```bash
{
    echo "=== CONSUMER GROUP KAFKA ==="
    docker compose exec -T kafka \
        /opt/kafka/bin/kafka-consumer-groups.sh \
        --bootstrap-server localhost:9092 \
        --describe \
        --group core-banking-withdrawal-group
} | tee "$EVID/08_kafka_consumer_group.txt"
```

Resultado esperado: filas del grupo `core-banking-withdrawal-group`; inicialmente los offsets pueden estar vacíos hasta el primer retiro.

### Paso 09 - JWT de ATM y tokens de trabajo

Obtiene tokens nuevos durante esta ejecución. El JWT completo se mantiene solo en variables de memoria; la evidencia muestra una versión enmascarada. También se crean `WEB_TOKEN` y `MOBILE_TOKEN` para los pasos posteriores sin generar evidencias extra.

```bash
ATM_LOGIN=$(curl -sS \
    -X POST \
    -H "Content-Type: application/json" \
    -d '{"username":"atmuser","password":"atm123"}' \
    http://localhost:8084/auth/login)

ATM_TOKEN=$(printf '%s' "$ATM_LOGIN" | python3 -c \
    'import json,sys; print(json.load(sys.stdin)["accessToken"])')

WEB_LOGIN=$(curl -sS \
    -X POST \
    -H "Content-Type: application/json" \
    -d '{"username":"webuser","password":"web123"}' \
    http://localhost:8082/auth/login)

WEB_TOKEN=$(printf '%s' "$WEB_LOGIN" | python3 -c \
    'import json,sys; print(json.load(sys.stdin)["accessToken"])')

MOBILE_LOGIN=$(curl -sS \
    -X POST \
    -H "Content-Type: application/json" \
    -d '{"username":"mobileuser","password":"mobile123"}' \
    http://localhost:8083/auth/login)

MOBILE_TOKEN=$(printf '%s' "$MOBILE_LOGIN" | python3 -c \
    'import json,sys; print(json.load(sys.stdin)["accessToken"])')

{
    echo "=== JWT ATM ==="
    echo "POST /auth/login con atmuser"
    printf '%s' "$ATM_LOGIN" | python3 -c '
import json,sys
item=json.load(sys.stdin)
token=item["accessToken"]
item["accessToken"]=token[:16] + "...[JWT oculto]"
print(json.dumps(item, ensure_ascii=False))
'
    echo "Longitudes de tokens en memoria: ATM=${#ATM_TOKEN}, WEB=${#WEB_TOKEN}, MOBILE=${#MOBILE_TOKEN}"
} | tee "$EVID/09_jwt_atm.txt"
```

Resultado esperado: rol `ATM`; no se imprime ni se hardcodea un JWT completo.

### Paso 10 - Saldo inicial

La cuenta estable de la base inicial es `104`. Se define `ACCOUNT_ID` para los pasos siguientes y se consulta su saldo mediante el BFF ATM protegido.

```bash
ACCOUNT_ID=104

{
    echo "=== SALDO INICIAL ==="
    echo "Cuenta: $ACCOUNT_ID"
    curl -sS -i \
        -H "Authorization: Bearer $ATM_TOKEN" \
        "http://localhost:8084/api/bff/atm/accounts/$ACCOUNT_ID/balance"
} | tee "$EVID/10_saldo_inicial.txt"
```

Resultado esperado: HTTP `200` y saldo inicial reproducible `10200.00`.

### Paso 11 - Retiro oficial exitoso

Publica un retiro por Kafka, extrae dinámicamente `TX_OK` y consulta el estado hasta que deja de estar `PENDING`. No se usa ningún UUID de una ejecución anterior.

```bash
curl -sS \
    -D /tmp/s7_withdraw_ok_headers \
    -o /tmp/s7_withdraw_ok_body \
    -X POST \
    -H "Authorization: Bearer $ATM_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"amount":10000}' \
    "http://localhost:8084/api/bff/atm/accounts/$ACCOUNT_ID/withdraw"

TX_OK=$(python3 -c \
    'import json; print(json.load(open("/tmp/s7_withdraw_ok_body"))["transactionId"])')

for i in {1..60}; do
    STATUS_OK=$(curl -fsS \
        -H "Authorization: Bearer $ATM_TOKEN" \
        "http://localhost:8084/api/bff/atm/accounts/transactions/$TX_OK" || true)

    if [ -n "$STATUS_OK" ] \
        && printf '%s' "$STATUS_OK" | python3 -c \
            'import json,sys; raise SystemExit(0 if json.load(sys.stdin)["status"] == "COMPLETED" else 1)'; then
        break
    fi

    if [ "$i" -eq 60 ]; then
        echo "ERROR: TX_OK no llegó a COMPLETED." >&2
        break
    fi

    sleep 2
done

{
    echo "=== RETIRO OFICIAL EXITOSO ==="
    cat /tmp/s7_withdraw_ok_headers
    cat /tmp/s7_withdraw_ok_body
    echo
    echo "TX_OK=$TX_OK"
    echo "Estado final:"
    printf '%s\n' "$STATUS_OK"
} | tee "$EVID/11_retiro_oficial_solicitud.txt"
```

Resultado esperado: solicitud HTTP `202`, estado inicial `PENDING` y estado final `COMPLETED`.

### Paso 12 - Logs Kafka del retiro exitoso

Filtra los logs reales del BFF ATM y Core Banking con el `transactionId` recién generado. Muestra productor, consumidor, partición/hilo y resultado del retiro.

```bash
{
    echo "=== LOGS KAFKA PARA TX_OK=$TX_OK ==="
    docker compose logs --no-color bff-atm-service core-banking-service \
        | grep -F "$TX_OK"
} | tee "$EVID/12_logs_kafka.txt"
```

Resultado esperado: líneas `WithdrawalRequested`, `[Kafka][Consumer]`, partición, hilo y confirmación de retiro completado.

### Paso 13 - Retiro rechazado

El primer retiro dejó el saldo de la cuenta 104 en `200.00`; por eso otro retiro de `10000` es determinísticamente rechazado. Se guarda `TX_REJECT` y se hace polling hasta `REJECTED`.

```bash
curl -sS \
    -D /tmp/s7_withdraw_reject_headers \
    -o /tmp/s7_withdraw_reject_body \
    -X POST \
    -H "Authorization: Bearer $ATM_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"amount":10000}' \
    "http://localhost:8084/api/bff/atm/accounts/$ACCOUNT_ID/withdraw"

TX_REJECT=$(python3 -c \
    'import json; print(json.load(open("/tmp/s7_withdraw_reject_body"))["transactionId"])')

for i in {1..60}; do
    STATUS_REJECT=$(curl -fsS \
        -H "Authorization: Bearer $ATM_TOKEN" \
        "http://localhost:8084/api/bff/atm/accounts/transactions/$TX_REJECT" || true)

    if [ -n "$STATUS_REJECT" ] \
        && printf '%s' "$STATUS_REJECT" | python3 -c \
            'import json,sys; raise SystemExit(0 if json.load(sys.stdin)["status"] == "REJECTED" else 1)'; then
        break
    fi

    if [ "$i" -eq 60 ]; then
        echo "ERROR: TX_REJECT no llegó a REJECTED." >&2
        break
    fi

    sleep 2
done

{
    echo "=== RETIRO RECHAZADO ==="
    cat /tmp/s7_withdraw_reject_headers
    cat /tmp/s7_withdraw_reject_body
    echo
    echo "TX_REJECT=$TX_REJECT"
    echo "Estado final:"
    printf '%s\n' "$STATUS_REJECT"
} | tee "$EVID/13_retiro_rechazado.txt"
```

Resultado esperado: HTTP `202`, estado final `REJECTED` y `rejectionReason` equivalente a `Saldo insuficiente`.

### Paso 14 - Saldo final

Comprueba que solo el retiro exitoso descontó saldo.

```bash
{
    echo "=== SALDO FINAL ==="
    echo "Cuenta: $ACCOUNT_ID"
    curl -sS -i \
        -H "Authorization: Bearer $ATM_TOKEN" \
        "http://localhost:8084/api/bff/atm/accounts/$ACCOUNT_ID/balance"
} | tee "$EVID/14_saldo_final.txt"
```

Resultado esperado: HTTP `200` y saldo `200.00`; el retiro rechazado no realiza un segundo descuento.

### Paso 15 - Event Store

Consulta PostgreSQL dentro del contenedor real y filtra exclusivamente las dos transacciones de esta ejecución.

```bash
{
    echo "=== BANKING EVENTS ==="
    docker compose exec -T postgres \
        psql -U banco_user -d banco_xyz \
        -c "
SELECT event_type, transaction_id, account_id, amount, occurred_at
FROM banking_events
WHERE transaction_id IN ('$TX_OK', '$TX_REJECT')
ORDER BY occurred_at;"

    echo
    echo "=== WITHDRAWAL TRANSACTIONS ==="
    docker compose exec -T postgres \
        psql -U banco_user -d banco_xyz \
        -c "
SELECT transaction_id, status, final_balance, rejection_reason, updated_at
FROM withdrawal_transactions
WHERE transaction_id IN ('$TX_OK', '$TX_REJECT')
ORDER BY created_at;"
} | tee "$EVID/15_event_store.txt"
```

Resultado esperado: `WithdrawalRequested` y `WithdrawalCompleted` para `TX_OK`; `WithdrawalRequested` y `WithdrawalRejected` para `TX_REJECT`.

### Paso 16 - Estado de transacciones

Consulta ambos estados por la API oficial del BFF ATM.

```bash
{
    echo "=== ESTADO TX_OK ==="
    curl -sS \
        -H "Authorization: Bearer $ATM_TOKEN" \
        "http://localhost:8084/api/bff/atm/accounts/transactions/$TX_OK"
    echo
    echo "=== ESTADO TX_REJECT ==="
    curl -sS \
        -H "Authorization: Bearer $ATM_TOKEN" \
        "http://localhost:8084/api/bff/atm/accounts/transactions/$TX_REJECT"
} | tee "$EVID/16_estado_transaccion.txt"
```

Resultado esperado: un objeto `COMPLETED` y otro `REJECTED`.

### Paso 17 - Idempotencia por eventId

Obtiene desde `banking_events` el `eventId` y el JSON exacto de `WithdrawalRequested` para `TX_OK`. Lo republica en el topic real con el header Spring `__TypeId__`, usa la cuenta como Kafka key y verifica que Core Banking lo ignore sin descontar nuevamente el saldo.

```bash
EVENT_ID_OK=$(docker compose exec -T postgres \
    psql -At -U banco_user -d banco_xyz \
    -c "
SELECT event_id
FROM banking_events
WHERE transaction_id = '$TX_OK'
  AND event_type = 'WithdrawalRequested';")

EVENT_JSON=$(docker compose exec -T postgres \
    psql -At -U banco_user -d banco_xyz \
    -c "
SELECT payload::text
FROM banking_events
WHERE event_id = '$EVENT_ID_OK';")

EVENT_ACCOUNT_ID=$(printf '%s' "$EVENT_JSON" | python3 -c \
    'import json,sys; print(json.load(sys.stdin)["accountId"])')

BALANCE_BEFORE=$(docker compose exec -T postgres \
    psql -At -U banco_user -d banco_xyz \
    -c "SELECT saldo_final FROM intereses_mensuales WHERE cuenta_id = $EVENT_ACCOUNT_ID;")

printf '__TypeId__:cl.duoc.bancoxyz.events.WithdrawalRequestedEvent|%s|%s\n' \
    "$EVENT_ACCOUNT_ID" "$EVENT_JSON" \
    | docker compose exec -T kafka \
        /opt/kafka/bin/kafka-console-producer.sh \
        --bootstrap-server localhost:9092 \
        --topic bank.withdrawal.requested \
        --property parse.headers=true \
        --property headers.delimiter='|' \
        --property headers.separator=',' \
        --property headers.key.separator=':' \
        --property parse.key=true \
        --property key.separator='|'

IDEMPOTENCY_OK=0

for i in {1..30}; do
    if docker compose logs --no-color core-banking-service \
        | grep -F -q "[Idempotency] Evento $EVENT_ID_OK ya procesado"; then
        IDEMPOTENCY_OK=1
        echo "Evento duplicado detectado en el intento $i."
        break
    fi

    sleep 1
done

BALANCE_AFTER=$(docker compose exec -T postgres \
    psql -At -U banco_user -d banco_xyz \
    -c "SELECT saldo_final FROM intereses_mensuales WHERE cuenta_id = $EVENT_ACCOUNT_ID;")

{
    echo "=== IDEMPOTENCIA ==="
    echo "EVENT_ID_OK=$EVENT_ID_OK"
    echo "Saldo antes: $BALANCE_BEFORE"
    echo "Saldo después: $BALANCE_AFTER"
    echo

    if [ "$IDEMPOTENCY_OK" -eq 1 ]; then
        echo "=== LOG DE IDEMPOTENCIA ==="
        docker compose logs --no-color core-banking-service \
            | grep -F "$EVENT_ID_OK"
    else
        echo "ERROR: no se observó el log de idempotencia tras 30 intentos."
    fi

    echo
    if [ "$IDEMPOTENCY_OK" -eq 1 ] \
        && [ "$BALANCE_BEFORE" = "$BALANCE_AFTER" ]; then
        echo "RESULTADO: OK - El evento duplicado fue ignorado y el saldo no cambió."
    else
        echo "RESULTADO: ERROR - Revisar la prueba de idempotencia."
    fi
} | tee "$EVID/17_idempotencia.txt"
```

Resultado esperado: `[Idempotency] Evento ... ya procesado, ignorando`, el mismo saldo antes y después y `RESULTADO: OK`.

### Paso 18 - Escalabilidad Kafka

Publica seis retiros pequeños sobre cuentas distintas, con claves diferentes, y muestra particiones, hilos y estado del consumer group. Los identificadores de transacción se generan dinámicamente en cada solicitud.

```bash
SCALING_ACCOUNTS=(102 105 106 108 110 111)

{
    echo "=== SOLICITUDES PARA ESCALABILIDAD ==="

    for account_id in "${SCALING_ACCOUNTS[@]}"; do
        response=$(curl -sS \
            -X POST \
            -H "Authorization: Bearer $ATM_TOKEN" \
            -H "Content-Type: application/json" \
            -d '{"amount":1}' \
            "http://localhost:8084/api/bff/atm/accounts/$account_id/withdraw")
        echo "accountId=$account_id response=$response"
    done

    echo
    echo "=== LOGS DE CONSUMO ==="
    sleep 2
    docker compose logs --no-color core-banking-service \
        | grep -E '\[Kafka\]\[Consumer\].*partition='

    echo
    echo "=== CONSUMER GROUP ==="
    docker compose exec -T kafka \
        /opt/kafka/bin/kafka-consumer-groups.sh \
        --bootstrap-server localhost:9092 \
        --describe \
        --group core-banking-withdrawal-group
} | tee "$EVID/18_escalabilidad_kafka.txt"
```

Resultado esperado: actividad en las particiones `0`, `1` y `2`, hilos consumidores y lag observable o `0` una vez procesada la cola.

### Paso 19 - Resilience4j en operación normal

Prueba la consulta ATM protegida mientras Core Banking está disponible.

```bash
{
    echo "=== RESILIENCE4J NORMAL ==="
    curl -sS -i \
        -H "Authorization: Bearer $ATM_TOKEN" \
        "http://localhost:8084/api/bff/atm/accounts/$ACCOUNT_ID/balance"
} | tee "$EVID/19_resilience4j_normal.txt"
```

Resultado esperado: HTTP `200`.

### Paso 20 - Resilience4j ante fallo y apertura

Detiene únicamente el servicio Compose real `core-banking-service`, confirma la caída de su healthcheck y mantiene BFF ATM en ejecución para registrar los fallbacks controlados. No se mezcla `java -jar` con Docker Compose.

```bash
docker compose stop core-banking-service

for i in {1..30}; do
    if ! curl -fsS http://localhost:8081/actuator/health > /dev/null 2>&1; then
        echo "Core Banking detenido."
        break
    fi

    if [ "$i" -eq 30 ]; then
        echo "ERROR: Core Banking no se detuvo." >&2
        break
    fi

    sleep 1
done

{
    echo "=== RESILIENCE4J FALLO / OPEN ==="
    for attempt in {1..5}; do
        echo "Intento $attempt"
        curl -sS -i \
            -H "Authorization: Bearer $ATM_TOKEN" \
            "http://localhost:8084/api/bff/atm/accounts/$ACCOUNT_ID/balance"
        echo
    done
} | tee "$EVID/20_resilience4j_fallo_open.txt"
```

Resultado esperado: BFF ATM permanece disponible y devuelve HTTP `503` controlado.

### Paso 21 - Recuperación Resilience4j

Reinicia únicamente el contenedor existente de Core Banking, sin procesar dependencias. La opción `--no-deps` evita una nueva ejecución de `batch-init` y conserva el estado operacional de PostgreSQL; luego espera su healthcheck, el registro Eureka y la recuperación HTTP `200` del BFF ATM.

```bash
docker compose restart --no-deps core-banking-service
wait_url "http://localhost:8081/actuator/health" "Core Banking recuperado"

for i in {1..60}; do
    if curl -fsS \
        -H "Accept: application/xml" \
        http://localhost:8761/eureka/apps/CORE-BANKING-SERVICE \
        | grep -q '<status>UP</status>'; then
        echo "CORE-BANKING-SERVICE nuevamente UP en Eureka."
        break
    fi

    if [ "$i" -eq 60 ]; then
        echo "ERROR: Core Banking no se registró en Eureka." >&2
        break
    fi

    sleep 2
done

for i in {1..30}; do
    RECOVERY_HTTP=$(curl -sS \
        -o /tmp/s7_recovery_body \
        -w '%{http_code}' \
        -H "Authorization: Bearer $ATM_TOKEN" \
        "http://localhost:8084/api/bff/atm/accounts/$ACCOUNT_ID/balance")

    if [ "$RECOVERY_HTTP" = "200" ]; then
        break
    fi

    if [ "$i" -eq 30 ]; then
        echo "ERROR: BFF ATM no se recuperó." >&2
        break
    fi

    sleep 1
done

BALANCE_RECOVERED=$(python3 -c \
    'import json; print(json.load(open("/tmp/s7_recovery_body")).get("balance", ""))')


{
    echo "=== RECUPERACIÓN RESILIENCE4J ==="
    echo "CORE-BANKING-SERVICE en Eureka:"
    curl -sS \
        -H "Accept: application/xml" \
        http://localhost:8761/eureka/apps/CORE-BANKING-SERVICE \
        | grep -E '<name>|<status>|<port'
    echo
    echo "HTTP recuperado: $RECOVERY_HTTP"
    cat /tmp/s7_recovery_body
    echo
    echo "Saldo recuperado: $BALANCE_RECOVERED"
    if [ -n "$BALANCE_RECOVERED" ] \
        && printf '%s' "$BALANCE_RECOVERED" | python3 -c \
            'from decimal import Decimal; import sys; raise SystemExit(0 if Decimal(sys.stdin.read().strip()) == Decimal("200.00") else 1)'; then
        echo "Saldo operativo conservado: $BALANCE_RECOVERED"
    else
        echo "ERROR: El saldo cambio durante la recuperacion."
    fi
} | tee "$EVID/21_resilience4j_recuperacion.txt"
```

Resultado esperado: Core Banking `UP` en Eureka, HTTP `200` y saldo operativo `200.00`. La guía no inventa estados internos `OPEN`, `HALF_OPEN` o `CLOSED`; registra la respuesta observable real.

### Paso 22 - Seguridad y rutas retiradas

Comprueba los cuatro casos solicitados con los tokens creados en el Paso 09.

```bash
{
    echo "=== SIN JWT: 401 ==="
    curl -sS -i \
        -X POST \
        -H "Content-Type: application/json" \
        -d '{"amount":1}' \
        "http://localhost:8084/api/bff/atm/accounts/$ACCOUNT_ID/withdraw"

    echo
    echo "=== JWT WEB CONTRA ATM: 403 ==="
    curl -sS -i \
        -H "Authorization: Bearer $WEB_TOKEN" \
        "http://localhost:8084/api/bff/atm/accounts/$ACCOUNT_ID/balance"

    echo
    echo "=== RUTA /withdraw/async RETIRADA: 404 ==="
    curl -sS -i \
        -X POST \
        -H "Authorization: Bearer $ATM_TOKEN" \
        -H "Content-Type: application/json" \
        -d '{"amount":1}' \
        "http://localhost:8084/api/bff/atm/accounts/$ACCOUNT_ID/withdraw/async"

    echo
    echo "=== RETIRO HTTP SINCRÓNICO CORE RETIRADO: 404 ==="
    curl -sS -i \
        -X POST \
        -H "Content-Type: application/json" \
        -d '{"amount":1}' \
        "http://localhost:8081/api/core/accounts/$ACCOUNT_ID/withdraw"
} | tee "$EVID/22_seguridad_y_rutas_retiradas.txt"
```

Resultado esperado: HTTP `401`, `403`, `404` y `404`, respectivamente.

### Paso 23 - Regresión de componentes heredados

Comprueba servicios Config/Eureka/Core/BFF, seguridad, consultas síncronas disponibles y la ejecución Batch existente. Tras la recuperación del Core, espera activamente la propagación de la instancia hacia los tres BFF antes de registrar las consultas finales; no se prueba el retiro síncrono eliminado deliberadamente.

```bash
wait_bff_200() {
    local channel="$1"
    local token="$2"
    local url="$3"
    local response_file="$4"

    BFF_HTTP=000
    for attempt in {1..30}; do
        BFF_HTTP=$(curl -sS -i \
            -o "$response_file" \
            -w '%{http_code}' \
            -H "Authorization: Bearer $token" \
            "$url" || true)
        echo "BFF $channel intento $attempt: HTTP $BFF_HTTP"

        if [ "$BFF_HTTP" = "200" ]; then
            return 0
        fi

        sleep 2
    done

    echo "ERROR: BFF $channel no se recuperó."
    return 1
}

{
    echo "=== HEALTHCHECKS ==="
    for url in \
        http://localhost:8888/actuator/health \
        http://localhost:8761/actuator/health \
        http://localhost:8081/actuator/health \
        http://localhost:8082/actuator/health \
        http://localhost:8083/actuator/health \
        http://localhost:8084/actuator/health; do
        echo "$url"
        curl -sS "$url"
    done

    echo
    echo "=== CONFIG SERVER ==="
    curl -sS http://localhost:8888/core-banking-service/default \
        | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d["name"], d["propertySources"][0]["name"])'

    echo
    echo "=== EUREKA ==="
    curl -sS \
        -H "Accept: application/xml" \
        http://localhost:8761/eureka/apps \
        | grep -E '<name>|<status>'

    echo
    echo "=== CONSULTAS BFF ==="
    wait_bff_200 "Web" "$WEB_TOKEN" \
        "http://localhost:8082/api/bff/web/accounts/$ACCOUNT_ID" \
        /tmp/s7_regression_web_response || true
    echo "WEB HTTP FINAL: $BFF_HTTP"
    cat /tmp/s7_regression_web_response

    wait_bff_200 "Mobile" "$MOBILE_TOKEN" \
        "http://localhost:8083/api/bff/mobile/accounts/$ACCOUNT_ID" \
        /tmp/s7_regression_mobile_response || true
    echo "MOBILE HTTP FINAL: $BFF_HTTP"
    cat /tmp/s7_regression_mobile_response

    wait_bff_200 "ATM" "$ATM_TOKEN" \
        "http://localhost:8084/api/bff/atm/accounts/$ACCOUNT_ID/balance" \
        /tmp/s7_regression_atm_response || true
    echo "ATM HTTP FINAL: $BFF_HTTP"
    cat /tmp/s7_regression_atm_response

    echo
    echo "=== SEGURIDAD ==="
    SECURITY_HTTP=$(curl -sS -i \
        -o /tmp/s7_regression_no_jwt_response \
        -w '%{http_code}' \
        "http://localhost:8082/api/bff/web/accounts/$ACCOUNT_ID" || true)
    echo "SIN JWT HTTP FINAL: $SECURITY_HTTP"
    cat /tmp/s7_regression_no_jwt_response
    echo
    echo "=== BATCH INIT ==="
    docker compose logs --no-color batch-init \
        | grep -E 'dailyTransactionsJob|monthlyInterestJob|annualStatementsJob|BATCH INIT COMPLETED'
} | tee "$EVID/23_regresion_semana06.txt"
```

Resultado esperado: healthchecks `UP`, consultas protegidas `200`, sin JWT `401` y Jobs Batch `COMPLETED`. Si un BFF no llega a `200` después de 30 intentos separados por 2 segundos, el Paso 23 registra `ERROR` y continúa para dejar el diagnóstico en la evidencia.

### Paso 24 - Verificación final

Se ejecuta después de los pasos 01 a 23. `tee` crea `24_verificacion_final.txt` antes de contar, por lo que la validación debe informar exactamente 24 archivos `.txt`.

```bash
{
    echo "=== VERIFICACIÓN FINAL SEMANA 07 ==="
    echo
    echo "=== ARCHIVOS DE EVIDENCIA ==="
    find "$EVID" \
        -maxdepth 1 \
        -type f \
        -name '*.txt' \
        -printf '%f\n' \
        | sort

    echo
    echo "=== CANTIDAD DE EVIDENCIAS ==="
    find "$EVID" \
        -maxdepth 1 \
        -type f \
        -name '*.txt' \
        | wc -l

    echo
    echo "=== SERVICIOS ==="
    docker compose ps -a

    echo
    echo "=== BUILD ==="
    grep -E 'Reactor Summary|BUILD SUCCESS|BUILD FAILURE' "$EVID/01_build.txt"

    echo
    echo "=== HTTP PRINCIPALES ==="
    grep -H -E 'HTTP/.* (200|202|401|403|404|503)' \
        "$EVID/10_saldo_inicial.txt" \
        "$EVID/11_retiro_oficial_solicitud.txt" \
        "$EVID/14_saldo_final.txt" \
        "$EVID/19_resilience4j_normal.txt" \
        "$EVID/20_resilience4j_fallo_open.txt" \
        "$EVID/21_resilience4j_recuperacion.txt" \
        "$EVID/22_seguridad_y_rutas_retiradas.txt"
    echo
    echo "=== PASO 21 RECUPERACION ==="
    if grep -q 'HTTP recuperado: 200' "$EVID/21_resilience4j_recuperacion.txt" \
        && grep -Eq '"balance"[[:space:]]*:[[:space:]]*200(\.0+)?([,}]|$)' \
            "$EVID/21_resilience4j_recuperacion.txt"; then
        echo "PASO 21 RECUPERACION: OK"
    else
        echo "PASO 21 RECUPERACION: ERROR"
    fi
    echo
    echo "=== PASO 23 REGRESION ==="
    if grep -q 'WEB HTTP FINAL: 200' "$EVID/23_regresion_semana06.txt" \
        && grep -q 'MOBILE HTTP FINAL: 200' "$EVID/23_regresion_semana06.txt" \
        && grep -q 'ATM HTTP FINAL: 200' "$EVID/23_regresion_semana06.txt" \
        && grep -q 'SIN JWT HTTP FINAL: 401' "$EVID/23_regresion_semana06.txt"; then
        echo "PASO 23 REGRESION: OK"
    else
        echo "PASO 23 REGRESION: ERROR"
    fi

    echo "=== KAFKA ==="
    docker compose exec -T kafka \
        /opt/kafka/bin/kafka-consumer-groups.sh \
        --bootstrap-server localhost:9092 \
        --describe \
        --group core-banking-withdrawal-group

    echo
    echo "=== ESTADOS DE TRANSACCIONES ==="
    printf '%s\n' "$STATUS_OK"
    printf '%s\n' "$STATUS_REJECT"
} | tee "$EVID/24_verificacion_final.txt"
```

Resultado esperado: 24 archivos `.txt`, servicios activos, `BUILD SUCCESS`, respuestas HTTP principales, estado Kafka y transacciones `COMPLETED`/`REJECTED`.

## Colección final esperada

La carpeta `evidencias/semana 07` contiene exactamente los siguientes 24 archivos:

1. `01_build.txt`
2. `02_tests.txt`
3. `03_docker_servicios.txt`
4. `04_config_server.txt`
5. `05_eureka_servicios.txt`
6. `06_kafka_topics.txt`
7. `07_kafka_particiones.txt`
8. `08_kafka_consumer_group.txt`
9. `09_jwt_atm.txt`
10. `10_saldo_inicial.txt`
11. `11_retiro_oficial_solicitud.txt`
12. `12_logs_kafka.txt`
13. `13_retiro_rechazado.txt`
14. `14_saldo_final.txt`
15. `15_event_store.txt`
16. `16_estado_transaccion.txt`
17. `17_idempotencia.txt`
18. `18_escalabilidad_kafka.txt`
19. `19_resilience4j_normal.txt`
20. `20_resilience4j_fallo_open.txt`
21. `21_resilience4j_recuperacion.txt`
22. `22_seguridad_y_rutas_retiradas.txt`
23. `23_regresion_semana06.txt`
24. `24_verificacion_final.txt`

Advertencias para la futura ejecución manual: iniciar desde el Paso 00 elimina datos Compose locales del proyecto y las evidencias `.txt` de Semana 07; ejecutar siempre en una sola terminal; no reutilizar JWT, `transactionId` ni `eventId` de ejecuciones anteriores; y no lanzar una segunda ejecución mientras la primera siga en curso.


# Semana 08 - OAuth2, seguridad distribuida y preparación Cloud

## Objetivo

Semana 08 incorpora OAuth2.0 con Spring Authorization Server, Access Tokens JWT, OAuth2 Resource Servers y scopes por canal para proteger los servicios del Banco XYZ. Core Banking exige un token válido y los BFF propagan el Bearer en sus consultas HTTP. Docker y Docker Compose permiten ejecutar el stack completo de forma portable, conservando Kafka, Resilience4j y Spring Batch.

## Evolución de la seguridad

| Seguridad anterior      | Semana 08                        |
| ----------------------- | -------------------------------- |
| /auth/login propio      | /oauth2/token                    |
| JwtService propio       | Spring Authorization Server      |
| JWT HMAC propio         | Access Token JWT firmado con RSA |
| JwtAuthenticationFilter | OAuth2 Resource Server           |
| ROLE_WEB                | SCOPE_web                        |
| ROLE_MOBILE             | SCOPE_mobile                     |
| ROLE_ATM                | SCOPE_atm                        |

JWT continúa como formato del Access Token OAuth2. La emisión y validación propias fueron reemplazadas por Spring Authorization Server y Spring Security OAuth2 Resource Server.

## Arquitectura OAuth2

~~~mermaid
flowchart TD
    C[Clientes] -->|client_credentials| AS[Authorization Server :9000]
    AS -->|RSA / JWK| JWT[Access Token JWT]
    JWT --> W[BFF Web - SCOPE_web]
    JWT --> M[BFF Mobile - SCOPE_mobile]
    JWT --> A[BFF ATM - SCOPE_atm]
    W -->|Authorization: Bearer| CORE[Core Banking - Resource Server]
    M -->|Authorization: Bearer| CORE
    A -->|Authorization: Bearer en consultas HTTP| CORE
    A -->|WithdrawalRequested y ACK| K[Kafka - 3 particiones]
    K --> CORE
    CORE -->|WithdrawalCompleted / WithdrawalRejected| K
~~~

El cliente envía el encabezado Authorization: Bearer con su Access Token. Para consultas HTTP, cada BFF propaga el mismo token hacia Core, que vuelve a validarlo. El retiro ATM se publica como evento Kafka; el HTTP 202 confirma la aceptación tras el ACK, mientras el procesamiento bancario ocurre de forma asíncrona.

## Authorization Server

El módulo proyecto/authorization-server utiliza Java 17 y Spring Authorization Server. La configuración real define:

- Puerto 9000.
- POST /oauth2/token para obtener Access Tokens.
- Grant client_credentials y autenticación client_secret_basic.
- JWT firmado con una clave RSA de 2048 bits.
- GET /oauth2/jwks para consultar las claves públicas.
- Access Token con duración configurada de 30 minutos.
- RegisteredClientRepository en memoria.
- Secretos codificados mediante DelegatingPasswordEncoder, cuyo codificador predeterminado es BCrypt.
- GET /actuator/health público para infraestructura.

La clave RSA se genera al iniciar Authorization Server. Después de reiniciarlo se deben obtener tokens nuevos. Los secretos se configuran mediante OAUTH2_WEB_SECRET, OAUTH2_MOBILE_SECRET y OAUTH2_ATM_SECRET; esta sección utiliza variables y conserva sus valores fuera de los archivos de evidencias.

Las evidencias 05, 06 y 07 registraron HTTP 200, token_type Bearer, los scopes web, mobile y atm respectivamente y expires_in 1799 segundos. La evidencia 08 confirmó alg RS256 y los subjects web-client, mobile-client y atm-client, cada uno con su scope correspondiente. La diferencia entre exp e iat fue de 1800 segundos; expires_in refleja el tiempo restante al responder. Los Access Tokens completos y los secretos no se guardaron en las evidencias.

## Clientes y scopes

| client_id     | grant_type         | scope  |
| ------------- | ------------------ | ------ |
| web-client    | client_credentials | web    |
| mobile-client | client_credentials | mobile |
| atm-client    | client_credentials | atm    |

Spring Security convierte los scopes en autoridades: web → SCOPE_web, mobile → SCOPE_mobile y atm → SCOPE_atm.

## Resource Servers y propagación Bearer

| Servicio     | Puerto host | Protección                 |
| ------------ | ----------- | -------------------------- |
| BFF Web      | 8082        | SCOPE_web                  |
| BFF Mobile   | 8083        | SCOPE_mobile               |
| BFF ATM      | 8084        | SCOPE_atm                  |
| Core Banking | 8081        | Access Token OAuth2 válido |

Los Resource Servers validan firma, vigencia e issuer. /actuator/health es público para los healthchecks; las APIs bancarias requieren autenticación. Los tests WebBearerPropagationTest, MobileBearerPropagationTest y AtmBearerPropagationTest comprueban el encabezado Bearer hacia Core.

## Issuer por entorno

| Entorno          | issuer                           | jwk-set-uri                                  |
| ---------------- | -------------------------------- | -------------------------------------------- |
| Local, Maven/IDE | http://localhost:9000            | http://localhost:9000/oauth2/jwks            |
| Docker Compose   | http://authorization-server:9000 | http://authorization-server:9000/oauth2/jwks |

La configuración utiliza valores locales predeterminados y overrides mediante OAUTH2_ISSUER y OAUTH2_JWK_SET_URI. Core, los tres BFF y batch-init reciben los valores internos mediante Compose. Config Server continúa utilizando config-repo.

Desde el host se solicita el token en http://localhost:9000/oauth2/token. Bajo Compose, el claim iss real fue http://authorization-server:9000, que coincide con los Resource Servers. Estos valores corresponden a configuración por entorno y se mantienen tal como están implementados.

## Resultados de seguridad

| Operación ejecutada                | Resultado obtenido  | Evidencia |
| ---------------------------------- | ------------------- | --------- |
| Token web-client / scope web       | HTTP 200, Bearer    | 05        |
| Token mobile-client / scope mobile | HTTP 200, Bearer    | 06        |
| Token atm-client / scope atm       | HTTP 200, Bearer    | 07        |
| Web sin token                      | HTTP 401            | 09        |
| Web con scope web                  | HTTP 200 final      | 10        |
| Web con scope atm                  | HTTP 403            | 11        |
| Web con token inválido             | HTTP 401            | 12        |
| Mobile con scope mobile            | HTTP 200            | 13        |
| ATM, consulta con scope atm        | HTTP 200 final      | 14        |
| Core sin token                     | HTTP 401            | 15        |
| Core con token válido              | HTTP 200            | 16        |
| Retiro ATM autorizado              | HTTP 202, PENDING   | 17        |
| Consulta del retiro final          | HTTP 200, COMPLETED | 18        |

Las consultas autorizadas de Web (10) y saldo ATM (14) registraron un HTTP 503 transitorio antes del HTTP 200 final, durante la estabilización de las consultas hacia Core. Ambas evidencias terminaron con RESULTADO: PASS.

## Migración del JWT propio y componentes eliminados

Se eliminaron AuthController, JwtService y JwtAuthenticationFilter, del login /auth/login, de ROLE_WEB, ROLE_MOBILE, ROLE_ATM, de security.jwt.secret y de las dependencias jjwt-api, jjwt-impl y jjwt-jackson. Los usuarios del login propio fueron sustituidos por clientes OAuth2.

Las referencias históricas a esos componentes en semanas anteriores permanecen intactas para explicar la evolución.

### Nueve archivos eliminados

Rutas relativas a la carpeta de este README:

~~~text
proyecto/bff-web-service/src/main/java/cl/duoc/bancoxyz/security/AuthController.java
proyecto/bff-web-service/src/main/java/cl/duoc/bancoxyz/security/JwtService.java
proyecto/bff-web-service/src/main/java/cl/duoc/bancoxyz/security/JwtAuthenticationFilter.java
proyecto/bff-mobile-service/src/main/java/cl/duoc/bancoxyz/security/AuthController.java
proyecto/bff-mobile-service/src/main/java/cl/duoc/bancoxyz/security/JwtService.java
proyecto/bff-mobile-service/src/main/java/cl/duoc/bancoxyz/security/JwtAuthenticationFilter.java
proyecto/bff-atm-service/src/main/java/cl/duoc/bancoxyz/security/AuthController.java
proyecto/bff-atm-service/src/main/java/cl/duoc/bancoxyz/security/JwtService.java
proyecto/bff-atm-service/src/main/java/cl/duoc/bancoxyz/security/JwtAuthenticationFilter.java
~~~

## Validación automatizada

Se ejecutó mvn test desde proyecto/, sin flags para omitir pruebas. La ejecución real terminó con:

~~~text
Tests run: 92
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
~~~

| Módulo               | Tests en la suite completa |
| -------------------- | -------------------------: |
| Authorization Server |                          8 |
| Core Banking         |                         43 |
| BFF Web              |                         10 |
| BFF Mobile           |                         10 |
| BFF ATM              |                         21 |
| Total                |                         92 |

## Docker

El Dockerfile proyecto/docker/Dockerfile.service utiliza Java 17 JDK para compilar y Java 17 JRE para ejecutar. ARG MODULE selecciona el JAR de cada servicio. El archivo incorpora COPY authorization-server authorization-server.

La reproducción actual ejecutó docker compose build y terminó con RESULTADO: PASS en 02_docker_compose_build.txt. La evidencia 25 confirmó las ocho imágenes propias construidas:

~~~text
proyecto-authorization-server
proyecto-config-server
proyecto-eureka-server
proyecto-core-banking-service
proyecto-bff-web-service
proyecto-bff-mobile-service
proyecto-bff-atm-service
proyecto-batch-init
~~~

PostgreSQL y Kafka utilizan las imágenes configuradas directamente en docker-compose.yml y no requieren ser construidas mediante el Dockerfile del proyecto. La validación completa de la aplicación se realiza de forma independiente mediante mvn test.

## Docker Compose

Authorization Server utiliza la red default existente, publica 9000:9000 y verifica /actuator/health mediante curl. Core, BFF y batch-init esperan su estado healthy. Se conservan las dependencias de infraestructura y batch-init con service_completed_successfully.

| Componente           | Puerto host   | Estado estable |
| -------------------- | ------------- | -------------- |
| PostgreSQL           | 5433          | healthy        |
| Kafka                | 29092         | healthy        |
| Config Server        | 8888          | healthy        |
| Eureka Server        | 8761          | healthy        |
| Authorization Server | 9000          | healthy        |
| Core Banking         | 8081          | healthy        |
| BFF Web              | 8082          | healthy        |
| BFF Mobile           | 8083          | healthy        |
| BFF ATM              | 8084          | healthy        |
| batch-init           | Inicializador | Exited (0)     |

batch-init ejecutó dailyTransactionsJob, monthlyInterestJob y annualStatementsJob, registró COMPLETED y terminó con código 0.

La evidencia 04 registró siete healthchecks HTTP 200 con status UP. La evidencia 03 muestra el estado Compose de esa etapa del arranque; la comprobación final de los nueve servicios healthy y batch-init Exited (0), después de las pruebas de recuperación, está en la evidencia 34.

## Kafka y retiro asíncrono

Se consultaron los datos recién cargados y se seleccionó la cuenta 102 con saldo inicial 10200.00. El retiro de 10000.00 fue aceptado con HTTP 202 y PENDING; el productor espera el ACK de Kafka antes de responder.

La consulta de estado de la evidencia 18 obtuvo HTTP 200 y COMPLETED en el primer intento. Core descontó 10000.00 y el saldo final fue 200.00. El transactionId de esta ejecución queda identificado en las evidencias 17, 18 y 21, que relacionan la aceptación, el resultado final y la trazabilidad del retiro.

Los topics bank.withdrawal.requested, bank.withdrawal.completed y bank.withdrawal.rejected mostraron cada uno PartitionCount: 3, con particiones 0, 1 y 2 y factor de replicación 1. El consumer group core-banking-withdrawal-group mostró tres consumidores, uno por partición. La transacción se procesó en la partición 0, con current-offset 1, log-end-offset 1 y lag 0; las otras dos particiones no habían recibido mensajes y todavía no tenían offset confirmado en esa captura.

La configuración centralizada mostrada en la evidencia 27 mantiene concurrency: 3. Las evidencias 19 y 20 contienen las consultas reales de topics, particiones y consumer group. La evidencia 21 filtra los logs por transactionId y muestra WithdrawalRequested, consumo por Core, procesamiento COMPLETED y publicación en bank.withdrawal.completed. La consulta del Event Store confirma WithdrawalRequested y WithdrawalCompleted para la misma transacción.

En caso de error o timeout al publicar un retiro en Kafka, el servicio responde HTTP 503 con estado NOT_PUBLISHED. Este comportamiento fue validado mediante pruebas automatizadas incluidas en la suite Maven. Durante la ejecución manual no se realizó una prueba deteniendo Kafka para provocar este escenario.

### Idempotencia

La evidencia 22 fue generada nuevamente durante esta reproducción mediante la ejecución específica de AsyncWithdrawalServiceTest. Su método ignoresAnEventAlreadyProcessed obtuvo PASS: 1 test, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS y RESULTADO: PASS. El mismo comportamiento quedó cubierto dentro de la suite completa de 92 tests de la evidencia 01.

Este test comprueba que el servicio identifica un eventId ya procesado. La evidencia 22 corresponde a una prueba unitaria; durante la ejecución manual no se republicó un evento duplicado en la base.

## Resilience4j: configuración y validación final

Los Circuit Breakers `coreWeb`, `coreMobile` y `coreAtm` protegen las consultas HTTP de los BFF hacia Core. Config Server entregó los siguientes parámetros productivos mediante sus endpoints reales; los valores y sus fuentes están en [32_resilience4j_configuracion_productiva.txt](evidencias/semana 08/32_resilience4j_configuracion_productiva.txt).

| Circuito   | Ventana | Mínimo llamadas | Umbral fallo | Espera OPEN | Llamadas HALF_OPEN |
| ---------- | ------: | --------------: | -----------: | ----------: | -----------------: |
| coreWeb    |       5 |               5 |         50 % |         5 s |                  2 |
| coreMobile |       5 |               5 |         50 % |         5 s |                  2 |
| coreAtm    |       5 |               5 |         50 % |         5 s |                  2 |

`sliding-window-size=5` conserva una ventana de cinco llamadas; `minimum-number-of-calls=5` exige cinco resultados registrados antes de evaluar la tasa de fallos. `failure-rate-threshold=50` abre el circuito al alcanzar el 50 % de fallos. `wait-duration-in-open-state=5s` establece la espera en OPEN y `permitted-number-of-calls-in-half-open-state=2` permite dos llamadas de prueba en HALF_OPEN. Estos valores corresponden a la configuración validada en la evidencia 32.

### Validación runtime

Las evidencias [28_resilience4j_runtime.txt](evidencias/semana 08/28_resilience4j_runtime.txt) y [31_resilience4j_recuperacion_sin_dependencias.txt](evidencias/semana 08/31_resilience4j_recuperacion_sin_dependencias.txt) corresponden a una misma prueba de recuperación. Con Core operativo, Web, Mobile y ATM respondieron HTTP 200. Al detener Core, cada canal respondió HTTP 503. Tras reiniciar Core, Mobile y ATM recuperaron inmediatamente con HTTP 200, mientras que Web presentó respuestas 503 transitorias antes de volver a HTTP 200. La prueba finalizó con tres respuestas 200 consecutivas por canal y RESULTADO: PASS.

La evidencia [33_resilience4j_runtime_final.txt](evidencias/semana 08/33_resilience4j_runtime_final.txt) es la prueba runtime definitiva. Se consultó la cuenta 102 mediante los tres BFF; ATM utilizó solamente la consulta de saldo.

| Canal  | Core operativo | Core detenido: llamadas 1 a 6 | Core recuperado |
| ------ | -------------: | ----------------------------- | --------------: |
| Web    |            200 | 503, 503, 503, 503, 503, 503  |             200 |
| Mobile |            200 | 503, 503, 503, 503, 503, 503  |             200 |
| ATM    |            200 | 503, 503, 503, 503, 503, 503  |             200 |

Se detuvo exclusivamente Core con `docker compose stop core-banking-service` y se recuperó mediante `docker compose restart --no-deps core-banking-service`. Su healthcheck volvió a HTTP 200 con status UP. Después de la espera de recuperación, Web registró 503 transitorios en los intentos 1–8 y respondió 200 en los intentos 9, 10 y 11. Mobile y ATM obtuvieron 200 en sus tres primeros intentos. La prueba terminó con tres respuestas 200 consecutivas por canal y RESULTADO: PASS.

En las comprobaciones 28/31 y 33, `batch-init` mantuvo el mismo identificador, estado Exited (0) y fecha de arranque; no se reejecutó Batch. No se realizaron retiros durante estas pruebas y los 50 saldos permanecieron idénticos antes y después; la cuenta 102 mantuvo 200.00.

La evidencia 23 registró nueve tests JUnit PASS, tres por canal: CLOSED, OPEN, HALF_OPEN, recuperación a CLOSED, fallback y short-circuit. Los estados internos se comprobaron directamente mediante CircuitBreakerRegistry; las pruebas runtime registraron el comportamiento observable HTTP 503 → 200 con la configuración productiva, sin deducir OPEN únicamente de un 503.

## Spring Batch

La evidencia 24 registra la ejecución específica de DailyTransactionsJobIntegrationTest, MonthlyInterestJobIntegrationTest y AnnualMovementsStepIntegrationTest: 3 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS y RESULTADO: PASS. Los tres también pasaron dentro de la suite completa de la evidencia 01.

batch-init ejecutó dailyTransactionsJob, monthlyInterestJob y annualStatementsJob después del reset; los tres Jobs finalizaron COMPLETED y el inicializador registró BATCH INIT COMPLETED. La evidencia 24 muestra los logs y el estado de los Jobs. Batch no se reejecutó durante las pruebas de recuperación Resilience4j.

## Verificación final de la colección

[34_verificacion_final_semana_08.txt](evidencias/semana%2008/34_verificacion_final_semana_08.txt) contiene el resumen definitivo de la reproducción iniciada en RUN_STARTED, indicado en el índice 00. El cierre comprobó 35 evidencias nuevas y no vacías, todas posteriores a RUN_STARTED, incluido el índice y los TXT 01–34.

| Comprobación                            | Resultado de la ejecución                    |
| --------------------------------------- | -------------------------------------------- |
| Maven, evidencia 01                     | 92/92 PASS; BUILD SUCCESS                    |
| Idempotencia, evidencia 22              | 1/1 PASS; BUILD SUCCESS                      |
| Resilience4j JUnit, evidencia 23        | 9/9 PASS; BUILD SUCCESS                      |
| Spring Batch, evidencia 24              | 3/3 PASS; BUILD SUCCESS; tres Jobs COMPLETED |
| OAuth2, JWT y seguridad HTTP            | PASS                                         |
| Retiro ATM y Kafka                      | PASS                                         |
| Docker, Compose, Eureka y Config Server | PASS                                         |
| Resilience4j, evidencias 28/31          | PASS; Batch y balances sin cambios           |
| Resilience4j, evidencia 33              | PASS; seis 503 por canal y recuperación 200  |
| Cierre de la colección                  | RESULTADO FINAL: PASS                        |

## Ejecución

Ejecutar los pasos 00–33 en orden, preferentemente en **una misma terminal Bash/WSL**. Se mantienen `EVID`, `WEB_CLIENT_SECRET`, `MOBILE_CLIENT_SECRET`, `ATM_CLIENT_SECRET`, `WEB_TOKEN`, `MOBILE_TOKEN`, `ATM_TOKEN`, `ACCOUNT_ID` y `TRANSACTION_ID`. Se necesitan Java 17, Maven, Docker Compose, curl y Python 3.

Los pasos siguientes permiten reproducir manualmente la colección completa de evidencias de Semana 08. El Paso 00 respalda la colección existente y posteriormente limpia la carpeta activa antes de iniciar una nueva generación. Se crean desde cero el índice 00 y los TXT 01–34 con los comandos de esta nueva secuencia. El respaldo solo se compara antes de la limpieza y no se utiliza para reconstruir ni completar evidencias. Si un comando falla, detener la secuencia antes de pasar al siguiente paso. No iniciar dos reproducciones simultáneas.

La reproducción busca resultados **funcionalmente equivalentes**. Pueden variar timestamps, `transactionId`, `eventId`, identificadores de contenedores y consumidores, offsets Kafka, tiempos de arranque, cuenta y saldo seleccionados y orden de logs. Se mantienen los criterios HTTP, de procesamiento, configuración y conservación del estado.

### Paso 00 - Preparar entorno y variables

Definir las rutas y funciones auxiliares, crear y verificar un respaldo completo fuera de la carpeta activa, vaciar exclusivamente su contenido y generar el índice 00. No elimina la carpeta. El respaldo queda disponible solo para recuperación. La preparación no ejecuta todavía el reset de Docker.

**Advertencia para el Paso 03:** `docker compose down -v --remove-orphans` es **destructivo** para los volúmenes locales de este Compose: elimina los datos PostgreSQL y cualquier persistencia Compose asociada. Utilizarlo solamente cuando se desea comenzar desde cero. No ejecutar de nuevo ese reset durante los pasos de recuperación.

~~~bash
cd "/home/francisco/proyecto/BACKEND III/SEMANA 8/Exp3_S8_Francisco_Henriquez/proyecto"

EVID="/home/francisco/proyecto/BACKEND III/SEMANA 8/Exp3_S8_Francisco_Henriquez/evidencias/semana 08"
mkdir -p "$EVID"
set -o pipefail
set +x

RUN_STARTED=$(date -Iseconds)
EXPECTED_EVIDENCES=(
    '00_indice_evidencias.md'
    '01_maven_test.txt'
    '02_docker_compose_build.txt'
    '03_docker_compose_ps.txt'
    '04_healthchecks.txt'
    '05_oauth2_token_web.txt'
    '06_oauth2_token_mobile.txt'
    '07_oauth2_token_atm.txt'
    '08_oauth2_jwt_claims.txt'
    '09_web_sin_token_401.txt'
    '10_web_scope_correcto.txt'
    '11_web_scope_incorrecto_403.txt'
    '12_web_token_invalido_401.txt'
    '13_mobile_autorizado.txt'
    '14_atm_saldo_autorizado.txt'
    '15_core_sin_token_401.txt'
    '16_core_token_valido.txt'
    '17_atm_retiro_pending.txt'
    '18_atm_retiro_resultado_final.txt'
    '19_kafka_topics_particiones.txt'
    '20_kafka_consumer_group.txt'
    '21_kafka_flujo_retiro.txt'
    '22_kafka_idempotencia_test.txt'
    '23_resilience4j_tests.txt'
    '24_batch_regresion.txt'
    '25_docker_images.txt'
    '26_eureka_servicios.txt'
    '27_config_server.txt'
    '28_resilience4j_runtime.txt'
    '29_verificacion_documental_alcance.txt'
    '30_docker_reset_arranque.txt'
    '31_resilience4j_recuperacion_sin_dependencias.txt'
    '32_resilience4j_configuracion_productiva.txt'
    '33_resilience4j_runtime_final.txt'
    '34_verificacion_final_semana_08.txt'
)
BACKUP="${EVID%/*}/respaldo-semana-08-$(date +%Y%m%d-%H%M%S)"

prepare_evidence_collection() (
    set -e
    if [ "$EVID" != "/home/francisco/proyecto/BACKEND III/SEMANA 8/Exp3_S8_Francisco_Henriquez/evidencias/semana 08" ] \
        || [ -L "$EVID" ]; then
        echo 'ERROR: EVID vacía, distinta de la ruta autorizada o enlace simbólico; no limpiar.' >&2
        exit 1
    fi
    python3 - "$EVID" "$BACKUP" <<'PY'
import pathlib,sys
active=pathlib.Path(sys.argv[1]); backup=pathlib.Path(sys.argv[2])
expected=pathlib.Path("/home/francisco/proyecto/BACKEND III/SEMANA 8/Exp3_S8_Francisco_Henriquez/evidencias/semana 08")
assert active.resolve() == expected and active.is_dir() and not active.is_symlink()
assert backup.parent.resolve() == expected.parent and backup.name.startswith("respaldo-semana-08-")
assert not backup.exists() and not backup.is_symlink(), "El respaldo ya existe; elegir otro timestamp"
PY
    cp -a -- "$EVID" "$BACKUP"
    python3 - "$EVID" "$BACKUP" <<'PY'
import hashlib,pathlib,sys
def manifest(base):
    entries={}
    for path in sorted(base.rglob("*")):
        name=str(path.relative_to(base))
        if path.is_symlink():
            entries[name]=("symlink",str(path.readlink()))
        elif path.is_dir():
            entries[name]=("directory",)
        elif path.is_file():
            digest=hashlib.sha256()
            with path.open("rb") as stream:
                for chunk in iter(lambda:stream.read(1024*1024),b""):
                    digest.update(chunk)
            entries[name]=("file",digest.hexdigest())
        else:
            raise ValueError("Tipo de archivo no admitido: "+name)
    return entries
assert manifest(pathlib.Path(sys.argv[1])) == manifest(pathlib.Path(sys.argv[2])), "Respaldo incompleto"
print("Respaldo completo verificado:",sys.argv[2])
PY
    # La ruta exacta y el respaldo se han verificado; no seguir enlaces.
    find "$EVID" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
    python3 - "$EVID" <<'PY'
import pathlib,sys
assert not any(pathlib.Path(sys.argv[1]).iterdir()), "La carpeta activa no quedó vacía"
PY
    python3 - "$RUN_STARTED" "${EXPECTED_EVIDENCES[@]}" <<'PY' | tee "$EVID/00_indice_evidencias.md"
import sys
started=sys.argv[1]; names=sys.argv[2:]
descriptions=[
    "Suite Maven completa: 92 tests y BUILD SUCCESS",
    "Construcción de las ocho imágenes propias",
    "Estado de servicios Compose e inicializador Batch",
    "Healthchecks HTTP 200 y UP",
    "Access Token OAuth2 Web: metadatos seguros",
    "Access Token OAuth2 Mobile: metadatos seguros",
    "Access Token OAuth2 ATM: metadatos seguros",
    "Claims JWT públicos, issuer y vigencia",
    "Web sin token: HTTP 401",
    "Web con scope correcto: HTTP 200",
    "Web con scope ATM: HTTP 403",
    "Web con token inválido: HTTP 401",
    "Mobile autorizado: HTTP 200",
    "Cuenta real y saldo ATM autorizado",
    "Core sin token: HTTP 401",
    "Core con token válido: HTTP 200",
    "Retiro ATM aceptado: HTTP 202 y PENDING",
    "Estado terminal del retiro y comprobación de saldo",
    "Topics Kafka y sus tres particiones",
    "Consumer group, miembros, offsets y lag",
    "Trazabilidad Kafka de la transacción actual",
    "Test de idempotencia del evento ya procesado",
    "Nueve tests Resilience4j: estados, fallback y recuperación",
    "Tres tests de integración y Jobs Spring Batch",
    "Ocho imágenes propias del proyecto",
    "Cuatro servicios registrados UP en Eureka",
    "Configuración centralizada sin secretos",
    "Runtime Resilience4j seguro: captura compartida con 31",
    "Inspección documental, migración e inventario",
    "Reset único, arranque y espera del stack",
    "Recuperación de Core sin dependencias ni reejecución Batch",
    "Parámetros productivos Resilience4j desde Config Server",
    "Runtime definitivo: seis 503 por canal y recuperación 200",
    "Verificación final de las 35 evidencias y criterios funcionales"
]
assert len(names) == 35 and names[0] == "00_indice_evidencias.md"
assert len(descriptions) == 34
print("# Índice de evidencias - Semana 08")
print()
print("Inicio de la reproducción:",started)
print()
print("Procedencia: colección regenerada mediante la secuencia manual de Semana 08.")
print("Este índice se crea al inicio; los TXT se generan en los pasos posteriores.")
print("34_verificacion_final_semana_08.txt comprueba el cierre de la colección completa.")
print("El respaldo externo es solo recuperación; no forma parte de esta entrega.")
print()
print("| Evidencia | Descripción |")
print("| --- | --- |")
for name,description in zip(names[1:],descriptions):
    print("| "+chr(96)+name+chr(96)+" | "+description+" |")
PY
)
prepare_evidence_collection

unset WEB_TOKEN MOBILE_TOKEN ATM_TOKEN ACCOUNT_ID TRANSACTION_ID
unset WEB_CLIENT_SECRET MOBILE_CLIENT_SECRET ATM_CLIENT_SECRET

begin_evidence() {
    printf 'EVIDENCIA: %s\nFECHA: %s\nOBJETIVO: %s\nCOMANDO: %s\n\nRESULTADO REAL:\n' \
        "$1" "$(date -Iseconds)" "$2" "$3"
}

wait_url() {
    local url="$1" name="$2" attempt
    for attempt in {1..90}; do
        if curl -fsS --max-time 5 "$url" | python3 -c \
            'import json,sys; assert json.load(sys.stdin)["status"] == "UP"' 2>/dev/null; then
            printf '%s disponible: HTTP 200, status UP.\n' "$name"
            return 0
        fi
        sleep 2
    done
    printf 'ERROR: timeout esperando %s.\n' "$name" >&2
    return 1
}

http_request() {
    local method="$1" url="$2" token="${3:-}" data="${4:-}" response
    if [ "$method" = POST ]; then
        response=$(curl -sS --max-time 15 -X POST \
            -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
            --data "$data" -w '\n%{http_code}' "$url") || return 1
    elif [ -n "$token" ]; then
        response=$(curl -sS --max-time 15 \
            -H "Authorization: Bearer $token" -w '\n%{http_code}' "$url") || return 1
    else
        response=$(curl -sS --max-time 15 -w '\n%{http_code}' "$url") || return 1
    fi
    HTTP_STATUS=$(printf '%s\n' "$response" | tail -n 1)
    HTTP_BODY=$(printf '%s\n' "$response" | sed '$d')
}

record_get() {
    local file="$1" expected="$2" url="$3" token="${4:-}" attempt limit=1
    begin_evidence "$file" "Comprobar HTTP $expected" "GET $url; Bearer en memoria cuando corresponde" \
        | tee "$EVID/$file"
    if [ "$expected" = 200 ]; then limit=60; fi
    for ((attempt=1; attempt<=limit; attempt++)); do
        http_request GET "$url" "$token" || return 1
        printf 'Intento: %s\nHTTP status: %s\n%s\n' "$attempt" "$HTTP_STATUS" "$HTTP_BODY" \
            | tee -a "$EVID/$file"
        if [ "$HTTP_STATUS" = "$expected" ]; then
            echo 'RESULTADO: PASS' | tee -a "$EVID/$file"
            return 0
        fi
        if [ "$expected" != 200 ] || [ "$HTTP_STATUS" != 503 ]; then break; fi
        sleep 2
    done
    echo 'RESULTADO: FAIL' | tee -a "$EVID/$file"
    return 1
}

request_token() {
    local client="$1" scope="$2" secret="$3" variable="$4" file="$5"
    local response code body token title
    response=$(curl -sS --max-time 15 --user "$client:$secret" \
        --data-urlencode 'grant_type=client_credentials' \
        --data-urlencode "scope=$scope" \
        -w '\n%{http_code}' http://localhost:9000/oauth2/token) || return 1
    code=$(printf '%s\n' "$response" | tail -n 1)
    body=$(printf '%s\n' "$response" | sed '$d')
    case "$scope" in
        web) title='Token OAuth2 Web' ;;
        mobile) title='Token OAuth2 Mobile' ;;
        atm) title='Token OAuth2 ATM' ;;
        *) title="Token OAuth2 $scope" ;;
    esac
    begin_evidence "$title" \
        "Obtener Access Token OAuth2 para $client sin almacenar el JWT completo" \
        "POST http://localhost:9000/oauth2/token - client=$client - grant_type=client_credentials - scope=$scope" \
        | tee "$EVID/$file"
    printf 'HTTP status: %s\n' "$code" | tee -a "$EVID/$file"
    if [ "$code" != 200 ]; then
        echo 'ERROR: solicitud OAuth2 fallida; no se guarda el body sensible.' >&2
        return 1
    fi
    token=$(printf '%s' "$body" | python3 -c '
import json,sys
d=json.load(sys.stdin)
assert d["token_type"].lower() == "bearer"
assert d["scope"].split() == [sys.argv[1]]
assert 0 < int(d["expires_in"]) <= 1800
assert len(d["access_token"].split(".")) == 3
print(d["access_token"])
' "$scope") || return 1
    printf -v "$variable" '%s' "$token"
    # Se imprimen solo metadatos; la función se llama sin pipe exterior.
    printf '%s' "$body" | python3 -c '
import json,sys
d=json.load(sys.stdin)
print(json.dumps({k:d[k] for k in ("token_type","scope","expires_in")},ensure_ascii=False))
print("Access Token mantenido exclusivamente en memoria.")
' | tee -a "$EVID/$file"
}

report_tests() {
    python3 -c '
import pathlib,sys,xml.etree.ElementTree as ET
expected=int(sys.argv[1]); names=set(sys.argv[2:]); totals=[0,0,0,0]; found=set()
for path in sorted(pathlib.Path(".").glob("*/target/surefire-reports/TEST-*.xml")):
    root=ET.parse(path).getroot()
    short=root.attrib["name"].split(".")[-1]
    if names and short not in names:
        continue
    found.add(short)
    counts=[int(root.attrib.get(k,0)) for k in ("tests","failures","errors","skipped")]
    totals=[a+b for a,b in zip(totals,counts)]
    print(root.attrib["name"], dict(zip(("tests","failures","errors","skipped"),counts)))
    for case in root.findall("testcase"):
        passed=all(case.find(k) is None for k in ("failure","error","skipped"))
        print("  "+case.attrib["name"]+": "+("PASS" if passed else "FAIL"))
for key,value in zip(("Tests run","Failures","Errors","Skipped"),totals):
    print(f"{key}: {value}")
assert not names or found == names, "Faltan reportes solicitados"
assert totals == [expected,0,0,0], totals
print("RESULTADO: PASS")
' "$@"
}


wait_eureka() {
    local attempt
    for attempt in {1..60}; do
        if curl -fsS --max-time 10 -H 'Accept: application/xml' \
            http://localhost:8761/eureka/apps | python3 -c '
import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
expected={"CORE-BANKING-SERVICE","BFF-WEB-SERVICE","BFF-MOBILE-SERVICE","BFF-ATM-SERVICE"}
up={a.findtext("name") for a in root.findall("application")
    if any(i.findtext("status")=="UP" for i in a.findall("instance"))}
assert expected <= up
for name in sorted(expected):
    print(name, "UP")
' 2>/dev/null; then
            return 0
        fi
        sleep 2
    done
    echo 'ERROR: timeout de registro Eureka.' >&2
    return 1
}

wait_compose_ready() {
    local attempt id state service infra_ready=0 batch_ready=0
    for attempt in {1..90}; do
        if docker compose exec -T postgres pg_isready -U banco_user -d banco_xyz \
            && docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh \
                --bootstrap-server localhost:9092 --list >/dev/null; then
            echo 'PostgreSQL y Kafka disponibles mediante sus herramientas.'
            infra_ready=1
            break
        fi
        sleep 2
    done
    test "$infra_ready" -eq 1 || { echo 'ERROR: timeout PostgreSQL/Kafka.' >&2; return 1; }
    for attempt in {1..180}; do
        id=$(docker compose ps -a -q batch-init)
        if [ -n "$id" ]; then
            state=$(docker inspect --format '{{.State.Status}} {{.State.ExitCode}}' "$id")
            if [ "$state" = 'exited 0' ]; then
                echo 'batch-init: Exited (0).'
                batch_ready=1
                break
            fi
            if [[ "$state" == exited* ]]; then
                echo "ERROR: batch-init: $state" >&2
                return 1
            fi
        fi
        sleep 2
    done
    test "$batch_ready" -eq 1 || { echo 'ERROR: timeout batch-init.' >&2; return 1; }
    wait_url http://localhost:8888/actuator/health 'Config Server' || return 1
    wait_url http://localhost:8761/actuator/health 'Eureka' || return 1
    wait_url http://localhost:9000/actuator/health 'Authorization Server' || return 1
    wait_url http://localhost:8081/actuator/health 'Core' || return 1
    wait_url http://localhost:8082/actuator/health 'Web' || return 1
    wait_url http://localhost:8083/actuator/health 'Mobile' || return 1
    wait_url http://localhost:8084/actuator/health 'ATM' || return 1
    wait_eureka || return 1
    for service in postgres kafka config-server eureka-server authorization-server \
        core-banking-service bff-web-service bff-mobile-service bff-atm-service; do
        for attempt in {1..60}; do
            id=$(docker compose ps -a -q "$service") || return 1
            state=$(docker inspect --format '{{.State.Status}} {{.State.Health.Status}}' "$id") || return 1
            if [ "$state" = 'running healthy' ]; then break; fi
            sleep 2
        done
        printf '%s: %s\n' "$service" "$state"
        test "$state" = 'running healthy' || return 1
    done
}
~~~

Resultado esperado: respaldo completo verificado fuera de la carpeta activa, eliminación de su contenido anterior, nuevo índice 00 y funciones disponibles. Evidencia: `00_indice_evidencias.md`, con fecha/hora de RUN_STARTED y listado ordenado de 01–34.

**Ingreso de secretos: ejecutar las tres líneas siguientes una a una.** Los caracteres no serán visibles. **No pegar estos bloques juntos ni con otros comandos:** `read -s` puede consumir líneas posteriores del búfer. Los secretos no se escriben en el índice ni en evidencias.

Web:

~~~bash
read -r -s -p 'Secreto elegido para Web: ' WEB_CLIENT_SECRET; printf '\n'
~~~

Mobile, después de completar Web:

~~~bash
read -r -s -p 'Secreto elegido para Mobile: ' MOBILE_CLIENT_SECRET; printf '\n'
~~~

ATM, después de completar Mobile:

~~~bash
read -r -s -p 'Secreto elegido para ATM: ' ATM_CLIENT_SECRET; printf '\n'
~~~

Una vez completadas las tres entradas, comprobar que no están vacías y exportarlas para Authorization Server. Este bloque se copia por separado y no muestra sus valores:

~~~bash
test -n "$WEB_CLIENT_SECRET" && test -n "$MOBILE_CLIENT_SECRET" && test -n "$ATM_CLIENT_SECRET" && \
    export OAUTH2_WEB_SECRET="$WEB_CLIENT_SECRET" \
           OAUTH2_MOBILE_SECRET="$MOBILE_CLIENT_SECRET" \
           OAUTH2_ATM_SECRET="$ATM_CLIENT_SECRET"
~~~

Resultado esperado: WEB_CLIENT_SECRET, MOBILE_CLIENT_SECRET y ATM_CLIENT_SECRET en memoria, con sus equivalentes OAUTH2_WEB_SECRET, OAUTH2_MOBILE_SECRET y OAUTH2_ATM_SECRET exportados. 
Evidencia: ninguna adicional; no imprimir secretos.

**Solo si Docker Desktop/WSL presenta un error relacionado con `docker-credential-desktop.exe`**, antes del Paso 02 utilizar esta configuración temporal. No modifica la configuración global:

~~~bash
mkdir -p /tmp/docker-config-s8
printf '{}\n' > /tmp/docker-config-s8/config.json
export DOCKER_CONFIG=/tmp/docker-config-s8
docker info >/dev/null && echo "Docker OK"
~~~

Resultado esperado del bloque opcional: `Docker OK`. 
Evidencia: ninguna.

### Paso 01 - Tests Maven

Ejecutar la suite completa desde proyecto/, sin omitir pruebas. El filtro conserva el resumen Maven y evita volcar logs DEBUG de las pruebas de autenticación. El resumen agregado se calcula desde los reportes Surefire recién producidos.

~~~bash
(
    set -e
    begin_evidence 'Tests Maven' 'Validar la suite completa' 'mvn test'
    mvn test 2>&1 \
        | sed -E 's/\x1B\[[0-9;]*[mK]//g' \
        | grep -E '^\[INFO\]|^\[ERROR\]|^\[WARNING\]|^Tests run:'
    report_tests 92
) 2>&1 | tee "$EVID/01_maven_test.txt"
~~~

Resultado esperado: Tests run: 92, Failures: 0, Errors: 0, Skipped: 0 y `BUILD SUCCESS`. 
Evidencia: `01_maven_test.txt`.

### Paso 02 - Docker build

Construir las ocho imágenes propias declaradas por el Compose actual. Los secretos definidos en el Paso 00 se utilizarán al arrancar Authorization Server.

~~~bash
(
    set -e
    begin_evidence 'Docker build' 'Construir ocho imágenes propias' 'docker compose build'
    docker compose build
    echo 'RESULTADO: PASS'
) 2>&1 | tee "$EVID/02_docker_compose_build.txt"
~~~

Resultado esperado: build exitoso de authorization-server, config-server, eureka-server, core-banking-service, los tres BFF y batch-init. 
Evidencia: `02_docker_compose_build.txt`.

### Paso 03 - Reset, arranque y espera de servicios

Este es el **único reset destructivo** de la secuencia. El respaldo del Paso 00 contiene la colección anterior completa. La carpeta activa ya tiene únicamente el nuevo índice 00 y los TXT generados por esta secuencia; no restaurar archivos desde el respaldo. Compose inicia las dependencias y el inicializador; esperar su finalización y los servicios HTTP antes de continuar.

~~~bash
(
    set -e
    begin_evidence 'Reset y arranque' 'Reproducir desde cero' \
        'docker compose down -v --remove-orphans; docker compose up -d'
    docker compose down -v --remove-orphans
    docker compose up -d
    wait_compose_ready
    docker compose ps -a
    echo 'RESULTADO: PASS'
) 2>&1 | tee "$EVID/30_docker_reset_arranque.txt"
~~~

Resultado esperado: PostgreSQL y Kafka disponibles; Config :8888, Eureka :8761, Authorization Server :9000, Core :8081, Web :8082, Mobile :8083 y ATM :8084 disponibles; batch-init `Exited (0)`. 
Evidencia: `30_docker_reset_arranque.txt`.

### Paso 04 - Estado Docker Compose

Distinguir los nueve servicios permanentes del inicializador que ya terminó.

~~~bash
{
    begin_evidence 'Estado Compose' 'Inspeccionar servicios e inicializador' \
        'docker compose ps; docker compose ps -a'
    docker compose ps
    docker compose ps -a
} 2>&1 | tee "$EVID/03_docker_compose_ps.txt"
~~~

Resultado esperado: nueve servicios permanentes healthy y batch-init `Exited (0)`. 
Evidencia: `03_docker_compose_ps.txt`.

### Paso 05 - Healthchecks HTTP

Registrar código y body de los siete endpoints públicos de health. El bloque falla si algún servicio no responde 200/UP.

~~~bash
(
    set -e
    begin_evidence 'Healthchecks' 'Comprobar HTTP 200 y UP' 'GET /actuator/health en siete servicios'
    for port in 8888 8761 9000 8081 8082 8083 8084; do
        http_request GET "http://localhost:$port/actuator/health"
        printf 'Puerto: %s\nHTTP status: %s\n%s\n' "$port" "$HTTP_STATUS" "$HTTP_BODY"
        test "$HTTP_STATUS" = 200
        printf '%s' "$HTTP_BODY" | python3 -c \
            'import json,sys; assert json.load(sys.stdin)["status"] == "UP"'
    done
    echo 'RESULTADO: PASS'
) 2>&1 | tee "$EVID/04_healthchecks.txt"
~~~

Resultado esperado: siete respuestas HTTP 200 y status UP. 
Evidencia: `04_healthchecks.txt`.

### Paso 06 - Token OAuth2 Web

Solicitar un Access Token real con client_credentials y scope web. La función mantiene el JWT en WEB_TOKEN.

~~~bash
request_token web-client web "$WEB_CLIENT_SECRET" WEB_TOKEN '05_oauth2_token_web.txt'
~~~

Resultado esperado: HTTP 200, token_type Bearer, scope web y expires_in positivo, normalmente 1799; WEB_TOKEN disponible en memoria. 
Evidencia: `05_oauth2_token_web.txt`; contiene solamente HTTP status, token_type, scope y expires_in.

### Paso 07 - Token OAuth2 Mobile

Utilizar el cliente y secreto del canal Mobile.

~~~bash
request_token mobile-client mobile "$MOBILE_CLIENT_SECRET" MOBILE_TOKEN '06_oauth2_token_mobile.txt'
~~~

Resultado esperado: HTTP 200, Bearer, scope mobile y vigencia restante de aproximadamente 30 minutos. 
Evidencia: `06_oauth2_token_mobile.txt`; MOBILE_TOKEN completo queda solo en memoria.

### Paso 08 - Token OAuth2 ATM

Obtener el token que se utilizará en saldo, Core y retiro.

~~~bash
request_token atm-client atm "$ATM_CLIENT_SECRET" ATM_TOKEN '07_oauth2_token_atm.txt'
~~~

Resultado esperado: HTTP 200, Bearer y scope atm. Evidencia: `07_oauth2_token_atm.txt`; ATM_TOKEN completo queda solo en memoria.

Los Access Tokens duran 30 minutos. Si expiran durante una reproducción larga, **repetir exclusivamente los pasos 06, 07 y 08** para actualizar las tres variables; no reiniciar el stack ni repetir retiros. Después de reiniciar Authorization Server también deben obtenerse tokens nuevos, porque su clave RSA cambia. Mantener desactivado `set -x` y no imprimir variables, encabezados Authorization ni respuestas completas del endpoint de tokens.

### Paso 09 - Claims JWT en memoria

Decodificar header y payload de los tres tokens y comprobar los campos mediante aserciones locales. Esto comprueba contenido y vigencia; la decodificación Base64 no verifica criptográficamente la firma. Los Resource Servers validan firma RSA, issuer y vigencia en las consultas autorizadas posteriores.

~~~bash
(
    set -e
    begin_evidence 'Claims JWT' 'Comprobar claims públicos sin publicar JWT' 'Decodificación local en Python'
    for channel in WEB MOBILE ATM; do
        variable="${channel}_TOKEN"
        printf '%s' "${!variable}" | python3 -c '
import base64,json,sys,time
channel=sys.argv[1].lower()
parts=sys.stdin.read().strip().split(".")
assert len(parts) == 3
decode=lambda value:json.loads(base64.urlsafe_b64decode(value+"="*((-len(value))%4)))
header,payload=decode(parts[0]),decode(parts[1])
scope=payload["scope"]
scopes=scope.split() if isinstance(scope,str) else scope
assert header["alg"] == "RS256"
assert payload["iss"] == "http://authorization-server:9000"
assert payload["sub"] == channel+"-client"
assert scopes == [channel]
assert payload["exp"]-payload["iat"] == 1800
assert payload["iat"] <= time.time()+60 < payload["exp"]
print(json.dumps({"alg":header["alg"],**{k:payload[k] for k in ("iss","sub","scope","iat","exp")}},ensure_ascii=False))
' "$channel"
    done
    echo 'RESULTADO: PASS'
) 2>&1 | tee "$EVID/08_oauth2_jwt_claims.txt"
~~~

Resultado esperado: alg RS256, sub correspondiente, scope correcto y exp − iat = 1800. Durante Docker, iss debe ser `http://authorization-server:9000`. Local/IDE conserva `http://localhost:9000`; no cambiar esa configuración. 
Evidencia: `08_oauth2_jwt_claims.txt`.

### Paso 10 - Seleccionar cuenta real y consultar saldo ATM

La cuenta se selecciona **antes de todas las consultas que dependen de ACCOUNT_ID**. Elegir una cuenta de la consulta real con saldo suficiente para un retiro de 10000 (Se propone 102). Validar la entrada numérica antes de interpolarla en SQL.

~~~bash
{
    begin_evidence 'Saldo ATM' 'Seleccionar cuenta real con saldo suficiente' \
        'SELECT cuenta_id,saldo_final FROM intereses_mensuales; GET saldo ATM'
    docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U banco_user -d banco_xyz -c \
        "SELECT cuenta_id, saldo_final FROM intereses_mensuales WHERE saldo_final >= 10000 ORDER BY cuenta_id LIMIT 5;"
} 2>&1 | tee "$EVID/14_atm_saldo_autorizado.txt"

select_account() {
    read -r -p 'Cuenta de la consulta anterior: ' ACCOUNT_ID
    [[ "$ACCOUNT_ID" =~ ^[0-9]+$ ]] || { echo 'ERROR: cuenta no numérica.' >&2; return 1; }
    BALANCE_BEFORE=$(docker compose exec -T postgres \
        psql -v ON_ERROR_STOP=1 -At -U banco_user -d banco_xyz -c \
        "SELECT saldo_final FROM intereses_mensuales WHERE cuenta_id = $ACCOUNT_ID;") || return 1
    printf '%s' "$BALANCE_BEFORE" | python3 -c \
        'from decimal import Decimal; import sys; assert Decimal(sys.stdin.read().strip()) >= Decimal("10000")' \
        || return 1
    local attempt
    for attempt in {1..60}; do
        http_request GET "http://localhost:8084/api/bff/atm/accounts/$ACCOUNT_ID/balance" "$ATM_TOKEN" || return 1
        printf 'Espera ATM intento=%s HTTP=%s\n' "$attempt" "$HTTP_STATUS" \
            | tee -a "$EVID/14_atm_saldo_autorizado.txt"
        if [ "$HTTP_STATUS" = 200 ]; then break; fi
        test "$HTTP_STATUS" = 503 || return 1
        sleep 2
    done
    printf 'Cuenta: %s\nSaldo SQL previo: %s\nHTTP status: %s\n%s\n' \
        "$ACCOUNT_ID" "$BALANCE_BEFORE" "$HTTP_STATUS" "$HTTP_BODY" \
        | tee -a "$EVID/14_atm_saldo_autorizado.txt"
    test "$HTTP_STATUS" = 200 || return 1
    printf '%s' "$HTTP_BODY" | python3 -c '
from decimal import Decimal
import json,sys
d=json.load(sys.stdin)
assert int(d["accountId"]) == int(sys.argv[1])
assert Decimal(str(d["balance"])) == Decimal(sys.argv[2])
' "$ACCOUNT_ID" "$BALANCE_BEFORE" || return 1
    echo 'RESULTADO: PASS' | tee -a "$EVID/14_atm_saldo_autorizado.txt"
}
select_account
~~~

Resultado esperado: ACCOUNT_ID de una cuenta existente, saldo SQL ≥ 10000 y ATM HTTP 200 con el mismo saldo. 
Evidencia: `14_atm_saldo_autorizado.txt`.

### Paso 11 - Web sin token

Comprobar el endpoint real del canal Web sin Authorization.

~~~bash
record_get '09_web_sin_token_401.txt' 401 \
    "http://localhost:8082/api/bff/web/accounts/$ACCOUNT_ID"
~~~

Resultado esperado: HTTP 401. 
Evidencia: `09_web_sin_token_401.txt`.

### Paso 12 - Web con scope correcto

Consultar la misma cuenta utilizando WEB_TOKEN.

~~~bash
record_get '10_web_scope_correcto.txt' 200 \
    "http://localhost:8082/api/bff/web/accounts/$ACCOUNT_ID" "$WEB_TOKEN"
~~~

Resultado esperado: HTTP 200 y datos de la cuenta. 
Evidencia: `10_web_scope_correcto.txt`.

### Paso 13 - Web con scope incorrecto

Enviar un token válido del canal ATM al endpoint protegido con SCOPE_web.

~~~bash
record_get '11_web_scope_incorrecto_403.txt' 403 \
    "http://localhost:8082/api/bff/web/accounts/$ACCOUNT_ID" "$ATM_TOKEN"
~~~

Resultado esperado: HTTP 403. 
Evidencia: `11_web_scope_incorrecto_403.txt`.

### Paso 14 - Web con token inválido

Utilizar una cadena inválida explícita, sin publicar un JWT real.

~~~bash
record_get '12_web_token_invalido_401.txt' 401 \
    "http://localhost:8082/api/bff/web/accounts/$ACCOUNT_ID" 'invalid-token'
~~~

Resultado esperado: HTTP 401. 
Evidencia: `12_web_token_invalido_401.txt`.

### Paso 15 - Mobile autorizado

Consultar la cuenta con el scope Mobile.

~~~bash
record_get '13_mobile_autorizado.txt' 200 \
    "http://localhost:8083/api/bff/mobile/accounts/$ACCOUNT_ID" "$MOBILE_TOKEN"
~~~

Resultado esperado: HTTP 200. 
Evidencia: `13_mobile_autorizado.txt`.

### Paso 16 - Core sin token

Comprobar directamente la protección de Core Banking.

~~~bash
record_get '15_core_sin_token_401.txt' 401 \
    "http://localhost:8081/api/core/accounts/$ACCOUNT_ID/balance"
~~~

Resultado esperado: HTTP 401. 
Evidencia: `15_core_sin_token_401.txt`.

### Paso 17 - Core con token válido

Core exige un Access Token válido; la consulta utiliza ATM_TOKEN.

~~~bash
record_get '16_core_token_valido.txt' 200 \
    "http://localhost:8081/api/core/accounts/$ACCOUNT_ID/balance" "$ATM_TOKEN"
~~~

Resultado esperado: HTTP 200. 
Evidencia: `16_core_token_valido.txt`.

### Paso 18 - Publicar retiro ATM y conservar transactionId

La regla real de AsyncWithdrawalService exige monto positivo y múltiplo de 10000. En una reproducción sin operaciones concurrentes, el saldo verificado permite retirar 10000. **Este paso modifica el saldo cuando Core procesa el retiro; ejecutarlo una sola vez.** HTTP 202 confirma aceptación/publicación tras el ACK Kafka, no el resultado bancario final.

~~~bash
submit_withdrawal() {
    http_request POST "http://localhost:8084/api/bff/atm/accounts/$ACCOUNT_ID/withdraw" \
        "$ATM_TOKEN" '{"amount":10000}' || return 1
    {
        begin_evidence 'Retiro ATM' 'Publicar monto válido por Kafka' \
            'POST /api/bff/atm/accounts/$ACCOUNT_ID/withdraw; amount=10000'
        printf 'Cuenta: %s\nHTTP status: %s\n%s\n' "$ACCOUNT_ID" "$HTTP_STATUS" "$HTTP_BODY"
    } | tee "$EVID/17_atm_retiro_pending.txt"
    test "$HTTP_STATUS" = 202 || return 1
    TRANSACTION_ID=$(printf '%s' "$HTTP_BODY" | python3 -c '
import json,sys,uuid
d=json.load(sys.stdin)
assert d["status"] == "PENDING"
print(uuid.UUID(d["transactionId"]))
') || return 1
    printf 'TRANSACTION_ID=%s\nRESULTADO: PASS\n' "$TRANSACTION_ID" \
        | tee -a "$EVID/17_atm_retiro_pending.txt"
}
submit_withdrawal
~~~

Resultado esperado: HTTP 202, PENDING y TRANSACTION_ID extraído de la respuesta real; no reutilizar IDs anteriores. 
Evidencia: `17_atm_retiro_pending.txt`.

### Paso 19 - Esperar estado final del retiro

Consultar exclusivamente TRANSACTION_ID con límite de 60 intentos. PENDING y un 503 transitorio no son estados terminales; no exigir un 503 en el primer intento. Un 401 requiere renovar el token antes de continuar el polling, sin volver a publicar el retiro.

~~~bash
poll_withdrawal() {
    local attempt
    FINAL_STATUS=''
    begin_evidence 'Resultado del retiro' 'Esperar estado terminal y verificar saldo' \
        'GET /api/bff/atm/accounts/transactions/$TRANSACTION_ID; SELECT saldo' \
        | tee "$EVID/18_atm_retiro_resultado_final.txt"
    for attempt in {1..60}; do
        http_request GET "http://localhost:8084/api/bff/atm/accounts/transactions/$TRANSACTION_ID" \
            "$ATM_TOKEN" || return 1
        printf 'Intento: %s\nHTTP status: %s\n%s\n' "$attempt" "$HTTP_STATUS" "$HTTP_BODY" \
            | tee -a "$EVID/18_atm_retiro_resultado_final.txt"
        if [ "$HTTP_STATUS" = 200 ]; then
            FINAL_STATUS=$(printf '%s' "$HTTP_BODY" | python3 -c \
                'import json,sys; print(json.load(sys.stdin)["status"])') || return 1
            if [ "$FINAL_STATUS" = COMPLETED ] || [ "$FINAL_STATUS" = REJECTED ]; then
                break
            fi
        elif [ "$HTTP_STATUS" != 503 ]; then
            echo "ERROR: HTTP inesperado $HTTP_STATUS; no republicar el retiro." >&2
            return 1
        fi
        sleep 2
    done
    if [ "$FINAL_STATUS" != COMPLETED ] && [ "$FINAL_STATUS" != REJECTED ]; then
        echo 'ERROR: timeout; conservar TRANSACTION_ID y revisar Core/Kafka.' >&2
        return 1
    fi
    BALANCE_AFTER=$(docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -At \
        -U banco_user -d banco_xyz -c \
        "SELECT saldo_final FROM intereses_mensuales WHERE cuenta_id = $ACCOUNT_ID;") || return 1
    printf 'Estado final: %s\nSaldo antes: %s\nSaldo después: %s\n' \
        "$FINAL_STATUS" "$BALANCE_BEFORE" "$BALANCE_AFTER" \
        | tee -a "$EVID/18_atm_retiro_resultado_final.txt"
    python3 -c '
from decimal import Decimal
import sys
before,after=map(Decimal,sys.argv[1:3]); state=sys.argv[3]
assert after == (before-Decimal("10000") if state=="COMPLETED" else before)
' "$BALANCE_BEFORE" "$BALANCE_AFTER" "$FINAL_STATUS" || return 1
    echo 'RESULTADO: PASS' | tee -a "$EVID/18_atm_retiro_resultado_final.txt"
}
poll_withdrawal
~~~

Resultado esperado: HTTP 200 con COMPLETED para la cuenta y monto válidos de esta secuencia, y descuento de 10000. Si termina REJECTED, registrar el motivo real y saldo sin descuento; el polling termina igualmente, pero no equivale al retiro exitoso de la evidencia original. 
Evidencia: `18_atm_retiro_resultado_final.txt`.

### Paso 20 - Topics Kafka y particiones

Consultar el broker real desde el contenedor Kafka y describir los tres topics existentes.

~~~bash
(
    set -e
    begin_evidence 'Topics Kafka' 'Comprobar topics y tres particiones' 'kafka-topics.sh --list y --describe'
    docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh \
        --bootstrap-server localhost:9092 --list
    for topic in bank.withdrawal.requested bank.withdrawal.completed bank.withdrawal.rejected; do
        docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh \
            --bootstrap-server localhost:9092 --describe --topic "$topic"
    done
) 2>&1 | tee "$EVID/19_kafka_topics_particiones.txt"
~~~

Resultado esperado: los tres topics, PartitionCount: 3 y particiones 0, 1 y 2, con replicación 1. 
Evidencia: `19_kafka_topics_particiones.txt`.

### Paso 21 - Consumer group de Core

Mostrar offsets y miembros después del procesamiento del retiro; no crear otro grupo.

~~~bash
(
    set -e
    begin_evidence 'Consumer group' 'Inspeccionar consumidores, asignaciones y lag' \
        'kafka-consumer-groups.sh --describe --group core-banking-withdrawal-group'
    docker compose exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh \
        --bootstrap-server localhost:9092 --describe --group core-banking-withdrawal-group
    docker compose exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh \
        --bootstrap-server localhost:9092 --describe \
        --group core-banking-withdrawal-group --members --verbose
) 2>&1 | tee "$EVID/20_kafka_consumer_group.txt"
~~~

Resultado esperado: topic, particiones, CONSUMER-ID, CURRENT-OFFSET, LOG-END-OFFSET y LAG; tres miembros y asignaciones de las tres particiones. Tras el procesamiento, la partición utilizada converge a lag 0. Las particiones sin mensajes pueden mostrar offset sin confirmar. 
Evidencia: `20_kafka_consumer_group.txt`.

### Paso 22 - Trazabilidad del retiro Kafka

Filtrar únicamente el transactionId de esta ejecución. El productor del resultado puede aparecer unos instantes después de que la consulta de estado muestre COMPLETED.

~~~bash
(
    set -e
    begin_evidence 'Flujo Kafka' 'Relacionar publicación, consumo y resultado' \
        'docker compose logs --no-color bff-atm-service core-banking-service; filtro TRANSACTION_ID'
    sleep 2
    docker compose logs --no-color bff-atm-service core-banking-service \
        | grep -F "$TRANSACTION_ID"
    docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U banco_user -d banco_xyz -c \
        "SELECT event_type, transaction_id, account_id, amount FROM banking_events WHERE transaction_id = '$TRANSACTION_ID' ORDER BY occurred_at;"
) 2>&1 | tee "$EVID/21_kafka_flujo_retiro.txt"
~~~

Resultado esperado: WithdrawalRequested, log [Kafka][Consumer] con partición/hilo, resultado bancario y publicación en bank.withdrawal.completed o bank.withdrawal.rejected; Event Store de la misma transacción. No volcar miles de líneas ni consumir como resultado nuevo un mensaje antiguo del topic. 
Evidencia: `21_kafka_flujo_retiro.txt`.

### Paso 23 - Test de idempotencia

Ejecutar el test unitario real, sin republicar eventos ni efectuar otro descuento en la base Compose. `surefire.failIfNoSpecifiedTests=false` permite que los módulos previos del reactor sin esa clase participen; no omite el test solicitado.

~~~bash
(
    set -e
    begin_evidence 'Idempotencia' 'Comprobar evento ya procesado' \
        'mvn -pl core-banking-service -am -Dtest=AsyncWithdrawalServiceTest -Dsurefire.failIfNoSpecifiedTests=false test'
    mvn -pl core-banking-service -am -Dtest=AsyncWithdrawalServiceTest \
        -Dsurefire.failIfNoSpecifiedTests=false test 2>&1 \
        | sed -E 's/\x1B\[[0-9;]*[mK]//g' \
        | grep -E '^\[INFO\]|^\[ERROR\]|^\[WARNING\]|^Tests run:'
    report_tests 1 AsyncWithdrawalServiceTest
) 2>&1 | tee "$EVID/22_kafka_idempotencia_test.txt"
~~~

Resultado esperado: AsyncWithdrawalServiceTest.ignoresAnEventAlreadyProcessed PASS; 1 test, cero fallos, errores y omitidos, BUILD SUCCESS. 
Evidencia: `22_kafka_idempotencia_test.txt`.

### Paso 24 - Nueve tests JUnit Resilience4j

Ejecutar exclusivamente las tres clases reales, tres tests por canal. El resumen selecciona sus reportes para no confundirlos con la suite completa conservada en 01.

~~~bash
(
    set -e
    begin_evidence 'Resilience4j JUnit' 'Comprobar estados, fallback y short-circuit' \
        'mvn -pl bff-web-service,bff-mobile-service,bff-atm-service -am -Dtest=WebResilience4jTest,MobileResilience4jTest,AtmResilience4jTest -Dsurefire.failIfNoSpecifiedTests=false test'
    mvn -pl bff-web-service,bff-mobile-service,bff-atm-service -am \
        -Dtest=WebResilience4jTest,MobileResilience4jTest,AtmResilience4jTest \
        -Dsurefire.failIfNoSpecifiedTests=false test 2>&1 \
        | sed -E 's/\x1B\[[0-9;]*[mK]//g' \
        | grep -E '^\[INFO\]|^\[ERROR\]|^\[WARNING\]|^Tests run:'
    report_tests 9 WebResilience4jTest MobileResilience4jTest AtmResilience4jTest
) 2>&1 | tee "$EVID/23_resilience4j_tests.txt"
~~~

Resultado esperado: 9 tests, 0 failures, 0 errors, 0 skipped y BUILD SUCCESS. Los métodos reales comprueban **CLOSED**, **OPEN**, **HALF_OPEN**, **CLOSED recuperado**, fallback y short-circuit mediante CircuitBreakerRegistry y métricas; el tercer intento con circuito abierto no llega a Core. Sus overrides exclusivos de test (ventana/mínimo 2 y espera 1s) no sustituyen los parámetros productivos 5/5/50/5s/2. 
Evidencia: `23_resilience4j_tests.txt`.

### Paso 25 - Regresión Spring Batch y Jobs existentes

Ejecutar los tres tests de integración reales, que utilizan su entorno de pruebas. Después inspeccionar los Jobs ya ejecutados por batch-init en el Paso 03; no relanzar el inicializador.

~~~bash
(
    set -e
    begin_evidence 'Regresión Batch' 'Relacionar tres tests con tres Jobs reales' \
        'mvn -pl core-banking-service -am -Dtest=DailyTransactionsJobIntegrationTest,MonthlyInterestJobIntegrationTest,AnnualMovementsStepIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test; SELECT y logs batch-init'
    mvn -pl core-banking-service -am \
        -Dtest=DailyTransactionsJobIntegrationTest,MonthlyInterestJobIntegrationTest,AnnualMovementsStepIntegrationTest \
        -Dsurefire.failIfNoSpecifiedTests=false test 2>&1 \
        | sed -E 's/\x1B\[[0-9;]*[mK]//g' \
        | grep -E '^\[INFO\]|^\[ERROR\]|^\[WARNING\]|^Tests run:'
    report_tests 3 DailyTransactionsJobIntegrationTest MonthlyInterestJobIntegrationTest AnnualMovementsStepIntegrationTest
    batch_logs=$(docker compose logs --no-color batch-init)
    for job in dailyTransactionsJob monthlyInterestJob annualStatementsJob; do
        printf '%s\n' "$batch_logs" | grep -E "$job.*COMPLETED"
    done
    printf '%s\n' "$batch_logs" \
        | grep -E 'dailyTransactionsJob.*COMPLETED|monthlyInterestJob.*COMPLETED|annualStatementsJob.*COMPLETED|BATCH INIT COMPLETED'
    docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U banco_user -d banco_xyz -c \
        "SELECT i.job_name, e.job_execution_id, e.status FROM batch_job_instance i JOIN batch_job_execution e USING (job_instance_id) ORDER BY e.job_execution_id;"
    echo 'RESULTADO: PASS'
) 2>&1 | tee "$EVID/24_batch_regresion.txt"
~~~

Resultado esperado: DailyTransactionsJobIntegrationTest, MonthlyInterestJobIntegrationTest y AnnualMovementsStepIntegrationTest PASS; 3 tests sin fallos, errores ni omitidos, BUILD SUCCESS. Los Jobs reales dailyTransactionsJob, monthlyInterestJob y annualStatementsJob muestran COMPLETED y el log incluye BATCH INIT COMPLETED. 
Evidencia: `24_batch_regresion.txt`.

### Paso 26 - Imágenes propias del proyecto

Inspeccionar únicamente las ocho imágenes construidas por este Compose, con el nombre de proyecto derivado del directorio proyecto. Esto evita depender de otras imágenes presentes en el equipo.

~~~bash
(
    set -e
    begin_evidence 'Imágenes Docker' 'Inspeccionar ocho imágenes propias' 'docker image inspect proyecto-<servicio>:latest'
    for service in authorization-server config-server eureka-server core-banking-service \
        bff-web-service bff-mobile-service bff-atm-service batch-init; do
        docker image inspect --format '{{.RepoTags}} {{.Id}} bytes={{.Size}}' \
            "proyecto-$service:latest"
    done
    echo 'RESULTADO: PASS'
) 2>&1 | tee "$EVID/25_docker_images.txt"
~~~

Resultado esperado: ocho imágenes propias con tag, ID y tamaño; PostgreSQL y Kafka utilizan sus imágenes de infraestructura. 
Evidencia: `25_docker_images.txt`. No establecer otro COMPOSE_PROJECT_NAME ni utilizar -p durante esta secuencia.

### Paso 27 - Servicios registrados en Eureka

Esperar el registro de los cuatro servicios de negocio y comprobar sus estados XML, sin asumir que la sola presencia del nombre implica UP.

~~~bash
(
    set -e
    begin_evidence 'Eureka' 'Comprobar cuatro servicios UP' 'GET http://localhost:8761/eureka/apps'
    wait_eureka
    echo 'RESULTADO: PASS'
) 2>&1 | tee "$EVID/26_eureka_servicios.txt"
~~~

Resultado esperado: CORE-BANKING-SERVICE, BFF-WEB-SERVICE, BFF-MOBILE-SERVICE y BFF-ATM-SERVICE UP. 
Evidencia: `26_eureka_servicios.txt`. Antes de las primeras consultas autorizadas, si el descubrimiento aún devuelve 503, esperar y repetir la consulta correspondiente; conservar el diagnóstico y no publicar otro retiro.

### Paso 28 - Config Server sin secretos

Consultar los cuatro endpoints de Config Server y guardar en la evidencia solo las propiedades necesarias, evitando registrar credenciales. La configuración usa el issuer local fuera de Docker y el issuer interno cuando se ejecuta con Docker Compose.

~~~bash
(
    set -e
    begin_evidence 'Config Server' 'Comprobar configuración centralizada y fuentes' 'GET /<servicio>/default; selección de propiedades públicas'
    for service in core-banking-service bff-web-service bff-mobile-service bff-atm-service; do
        curl -fsS --max-time 15 "http://localhost:8888/$service/default" | python3 -c '
import json,sys
d=json.load(sys.stdin)
allowed={"server.port","spring.security.oauth2.resourceserver.jwt.issuer-uri",
         "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
         "spring.kafka.consumer.group-id","spring.kafka.listener.concurrency",
         "spring.kafka.producer.value-serializer"}
sources=[{"name":p["name"],"source":{k:v for k,v in p["source"].items() if k in allowed}}
         for p in d["propertySources"]]
assert d["name"] == sys.argv[1] and sources
assert any(sys.argv[1]+".yml" in p["name"] for p in sources)
print(json.dumps({"name":d["name"],"profiles":d["profiles"],"propertySources":sources},ensure_ascii=False))
' "$service"
    done
    echo 'RESULTADO: PASS'
) 2>&1 | tee "$EVID/27_config_server.txt"
~~~

Resultado esperado: cuatro respuestas HTTP 200 (curl -f rechaza errores), name/profiles y propertySources procedentes de config-repo, con propiedades públicas seleccionadas. 
Evidencia: `27_config_server.txt`.

### Paso 29 - Verificación documental y alcance

Revisar la estructura del proyecto y comprobar la existencia de las tres clases de prueba de Resilience4j y sus nueve métodos de prueba. Verificar además que los componentes y dependencias JWT propios de la implementación anterior hayan sido eliminados, que la evidencia 01 registre los 92 tests exitosos y que la estructura documental de Semana 08 sea correcta.

~~~bash
(
    set -e
    begin_evidence 'Verificación documental' 'Comprobar tests, migración e inventario' \
        'Lectura de fuentes/POM/evidencias; git status --short'
    python3 - "$EVID" <<'PY'
import pathlib,re,sys
root=pathlib.Path("."); evid=pathlib.Path(sys.argv[1])
methods=("circuitBreakerStartsClosedAndAllowsSuccessfulCall",
         "circuitBreakerOpensAfterConfiguredFailuresAndShortCircuits",
         "circuitBreakerRecoversThroughHalfOpenAndReturnsToClosed")
count=0
for channel,cls in (("web","Web"),("mobile","Mobile"),("atm","Atm")):
    path=root/f"bff-{channel}-service/src/test/java/cl/duoc/bancoxyz/{channel}/{cls}Resilience4jTest.java"
    text=path.read_text()
    for method in methods:
        assert re.search(r"@Test\s+void\s+"+method+r"\(",text), (path,method)
        count+=1
        print(path,method,"PRESENTE")
    for old in ("AuthController","JwtService","JwtAuthenticationFilter"):
        matches=list((root/f"bff-{channel}-service/src/main").rglob(old+".java"))
        assert not matches, matches
        print(channel,old,"AUSENTE")
assert count == 9
for pom in [root/"pom.xml",*root.glob("*/pom.xml")]:
    assert not re.search(r"\bjjwt(?:-api|-impl|-jackson)?\b",pom.read_text()), pom
print("Dependencias jjwt propias: AUSENTES")
full=(evid/"01_maven_test.txt").read_text()
for line in ("Tests run: 92","Failures: 0","Errors: 0","Skipped: 0","BUILD SUCCESS","RESULTADO: PASS"):
    assert line in full, line
    print(line)
doc=pathlib.Path("../README.md").read_text()
assert len(re.findall(r"^# Semana 08 - .+$",doc,re.M)) == 1
assert re.search(r"^## Ejecución(?: manual reproducible)?$",doc,re.M)
for path in sorted(evid.iterdir()):
    if path.is_file():
        print(path.name)
print("00: índice nuevo. 28 y 31: runtime seguro generado en el Paso 30. 33: runtime definitiva.")
PY
    GIT_OPTIONAL_LOCKS=0 git status --short
    echo 'RESULTADO: PASS'
) 2>&1 | tee "$EVID/29_verificacion_documental_alcance.txt"
~~~

Resultado esperado: tres clases de prueba de Resilience4j con nueve métodos en total, componentes JWT anteriores y dependencias jjwt ausentes, evidencia 01 con 92 tests y BUILD SUCCESS, estructura documental válida y RESULTADO: PASS.
Evidencia: `29_verificacion_documental_alcance.txt`.

### Paso 30 - Runtime Resilience4j y recuperación segura

Comprobar la respuesta de Web, Mobile y ATM antes, durante y después de detener Core, verificando la secuencia HTTP 200 → 503 → 200. La prueba no realiza retiros ni modificaciones SQL y genera las evidencias 28 y 31 en una misma ejecución. Si los tokens OAuth2 han vencido, deben renovarse antes de comenzar.

Antes y después de la prueba se comprueba el estado de batch-init y los saldos de las cuentas, para verificar que Batch no se reejecute y que los datos no sean modificados. Si ocurre un error mientras Core está detenido, el procedimiento intenta iniciarlo nuevamente antes de finalizar.

~~~bash
batch_snapshot() {
    local id
    id=$(docker compose ps -a -q batch-init) || return 1
    test -n "$id" || return 1
    docker inspect --format '{{.Id}} {{.State.Status}} {{.State.ExitCode}} {{.State.StartedAt}} {{.RestartCount}}' "$id"
}

balances_snapshot() {
    docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -At -U banco_user -d banco_xyz -c \
        'SELECT cuenta_id, saldo_final FROM intereses_mensuales ORDER BY cuenta_id;'
}

runtime_call() {
    local channel="$1" phase="$2" attempt="$3" url token
    case "$channel" in
        web) url="http://localhost:8082/api/bff/web/accounts/$ACCOUNT_ID"; token="$WEB_TOKEN" ;;
        mobile) url="http://localhost:8083/api/bff/mobile/accounts/$ACCOUNT_ID"; token="$MOBILE_TOKEN" ;;
        atm) url="http://localhost:8084/api/bff/atm/accounts/$ACCOUNT_ID/balance"; token="$ATM_TOKEN" ;;
        *) return 1 ;;
    esac
    http_request GET "$url" "$token" || return 1
    printf '%s %s intento=%s HTTP=%s %s\n' "$phase" "$channel" "$attempt" "$HTTP_STATUS" "$HTTP_BODY"
}

resilience_runtime() (
    set -e
    local calls="$1" file="$2" channel attempt round consecutive
    local core_stopped=0 batch_before batch_after balances_before balances_after
    restore_core() {
        if [ "$core_stopped" -eq 1 ]; then
            echo 'Guarda: recuperar exclusivamente Core.'
            docker compose restart --no-deps core-banking-service
            wait_url http://localhost:8081/actuator/health 'Core tras guarda'
        fi
    }
    trap restore_core EXIT
    trap 'exit 130' INT
    trap 'exit 143' TERM

    begin_evidence "$file" 'Comprobar fallback, recuperación y conservación de Batch/saldos' \
        'GET por canal; docker compose stop core-banking-service; docker compose restart --no-deps core-banking-service; inspección y SELECT'
    batch_before=$(batch_snapshot)
    [[ "$batch_before" == *" exited 0 "* ]]
    balances_before=$(balances_snapshot)
    test -n "$balances_before"
    printf 'batch-init antes: %s\nSaldos antes:\n%s\n' "$batch_before" "$balances_before"

    for channel in web mobile atm; do
        for attempt in {1..60}; do
            runtime_call "$channel" DISPONIBLE "$attempt"
            if [ "$HTTP_STATUS" = 200 ]; then break; fi
            test "$HTTP_STATUS" = 503
            sleep 2
        done
        test "$HTTP_STATUS" = 200
    done

    core_stopped=1
    docker compose stop core-banking-service
    test "$(docker inspect --format '{{.State.Status}}' "$(docker compose ps -a -q core-banking-service)")" = exited
    for ((round=1; round<=calls; round++)); do
        for channel in web mobile atm; do
            runtime_call "$channel" DETENIDO "$round"
            test "$HTTP_STATUS" = 503
        done
    done

    docker compose restart --no-deps core-banking-service
    wait_url http://localhost:8081/actuator/health 'Core recuperado'
    core_stopped=0
    sleep 6
    for channel in web mobile atm; do
        consecutive=0
        for attempt in {1..60}; do
            runtime_call "$channel" RECUPERADO "$attempt"
            if [ "$HTTP_STATUS" = 200 ]; then
                consecutive=$((consecutive+1))
                if [ "$consecutive" -eq 3 ]; then break; fi
            else
                consecutive=0
                test "$HTTP_STATUS" = 503
            fi
            sleep 2
        done
        test "$consecutive" -eq 3
    done

    batch_after=$(batch_snapshot)
    balances_after=$(balances_snapshot)
    printf 'batch-init después: %s\nSaldos después:\n%s\n' "$batch_after" "$balances_after"
    test "$batch_before" = "$batch_after"
    test "$balances_before" = "$balances_after"
    echo 'batch-init no reejecutado: PASS'
    echo 'Balances no modificados: PASS'
    echo 'RESULTADO: PASS'
    trap - EXIT INT TERM
)

resilience_runtime 1 'Runtime Resilience4j y recuperación segura - evidencias 28 y 31' \
    2>&1 | tee "$EVID/28_resilience4j_runtime.txt" \
                   "$EVID/31_resilience4j_recuperacion_sin_dependencias.txt"
~~~

Resultado esperado: Web, Mobile y ATM deben responder HTTP 503 mientras Core está detenido y recuperar posteriormente tres respuestas HTTP 200 consecutivas por canal. batch-init no debe volver a ejecutarse y los saldos deben permanecer sin cambios.
Evidencias: `28_resilience4j_runtime.txt` y `31_resilience4j_recuperacion_sin_dependencias.txt`, generadas en la misma ejecución. La recuperación reinicia únicamente core-banking-service, sin reiniciar sus dependencias.

### Paso 31 - Configuración productiva de Resilience4j

Consultar Config Server y verificar los valores efectivos de Resilience4j para cada circuito, identificando además la fuente de configuración utilizada. La comprobación se realiza sobre la respuesta real de Config Server y no solo sobre los archivos YAML locales.

~~~bash
(
    set -e
    begin_evidence 'Resilience4j productivo' 'Comprobar configuración centralizada efectiva' \
        'GET /bff-web-service/default, /bff-mobile-service/default, /bff-atm-service/default'
    for pair in 'bff-web-service coreWeb' 'bff-mobile-service coreMobile' 'bff-atm-service coreAtm'; do
        read -r service circuit <<< "$pair"
        curl -fsS --max-time 15 "http://localhost:8888/$service/default" | python3 -c '
import json,sys
d=json.load(sys.stdin); service,circuit=sys.argv[1:3]
assert d["name"] == service
expected={"sliding-window-size":5,"minimum-number-of-calls":5,"failure-rate-threshold":50,
          "wait-duration-in-open-state":"5s","permitted-number-of-calls-in-half-open-state":2}
values={}; sources={}
for field,wanted in expected.items():
    key="resilience4j.circuitbreaker.instances."+circuit+"."+field
    for source in d["propertySources"]:
        if key in source["source"]:
            values[field]=source["source"][key]; sources[field]=source["name"]
            break
    assert field in values, key
    actual=str(values[field]) if isinstance(wanted,str) else int(values[field])
    assert actual == wanted, (circuit,field,actual,wanted)
print(json.dumps({"endpoint":"http://localhost:8888/"+service+"/default","HTTP":200,
                  "circuit":circuit,"values":values,"sources":sources},ensure_ascii=False))
' "$service" "$circuit"
    done
    echo 'RESULTADO: PASS'
) 2>&1 | tee "$EVID/32_resilience4j_configuracion_productiva.txt"
~~~

Resultado esperado: coreWeb, coreMobile y coreAtm con sliding-window-size=5, minimum-number-of-calls=5, failure-rate-threshold=50, wait-duration-in-open-state=5s y permitted-number-of-calls-in-half-open-state=2, entregados por Config Server. 
Evidencia: `32_resilience4j_configuracion_productiva.txt`.

### Paso 32 - Runtime definitivo Resilience4j

Reutilizar la prueba del Paso 30, realizando seis consultas por cada canal mientras Core está detenido. La validación utiliza únicamente consultas GET de cuenta y saldo, sin ejecutar retiros ni modificar balances. Si los tokens OAuth2 han vencido, deben renovarse antes de comenzar, manteniendo la misma cuenta seleccionada.

~~~bash
resilience_runtime 6 '33_resilience4j_runtime_final.txt' \
    2>&1 | tee "$EVID/33_resilience4j_runtime_final.txt"
~~~

Resultado esperado: con Core disponible, Web, Mobile y ATM responden HTTP 200. Con Core detenido, cada canal registra seis respuestas HTTP 503. Después de reiniciar únicamente Core, se verifica su healthcheck y la recuperación de los tres canales mediante tres respuestas HTTP 200 consecutivas. batch-init no debe reejecutarse y los saldos deben permanecer sin cambios.
Evidencia: `33_resilience4j_runtime_final.txt` — evidencia runtime definitiva.
Esta prueba demuestra el fallback observable durante la caída de Core y la posterior recuperación del servicio. Los estados internos OPEN y HALF_OPEN de Resilience4j se validan mediante las pruebas JUnit correspondientes.

### Paso 33 - Verificación final Semana 08

Realizar la verificación final de las evidencias de Semana 08, comprobando el estado de Docker Compose y la información de Kafka. Este paso no ejecuta nuevas pruebas ni modifica servicios o datos; solo genera el resumen final.
Se verifica que la carpeta activa contenga exactamente las 35 evidencias esperadas, que todas estén generadas durante la ejecución actual y que no existan archivos adicionales. Los respaldos externos no forman parte de esta comprobación.

~~~bash
begin_evidence 'Verificación final Semana 08' 'Resumir criterios reales de la ejecución manual' \
    'Lectura de las 35 evidencias nuevas; docker compose ps -a; docker inspect; consultas Kafka' \
    | tee "$EVID/34_verificacion_final_semana_08.txt"

(
    set -e
    trap 'verification_status=$?; if [ "$verification_status" -eq 0 ]; then echo "RESULTADO FINAL: PASS"; else echo "RESULTADO FINAL: FAIL - código $verification_status"; fi' EXIT
    python3 - "$EVID" "$RUN_STARTED" "${EXPECTED_EVIDENCES[@]}" <<'PY'
import datetime,json,pathlib,re,sys
evid=pathlib.Path(sys.argv[1]); started=datetime.datetime.fromisoformat(sys.argv[2]).timestamp()
expected=sys.argv[3:]
assert len(expected) == 35
print("=== INVENTARIO Y PROCEDENCIA ===")
active=pathlib.Path("/home/francisco/proyecto/BACKEND III/SEMANA 8/Exp3_S8_Francisco_Henriquez/evidencias/semana 08")
assert evid == active and evid.resolve() == active and not evid.is_symlink()
assert len(set(expected)) == 35
observed={path.name for path in evid.iterdir()}
assert observed == set(expected), {"faltantes":sorted(set(expected)-observed),"extras":sorted(observed-set(expected))}
for name in expected:
    path=evid/name
    assert path.is_file() and not path.is_symlink(), ("No es archivo regular propio",name)
    info=path.stat()
    assert info.st_size > 0, ("Evidencia vacía",name)
    assert info.st_mtime > started, ("Evidencia anterior o igual a RUN_STARTED",name)
    print(name,"bytes="+str(info.st_size),"mtime="+datetime.datetime.fromtimestamp(info.st_mtime).astimezone().isoformat())
print("Inventario: PASS - 35 archivos nuevos, no vacíos, posteriores a RUN_STARTED.")
print("00: índice nuevo de esta reproducción; 28 y 31: runtime seguro generado conjuntamente.")
print("33: EVIDENCIA RUNTIME DEFINITIVA. El respaldo no interviene en la comprobación.")

def content(prefix):
    matches=[n for n in expected if n.startswith(prefix+"_")]
    assert len(matches) == 1
    return (evid/matches[0]).read_text()

def require(prefix,pattern):
    assert re.search(pattern,content(prefix),re.M), (prefix,pattern)

for prefix,total in (("01",92),("22",1),("23",9),("24",3)):
    for pattern in (rf"^Tests run: {total}$",r"^Failures: 0$",r"^Errors: 0$",r"^Skipped: 0$",
                    r"BUILD SUCCESS",r"^RESULTADO: PASS$"):
        require(prefix,pattern)
    print(f"Maven {prefix}: {total}/{total}; 0 failures/errors/skipped; BUILD SUCCESS.")
require("22",r"ignoresAnEventAlreadyProcessed: PASS")
for method in ("circuitBreakerStartsClosedAndAllowsSuccessfulCall",
               "circuitBreakerOpensAfterConfiguredFailuresAndShortCircuits",
               "circuitBreakerRecoversThroughHalfOpenAndReturnsToClosed"):
    assert content("23").count(method+": PASS") == 3, method
print("Resilience4j JUnit: CLOSED, OPEN, HALF_OPEN, CLOSED recuperado, fallback y short-circuit PASS.")

for prefix,code in (("09",401),("10",200),("11",403),("12",401),("13",200),
                    ("14",200),("15",401),("16",200),("17",202),("18",200)):
    require(prefix,rf"^HTTP status: {code}$")
    require(prefix,r"^RESULTADO: PASS$")
    print(f"HTTP evidencia {prefix}: {code} PASS.")
for prefix,scope in (("05","web"),("06","mobile"),("07","atm")):
    text=content(prefix)
    assert "HTTP status: 200" in text
    metadata=json.loads(next(line for line in text.splitlines() if line.startswith("{")))
    assert metadata["scope"] == scope and metadata["token_type"].lower() == "bearer"
    assert 0 < int(metadata["expires_in"]) <= 1800
    print(f"OAuth2 {scope}: HTTP 200, Bearer, scope y expires_in PASS.")
require("08",r"^RESULTADO: PASS$")
print("JWT claims: RS256, issuer Docker, sub, scope y vigencia PASS.")
require("17",r'"status"\s*:\s*"PENDING"')
require("18",r"^Estado final: COMPLETED$")
print("Retiro: 202/PENDING -> 200/COMPLETED; saldo comprobado. Core protegido: 401/200.")

for topic in ("bank.withdrawal.requested","bank.withdrawal.completed","bank.withdrawal.rejected"):
    assert topic in content("19"), topic
assert re.search(r"Topic:\s*bank\.withdrawal\.requested.*PartitionCount:\s*3",content("19"))
require("20",r"core-banking-withdrawal-group")
for field in ("CURRENT-OFFSET","LOG-END-OFFSET","LAG","CONSUMER-ID"):
    assert field in content("20"), field
for marker in ("WithdrawalRequested","[Kafka][Consumer]","bank.withdrawal.completed","WithdrawalCompleted"):
    assert marker in content("21"), marker
print("Kafka: tres topics, tres particiones, consumer group, offsets/lag y trazabilidad PASS.")
for job in ("dailyTransactionsJob","monthlyInterestJob","annualStatementsJob"):
    assert re.search(job+r".*COMPLETED",content("24")), job
require("24",r"BATCH INIT COMPLETED")
print("Spring Batch: tres tests y tres Jobs COMPLETED PASS.")

for prefix in ("02","04","25","26","27","28","29","30","31","32","33"):
    require(prefix,r"^RESULTADO: PASS$")
print("Build, health, ocho imágenes, Eureka, Config Server y alcance documental PASS.")
for service in ("authorization-server","config-server","eureka-server","core-banking-service",
                "bff-web-service","bff-mobile-service","bff-atm-service","batch-init"):
    assert "proyecto-"+service+":latest" in content("25"), service
for service in ("CORE-BANKING-SERVICE","BFF-WEB-SERVICE","BFF-MOBILE-SERVICE","BFF-ATM-SERVICE"):
    assert service+" UP" in content("26"), service

configs=[json.loads(line) for line in content("32").splitlines() if line.startswith("{")]
wanted={"sliding-window-size":5,"minimum-number-of-calls":5,"failure-rate-threshold":50,
        "wait-duration-in-open-state":"5s","permitted-number-of-calls-in-half-open-state":2}
assert {d["circuit"] for d in configs} == {"coreWeb","coreMobile","coreAtm"}
for config in configs:
    assert config["HTTP"] == 200
    for key,value in wanted.items():
        actual=config["values"][key]
        assert (str(actual) if isinstance(value,str) else int(actual)) == value
    print(config["circuit"],config["values"])
print("Configuración productiva desde Config Server: PASS.")

for prefix,calls in (("28",1),("31",1),("33",6)):
    text=content(prefix)
    for channel in ("web","mobile","atm"):
        assert re.search(rf"^DISPONIBLE {channel} intento=\d+ HTTP=200 ",text,re.M)
        failures=re.findall(rf"^DETENIDO {channel} intento=(\d+) HTTP=503 ",text,re.M)
        assert failures == [str(i) for i in range(1,calls+1)], (prefix,channel,failures)
        assert len(re.findall(rf"^RECUPERADO {channel} intento=\d+ HTTP=200 ",text,re.M)) >= 3
    assert "batch-init no reejecutado: PASS" in text and "Balances no modificados: PASS" in text
    print(f"Runtime {prefix}: 200 -> {calls} respuestas 503 por canal -> 200; Batch y balances conservados PASS.")
print("Comprobaciones documentales de esta ejecución: PASS.")
PY

    echo '=== COMPOSE ACTUAL ==='
    docker compose ps -a
    for service in postgres kafka config-server eureka-server authorization-server \
        core-banking-service bff-web-service bff-mobile-service bff-atm-service; do
        id=$(docker compose ps -a -q "$service")
        test -n "$id"
        state=$(docker inspect --format '{{.State.Status}} {{.State.Health.Status}}' "$id")
        printf '%s: %s\n' "$service" "$state"
        test "$state" = 'running healthy'
    done
    batch_snapshot
    [[ "$(batch_snapshot)" == *" exited 0 "* ]]

    echo '=== KAFKA ACTUAL ==='
    topics=$(docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh \
        --bootstrap-server localhost:9092 --describe --topic bank.withdrawal.requested)
    printf '%s\n' "$topics"
    printf '%s\n' "$topics" | grep -E 'PartitionCount:[[:space:]]*3'
    group=$(docker compose exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh \
        --bootstrap-server localhost:9092 --describe --group core-banking-withdrawal-group)
    printf '%s\n' "$group"
    printf '%s\n' "$group" | grep 'core-banking-withdrawal-group'
) 2>&1 | tee -a "$EVID/34_verificacion_final_semana_08.txt"
~~~

Resultado esperado: la carpeta debe contener exactamente las 35 evidencias de Semana 08, todas generadas durante la ejecución actual y ninguna vacía. La verificación final debe confirmar Maven con 92/92 tests y BUILD SUCCESS, seguridad OAuth2/JWT, validaciones HTTP, retiro ATM, Kafka, idempotencia, Resilience4j, Spring Batch y configuración productiva con resultado correcto. Los nueve servicios deben permanecer healthy y batch-init en estado Exited (0).
Evidencia: `34_verificacion_final_semana_08.txt`. El procedimiento registra RESULTADO FINAL: PASS cuando todas las comprobaciones terminan correctamente y RESULTADO FINAL: FAIL si alguna falla.

### Mapa paso / evidencia

El número de paso sigue el orden de ejecución; el número del TXT conserva el inventario existente.

| Paso | Evidencia                                                                          | Objetivo                                                    |
| ---- | ---------------------------------------------------------------------------------- | ----------------------------------------------------------- |
| 00   | `00_indice_evidencias.md`                                                          | Respaldo verificado, limpieza e índice nuevo                |
| 01   | `01_maven_test.txt`                                                                | Suite Maven: 92 tests                                       |
| 02   | `02_docker_compose_build.txt`                                                      | Ocho imágenes propias                                       |
| 03   | `30_docker_reset_arranque.txt`                                                     | Reset único, arranque y espera                              |
| 04   | `03_docker_compose_ps.txt`                                                         | Estado Compose                                              |
| 05   | `04_healthchecks.txt`                                                              | Healthchecks                                                |
| 06   | `05_oauth2_token_web.txt`                                                          | OAuth2 Web                                                  |
| 07   | `06_oauth2_token_mobile.txt`                                                       | OAuth2 Mobile                                               |
| 08   | `07_oauth2_token_atm.txt`                                                          | OAuth2 ATM                                                  |
| 09   | `08_oauth2_jwt_claims.txt`                                                         | Claims JWT                                                  |
| 10   | `14_atm_saldo_autorizado.txt`                                                      | Cuenta real y saldo ATM                                     |
| 11   | `09_web_sin_token_401.txt`                                                         | Web sin token:401                                           |
| 12   | `10_web_scope_correcto.txt`                                                        | Web con scope: 200                                          |
| 13   | `11_web_scope_incorrecto_403.txt`                                                  | Web con scope ATM: 403                                      |
| 14   | `12_web_token_invalido_401.txt`                                                    | Token inválido: 401                                         |
| 15   | `13_mobile_autorizado.txt`                                                         | Mobile: 200                                                 |
| 16   | `15_core_sin_token_401.txt`                                                        | Core sin token: 401                                         |
| 17   | `16_core_token_valido.txt`                                                         | Core con token: 200                                         |
| 18   | `17_atm_retiro_pending.txt`                                                        | Retiro 202/PENDING                                          |
| 19   | `18_atm_retiro_resultado_final.txt`                                                | Polling terminal y saldo                                    |
| 20   | `19_kafka_topics_particiones.txt`                                                  | Topics y particiones                                        |
| 21   | `20_kafka_consumer_group.txt`                                                      | Consumer group                                              |
| 22   | `21_kafka_flujo_retiro.txt`                                                        | Trazabilidad Kafka                                          |
| 23   | `22_kafka_idempotencia_test.txt`                                                   | Idempotencia                                                |
| 24   | `23_resilience4j_tests.txt`                                                        | Nueve tests Resilience4j                                    |
| 25   | `24_batch_regresion.txt`                                                           | Tres tests y Jobs Batch                                     |
| 26   | `25_docker_images.txt`                                                             | Imágenes del proyecto                                       |
| 27   | `26_eureka_servicios.txt`                                                          | Eureka UP                                                   |
| 28   | `27_config_server.txt`                                                             | Config Server sin secretos                                  |
| 29   | `29_verificacion_documental_alcance.txt`                                           | Inspección documental                                       |
| 30   | `28_resilience4j_runtime.txt`, `31_resilience4j_recuperacion_sin_dependencias.txt` | Una comprobación runtime segura capturada en dos TXT nuevos |
| 31   | `32_resilience4j_configuracion_productiva.txt`                                     | Parámetros productivos desde Config                         |
| 32   | `33_resilience4j_runtime_final.txt`                                                | Runtime definitivo sin retiros                              |
| 33   | `34_verificacion_final_semana_08.txt`                                              | Verificación final                                          |

## Colección final esperada - Semana 08

La carpeta activa `evidencias/semana 08` queda formada exclusivamente por **35 evidencias**: el índice 00 y los 34 TXT 01–34. El Paso 00 crea el índice desde cero y el Paso 30 genera conjuntamente 28 y 31 con una única ejecución runtime segura. El respaldo está fuera de la carpeta activa y solo sirve para recuperación; no integra la entrega ni se lee para validar o reconstruir evidencias. Registrar las salidas reales, incluidos errores o estados transitorios.

1. `00_indice_evidencias.md` — Índice generado en el Paso 00.
2. `01_maven_test.txt`
3. `02_docker_compose_build.txt`
4. `03_docker_compose_ps.txt`
5. `04_healthchecks.txt`
6. `05_oauth2_token_web.txt`
7. `06_oauth2_token_mobile.txt`
8. `07_oauth2_token_atm.txt`
9. `08_oauth2_jwt_claims.txt`
10. `09_web_sin_token_401.txt`
11. `10_web_scope_correcto.txt`
12. `11_web_scope_incorrecto_403.txt`
13. `12_web_token_invalido_401.txt`
14. `13_mobile_autorizado.txt`
15. `14_atm_saldo_autorizado.txt`
16. `15_core_sin_token_401.txt`
17. `16_core_token_valido.txt`
18. `17_atm_retiro_pending.txt`
19. `18_atm_retiro_resultado_final.txt`
20. `19_kafka_topics_particiones.txt`
21. `20_kafka_consumer_group.txt`
22. `21_kafka_flujo_retiro.txt`
23. `22_kafka_idempotencia_test.txt`
24. `23_resilience4j_tests.txt`
25. `24_batch_regresion.txt`
26. `25_docker_images.txt`
27. `26_eureka_servicios.txt`
28. `27_config_server.txt`
29. `28_resilience4j_runtime.txt` — Runtime seguro generado en el Paso 30, junto con 31.
30. `29_verificacion_documental_alcance.txt`
31. `30_docker_reset_arranque.txt`
32. `31_resilience4j_recuperacion_sin_dependencias.txt`
33. `32_resilience4j_configuracion_productiva.txt`
34. `33_resilience4j_runtime_final.txt` — **EVIDENCIA RUNTIME DEFINITIVA.**
35. `34_verificacion_final_semana_08.txt`

## Diagrama de arquitectura - Semana 08

Como documentación complementaria, se incluye un diagrama de la arquitectura final del proyecto correspondiente a Semana 08, generado mediante Archify.

El diagrama representa de forma visual los principales componentes e interacciones de la solución desarrollada.

Diagrama: [arquitectura_semana08.html](documentacion/semana-08/arquitectura_semana08.html)

Archivos asociados:

`documentacion/semana-08/`
