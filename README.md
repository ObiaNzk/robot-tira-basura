# Robot Tira Basura — HMI para Unitree G1 (simulado)

TP grupal de Desarrollo de Aplicaciones I (UADE): interfaz de operación y supervisión de un **Unitree G1** que
recoge bolsas de basura y las tira en un tacho. El APK funciona **sin robot, sin servidor y sin internet**: el
robot es un simulador que corre dentro de la app, detrás de un `Repository`.

## La misión

1. El operador escanea dos QR impresos: uno marca **dónde están las bolsas** y el otro **dónde está el tacho**.
2. La app calcula la ruta (A* sobre el plano del sitio, esquivando mesas) y muestra distancia y tiempo estimado.
3. Al iniciar, el robot ejecuta la **macro**: ir a las bolsas → recoger bolsa → ir al tacho → levantar tapa →
   depositar bolsa → bajar tapa → volver al punto seguro.

Los QR tienen el formato `RTB:<BOLSAS|TACHO>:<id>:<x>,<y>` (celda del plano). Hay cuatro listos para imprimir en
`docs/qr/`. Sin cámara (emulador), la pestaña Misión ofrece "Simular lectura".

## Cómo correrlo

Requisitos: Android Studio con su JBR (JDK 21) y el SDK de Android 37. Desde la raíz:

```bash
./gradlew assembleDebug          # APK en app/build/outputs/apk/debug/
./gradlew installDebug           # instalar en el emulador o teléfono conectado
./gradlew testDebugUnitTest      # tests JVM (dominio, simulador, repositorio, ViewModels)
```

Si el JDK del sistema es más nuevo que el que soporta Gradle, exportá
`JAVA_HOME` apuntando al JBR de Android Studio.

## Verlo en 3D (opcional)

`mujoco/visor_g1.py` muestra en MuJoCo, con el modelo oficial del G1, lo que hace el robot simulado:
camina por el patio, recoge la bolsa, levanta la tapa y la deposita. Es una visualización
cinemática, sin física. Se activa con `robot.mujocoVisorUrl=http://10.0.2.2:8766/` en
`local.properties` (solo en builds debug). Los pasos están en `mujoco/README.md`.

## Pantallas

| Pestaña | Para qué |
|---|---|
| Selección de robot | Conectarse al G1 simulado |
| Supervisión | Estado, misión y pasos, mapa con ruta, telemetría, comandos, últimas órdenes y su estado |
| Misión | Escanear los dos QR, ver la ruta planificada e iniciar |
| Alertas | Alertas ordenadas por gravedad; reconocerlas |
| Historial | Misiones y eventos guardados en Room (persisten al cerrar la app) |
| Simulador | Uso docente/pruebas: cortar el enlace, perder respuestas, rechazar órdenes, obstáculos, tacho lleno, persona cerca, batería |

### Navegación

```mermaid
flowchart LR
    START((Inicio)) --> SEL["Selección de robot<br/>G1 simulado"]
    SEL -- "Conectar" --> SUP

    subgraph TABS["Barra inferior (con robot conectado)"]
        direction TB
        SUP["Supervisión<br/>estado · pasos · mapa · telemetría<br/>comandos · estado de órdenes"]
        MIS["Misión<br/>escanear QR bolsas + tacho<br/>ruta A* · Iniciar"]
        ALE["Alertas<br/>por gravedad · reconocer"]
        HIS["Historial<br/>misiones y eventos (Room)"]
        SIMU["Simulador<br/>forzar fallas"]
    end

    MIS -- "Iniciar (confirmar recorrido despejado)" --> SUP
    SUP <--> MIS
    SUP <--> ALE
    SUP <--> HIS
    SUP <--> SIMU
    TABS -- "Desconectar<br/>(el robot sigue su misión)" --> SEL

    classDef main fill:#E3F2FD,stroke:#1565C0,color:#0D47A1
    classDef test fill:#FFF3E0,stroke:#EF6C00,color:#E65100
    class SEL,SUP,MIS,ALE,HIS main
    class SIMU test
```

## Cómo demostrar los cinco escenarios

