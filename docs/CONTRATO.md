# Contrato propuesto: telemetría, comandos y respuestas

> **Propuesta propia del TP.** No es un protocolo oficial de Unitree ni del SDK2. En el APK estos mensajes
> se intercambian en memoria con el simulador; el formato JSON sirve para documentar un futuro gateway.

## Telemetría (robot → app, cada 0,5 s mientras hay enlace)

```json
{
  "robot_id": "g1-sim-01",
  "timestamp": 1762640488000,
  "signal_percent": 82,
  "battery_percent": 64.2,
  "state": "EN_MISION",
  "wait_reason": null,
  "position": { "x_m": 2.75, "y_m": 3.25, "cell": [5, 6] },
  "heading_deg": 90,
  "speed_mps": 0.5,
  "mission": {
    "id": "c2b1…",
    "bolsas": { "id": "B1", "cell": [1, 9] },
    "tacho": { "id": "T1", "cell": [16, 1] },
    "current_step": "NAVEGAR_A_TACHO",
    "steps": ["COMPLETADO", "COMPLETADO", "EN_CURSO", "PENDIENTE", "PENDIENTE", "PENDIENTE", "PENDIENTE"],
    "progress_percent": 38,
    "result": null
  },
  "active_route": [[5, 6], [6, 6], [7, 6]],
  "extra_obstacles": [[8, 7]],
  "alerts": [
    { "id": "a1", "type": "OBSTACULO", "severity": "ADVERTENCIA", "created_at": 1762640480000, "acknowledged": false }
  ],
  "service": { "bags_collected": 3, "payload_kg": 3.0, "bin_fill_percent": 55 }
}
```

- `state`: `INACTIVO`, `CARGANDO`, `EN_MISION`, `PAUSADO`, `DETENIDO`, `ESPERANDO_OPERADOR`, `REGRESANDO`.
- `wait_reason` (solo en `ESPERANDO_OPERADOR`): `OBSTACULO` o `TACHO_LLENO`.
- Las tres variables del servicio son `bags_collected`, `payload_kg` y `bin_fill_percent`.
- **Frescura:** la app no confía en `timestamp`. Mide cuándo *recibió* el último mensaje: más de 3 s significa
  "datos antiguos" y más de 10 s significa "sin conexión".

## Comandos (app → robot)

```json
{ "command_id": "5f0e…", "type": "INICIAR_MISION", "sent_at": 1762640400000,
  "params": { "bolsas": { "id": "B1", "cell": [1, 9] }, "tacho": { "id": "T1", "cell": [16, 1] } } }
```

| `type` | `params` | Habilitado cuando |
|---|---|---|
| `INICIAR_MISION` | bolsas, tacho | Inactivo/cargando, dos QR válidos con ruta, batería ≥ 25 %, sin alerta crítica abierta |
| `PAUSAR` | — | En misión o regresando |
| `REANUDAR` | — | Pausado, detenido, o esperando por tacho lleno (reintento) |
| `CANCELAR` | — | Misión activa (en misión, pausado, detenido o esperando) |
| `REGRESAR_A_PUNTO_SEGURO` | — | No está regresando ni ya descansando en el punto seguro |
| `DETENCION_OPERATIVA` | — | Robot que puede moverse; **se ofrece aun sin enlace** |
| `RECONOCER_ALERTA` | `alert_id` | Hay alertas sin reconocer |
| `REPLANIFICAR` | — | Esperando por obstáculo |
| `DEJAR_JUNTO_AL_TACHO` | — | Esperando por tacho lleno |

Fuera de la detención, todo comando exige enlace fresco y que no haya otra orden del mismo tipo pendiente.

## Respuestas (robot → app)

```json
{ "command_id": "5f0e…", "status": "CONFIRMADA", "answered_at": 1762640400600 }
```

```json
{ "command_id": "7a91…", "status": "RECHAZADA", "reason": "BATERIA_INSUFICIENTE", "answered_at": 1762640400600 }
```

- Estados visibles en la app: `PENDIENTE` (enviada), `CONFIRMADA`, `RECHAZADA` (con `reason`) y `SIN_RESPUESTA`.
  `SIN_RESPUESTA` no es un mensaje del robot: la app lo marca sola a los 5 s sin respuesta.
- Motivos de rechazo: `ESTADO_INVALIDO`, `BATERIA_INSUFICIENTE`, `SIN_RUTA`, `ALERTA_CRITICA_PENDIENTE`,
  `ALERTA_INEXISTENTE`, `RECHAZO_SIMULADO`.
- **Confirmada ≠ ejecutada.** "Confirmada" solo significa que el robot aceptó la orden. El resultado se ve en la
  telemetría. Una orden "sin respuesta" puede haberse ejecutado igual, así que antes de reenviarla hay que
  revisar la supervisión.

## Eventos (para el historial)

```json
{ "timestamp": 1762640500000, "type": "MISION_FINALIZADA", "severity": "INFO", "detail": "COMPLETADA", "mission_id": "c2b1…" }
```
