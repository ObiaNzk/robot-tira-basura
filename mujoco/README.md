# Visor MuJoCo (opcional)

Muestra en 3D lo que hace el robot simulado del APK, con el modelo oficial del Unitree G1: camina por
el patio, recoge la bolsa, levanta la tapa del tacho, deposita la bolsa y vuelve al punto seguro.

**Es una visualización cinemática.** No corre física ni un controlador de caminata, y no controla
ningún robot: copia la telemetría que manda la app. El APK funciona igual sin el visor.

## Requisitos

- Python 3.10+ con `mujoco` (el entorno de `~/Downloads/sdk_emulator/.venv` ya lo tiene).
- El modelo del G1 de `unitree_mujoco` (`unitree_robots/g1/g1_29dof.xml`). Por defecto se busca en
  `~/Downloads/sdk_emulator/unitree_mujoco/`; si está en otro lado, pasalo con `--g1`.

## Uso

1. Terminal 1, levantar el visor (en macOS hay que usar `mjpython`):

   ```bash
   cd mujoco
   ~/Downloads/sdk_emulator/.venv/bin/mjpython visor_g1.py
   ```

2. En `local.properties` del proyecto Android, agregar la dirección del visor (el emulador ve la
   Mac como `10.0.2.2`) y reinstalar la app:

   ```properties
   robot.mujocoVisorUrl=http://10.0.2.2:8766/
   ```

3. En Android 17+ (API 37) la app necesita permiso de red local para llegar a `10.0.2.2`:

   ```bash
   adb shell pm grant com.example.robottirabasura android.permission.ACCESS_LOCAL_NETWORK
   ```

4. Abrir la app, conectarse al G1 e iniciar una misión: se ve en el teléfono y en MuJoCo a la vez.
   Si el visor no está abierto, la app sigue funcionando y solo deja un aviso en logcat (`VisorMirror`).

Sin esa línea la app no intenta conectarse a nada. Solo los builds debug pueden usar HTTP sin cifrar
y únicamente hacia `10.0.2.2`/`localhost`.

## Contrato

`POST /estado` (JSON, unas dos veces por segundo):

```json
{
  "x_m": 2.75, "y_m": 3.25, "heading_deg": 90, "speed_mps": 0.5,
  "state": "EN_MISION", "wait_reason": null, "step": "NAVEGAR_A_TACHO",
  "bag": "EN_MANO", "bags": [0.75, 4.75], "bin": [8.25, 0.75],
  "obstacles": [[8, 7]], "layout": ["..................", "..."]
}
```

- Posiciones en metros sobre el plano de la app (`y` crece hacia el sur); `obstacles` en celdas.
- `bag`: `EN_PISO`, `EN_MANO`, `EN_TACHO`, `JUNTO_AL_TACHO` o `null`.
- `GET /health` responde 200 mientras la ventana está abierta.

El visor escucha solo en `127.0.0.1` por defecto.
