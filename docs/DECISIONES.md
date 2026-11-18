# Decisiones, capacidades del G1, riesgos y limitaciones

## Por qué un G1 y no un Go2

Para tirar una bolsa hay que tomarla, levantar una tapa y soltarla adentro. Eso necesita brazos y manos. El Go2
es un cuadrúpedo sin brazo, así que no puede hacerlo sin equipamiento adicional. El G1 es humanoide: tiene brazos
de 7 grados de libertad y, en la versión EDU, puede usar manos diestras (Dex3-1).

## Qué existe hoy y qué requeriría desarrollo adicional

Fuente: el `SDK_CONTRACTS.md` del laboratorio `sdk_emulator` (selección del SDK2 Python del G1, consultado el
14-10-2026) y la documentación pública de Unitree. Conviene verificarlo contra el firmware real del robot.

| Necesidad del servicio | En el SDK2 (subconjunto del laboratorio) | Estado |
|---|---|---|
| Batería | `BmsState_.soc` / `soh` | **Disponible** |
| Inclinación / caída | `IMUState_.rpy` | **Disponible** |
| Avisar a personas | `AudioClient.TtsMaker`, `LedControl(R,G,B)` | **Disponible** (propuesta: LED ámbar en misión, rojo detenido; TTS "Robot en movimiento") |
| Gestos de cortesía | `LocoClient.WaveHand`, `G1ArmActionClient.ExecuteAction(18/99)` | **Disponible** (fuera del alcance de esta HMI) |
| Ir a un punto del plano | No está en el subconjunto. El SDK tiene velocidad (`LocoClient.Move`), no navegación a coordenadas. | **Desarrollo adicional**: mapa, localización (LiDAR Mid-360 / cámara de profundidad), planificador |
| Leer los QR desde el robot | No | **Desarrollo adicional**: visión con la cámara de cabeza. En el TP los lee el operador con el teléfono. |
| Recoger bolsa, levantar tapa, depositar | No: el `action_map` solo tiene gestos predefinidos | **Desarrollo adicional + equipamiento**: manos Dex3, control de brazo de bajo nivel, percepción del objeto |
| Detectar tacho lleno | No | **Desarrollo adicional**: visión o sensor en el tacho |
| Parada de emergencia | Física, en el control remoto del robot | La detención desde la app **no la reemplaza** |

## Decisiones de diseño

- **El simulador vive en el APK**, detrás de `RobotRepository`. El TP exige funcionar sin servidor ni red, así que
  el `sdk_emulator` (FastAPI + MuJoCo) no se usa desde la app. Podría servir para un video de demostración
  aparte.
- **Los QR los lee el teléfono del operador.** Así el operador confirma qué bolsas y qué tacho elige, y la cámara
  se usa solo cuando se pide (privacidad).
- **La ruta se calcula dos veces.** La app la calcula para mostrarla antes de iniciar, y el robot la vuelve a
  calcular al ejecutarla, porque la app puede tener datos viejos.
- **Las reglas de habilitación viven en el dominio** (`CommandRules`) y el robot vuelve a validar, por eso una
  orden habilitada todavía puede terminar "Rechazada".
- **Sin color dinámico (Material You).** Verde, ámbar y rojo tienen que significar lo mismo en cualquier teléfono,
  y siempre van acompañados de texto.
- **Room** guarda eventos y misiones. Hilt hace la inyección y la navegación es type-safe, igual que en el
  proyecto de referencia de la cátedra.

## Cinco riesgos y sus medidas

| Riesgo | Medida |
|---|---|
| **Personas cerca del robot** (patio con gente) | Confirmación "verificá que el recorrido esté despejado" al iniciar; alerta "persona cerca" con velocidad reducida a la mitad; propuesta de LED y aviso por voz (TTS) |
| **Órdenes accidentales** | Diálogo de confirmación para cancelar, detener, regresar y dejar la bolsa; botones deshabilitados con el motivo escrito; no se puede repetir una orden pendiente |
| **Pérdida de conexión** | Banner "datos antiguos" con su antigüedad; los comandos (salvo la detención) se deshabilitan; las órdenes sin respuesta se marcan a los 5 s; desconectarse de la app no detiene ni cancela al robot (se avisa) |
| **Confundir "enviada" con "hecha"** | Estados separados (pendiente, confirmada, rechazada con motivo, sin respuesta); el resultado real se lee en los pasos de la misión |
| **Privacidad** | La cámara solo se abre a pedido y solo lee QR (ML Kit offline, no se guardan imágenes); el historial no guarda datos de personas |

Otros riesgos contemplados: batería crítica (el robot se detiene solo una vez y queda disponible "regresar"),
tacho lleno (el robot espera y el operador decide) y la bolsa dejada en el piso (se avisa que hay que informar
al personal de limpieza).

## Limitaciones conocidas

- El escaneo con cámara (CameraX + ML Kit) es un adaptador fino que no tiene test automático: se verifica a
  mano con los QR de `docs/qr/`. En el emulador se usa "Simular lectura".
- Las duraciones de la macro, el consumo de batería y la velocidad (0,5 m/s) son valores plausibles, no medidos
  en un G1.
- Un solo robot simulado y un solo plano (patio de comidas de 9 × 5,5 m, celdas de 0,5 m).
- Los obstáculos del simulador quedan en el plano hasta que se quitan desde la pestaña Simulador.