| Escenario | Pasos |
|---|---|
| 1. Misión normal | Misión → simular `BOLSAS:B1` y `TACHO:T1` → Iniciar → ver progreso hasta "Completada" |
| 2. Batería baja | Simulador → batería 15 % → aparece alerta y "Regresar a punto seguro"; con 8 % el robot se detiene solo |
| 3. Desconexión | Simulador → apagar "Enlace" → banner "datos antiguos", chip amarillo/rojo, una orden enviada termina "Sin respuesta" → encender el enlace y los datos vuelven |
| 4. Incidente | Durante la misión: Simulador → "Poner obstáculo adelante" → Supervisión pide decisión: rodear o regresar |
| 5. Propio: tacho lleno | Simulador → "Tacho lleno" antes de iniciar → al levantar la tapa el robot espera: reintentar (si se vació) o dejar la bolsa al lado |

## Arquitectura

MVVM con capas, siguiendo las convenciones de la cátedra (mismas que Laburin):

```
ui/        Compose: XRoute (ViewModel + collectAsStateWithLifecycle) y XContent sin estado, previsualizable
           ViewModels con StateFlow<XUiState> y SharedFlow para efectos de una vez (navegación)
domain/    Kotlin puro: modelos, CommandRules (habilitación), PathPlanner (A*), MissionPlanner (macro),
           QrCodes, interfaces RobotRepository / HistoryRepository / SimulatorControls
data/      SimulatedRobotRepository (latencia, timeout, enlace) + RobotSimulator (máquina de estados)
           Room (eventos y misiones) + HistoryRecorder (persiste todo lo que emite el robot)
di/        Hilt. Para un robot real alcanza con cambiar el binding de RobotRepository.
```

### Diagrama de capas

```mermaid
flowchart TB
    OP(["👤 Operador / Tester"])

    subgraph UI["Presentación — ui/ (Jetpack Compose + MVVM)"]
        direction LR
        SCR["Pantallas Compose<br/>Selección · Supervisión · Misión<br/>Alertas · Historial · Simulador"]
        VM["ViewModels<br/>StateFlow&lt;UiState&gt; + eventos"]
        SCR -- "eventos (clicks)" --> VM
        VM -- "estado (StateFlow)" --> SCR
    end

    subgraph DOM["Dominio — domain/ (Kotlin puro, sin Android)"]
        direction LR
        RULES["CommandRules<br/>habilitación de órdenes"]
        PLAN["PathPlanner (A*)<br/>MissionPlanner (macro 7 pasos)"]
        MODEL["Modelos<br/>Telemetry · Command · Mission · Alert · QrTarget"]
        IFACE[["Interfaces<br/>RobotRepository · HistoryRepository<br/>SimulatorControls"]]
    end

    subgraph DATA["Datos — data/"]
        direction LR
        REPO["SimulatedRobotRepository<br/>latencia 0,6 s · timeout 5 s · enlace on/off"]
        SIM["RobotSimulator<br/>máquina de estados · batería · alertas"]
        REC["HistoryRecorder"]
        ROOM[("Room<br/>missions · events")]
        MIR["VisorMirror<br/>(solo debug, opcional)"]
        REPO --> SIM
        REC --> ROOM
    end

    DI{{"di/ — Hilt<br/>conecta interfaces con implementaciones"}}
    MUJOCO["Visor 3D MuJoCo<br/>(PC, opcional)"]

    OP --> SCR
    VM -- "usa" --> RULES & PLAN
    VM -- "solo habla con" --> IFACE
    REPO -. "implementa" .-> IFACE
    REC -. "implementa HistoryRepository" .-> IFACE
    REPO -- "eventos y misiones" --> REC
    MIR -- "HTTP" --> MUJOCO
    REPO -- "telemetría" --> MIR
    DI -. "inyecta" .-> VM

    classDef ui fill:#E3F2FD,stroke:#1565C0,color:#0D47A1
    classDef dom fill:#E8F5E9,stroke:#2E7D32,color:#1B5E20
    classDef data fill:#FFF3E0,stroke:#EF6C00,color:#E65100
    classDef ext fill:#F5F5F5,stroke:#757575,color:#424242,stroke-dasharray:4 3
    class SCR,VM ui
    class RULES,PLAN,MODEL,IFACE dom
    class REPO,SIM,REC,ROOM,MIR data
    class MUJOCO,DI ext
```

La UI nunca habla con el simulador: solo con `RobotRepository`. Ver `docs/CONTRATO.md` (mensajes JSON) y
`docs/DECISIONES.md` (qué existe en el G1 y qué no, riesgos y limitaciones).

## Flujos

### Flujo normal: misión completa

Enviada no es hecha: la orden pasa de *pendiente* a *confirmada* solo cuando el robot responde.

```mermaid
sequenceDiagram
    autonumber
    actor OP as Operador
    participant UI as Pantallas + ViewModels
    participant DOM as Dominio<br/>(CommandRules, PathPlanner)
    participant REPO as RobotRepository<br/>(simulado)
    participant ROB as RobotSimulator
    participant DB as Room (historial)

    OP->>UI: Escanea QR BOLSAS:B1 y TACHO:T1
    UI->>DOM: Validar QR y planificar ruta (A*)
    DOM-->>UI: Ruta, distancia y tiempo estimado
    UI->>DOM: ¿"Iniciar" habilitado?<br/>2 QR válidos · batería ≥ 25 % · sin alerta crítica
    DOM-->>UI: Habilitado
    OP->>UI: Iniciar → confirma "recorrido despejado"
    UI->>REPO: send(INICIAR_MISION)
    REPO-->>UI: Orden PENDIENTE
    REPO->>ROB: Orden (latencia 0,6 s)
    ROB-->>REPO: Aceptada
    REPO-->>UI: Orden CONFIRMADA
    REPO->>DB: Misión iniciada + evento

    loop cada 0,5 s
        ROB-->>REPO: Telemetría (posición, batería, paso, progreso)
        REPO-->>UI: StateFlow<Telemetry>
    end

    Note over ROB: Ir a bolsas → recoger → ir a tacho → levantar tapa<br/>→ depositar → bajar tapa → volver a punto seguro

    ROB-->>REPO: Misión COMPLETADA
    REPO->>DB: Misión finalizada + eventos
    REPO-->>UI: Estado INACTIVO · progreso 100 %
    UI-->>OP: "Completada" y registro en Historial
```

### Flujo con falla: desconexión y obstáculo

Si se corta el enlace, la app marca los datos como antiguos y las órdenes terminan "Sin respuesta". Si aparece un obstáculo, el robot espera la decisión del operador.

```mermaid
sequenceDiagram
    autonumber
    actor OP as Operador
    participant UI as Supervisión
    participant REPO as RobotRepository<br/>(simulado)
    participant ROB as RobotSimulator
    participant DB as Room (historial)

    Note over OP,ROB: Misión en curso, telemetría llegando cada 0,5 s

    rect rgba(255, 152, 0, 0.12)
    Note over REPO,ROB: Falla 1 — Desconexión (Simulador: Enlace OFF)
    ROB--xREPO: Telemetría deja de llegar
    UI-->>OP: > 3 s: banner "Datos antiguos" (ámbar) con antigüedad
    UI-->>OP: > 10 s: "Sin conexión" (rojo) · comandos deshabilitados salvo Detención
    OP->>UI: Detención operativa (con confirmación)
    UI->>REPO: send(DETENCION_OPERATIVA)
    REPO-->>UI: Orden PENDIENTE
    REPO--xROB: Sin enlace
    REPO-->>UI: a los 5 s: SIN RESPUESTA
    Note over REPO,ROB: Enlace ON
    ROB-->>REPO: Telemetría + eventos acumulados
    REPO->>DB: Eventos guardados
    UI-->>OP: Datos frescos (verde) · se ve el estado real del robot
    end

    rect rgba(244, 67, 54, 0.10)
    Note over REPO,ROB: Falla 2 — Obstáculo en la ruta
    ROB-->>REPO: Alerta OBSTÁCULO · estado ESPERANDO_OPERADOR
    REPO->>DB: Evento de alerta
    REPO-->>UI: Alerta + pedido de decisión
    UI-->>OP: "Rodear" o "Regresar a punto seguro"
    OP->>UI: Rodear
    UI->>REPO: send(REPLANIFICAR)
    REPO->>ROB: Orden
    alt ruta alternativa existe
        ROB-->>REPO: CONFIRMADA · nueva ruta A*
        REPO-->>UI: Misión continúa
    else no hay ruta
        ROB-->>REPO: RECHAZADA (motivo SIN_RUTA)
        UI-->>OP: Motivo visible · queda "Regresar" disponible
    end
    end
```
