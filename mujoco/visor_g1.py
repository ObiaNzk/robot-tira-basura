"""Visor MuJoCo de Robot Tira Basura.

La app (simulador del APK) manda su telemetría a POST /estado y esta ventana la reproduce con el
modelo oficial del G1: camina por el patio, recoge la bolsa, levanta la tapa del tacho y la deposita.
Es una visualización cinemática (no hay física ni controlador de caminata). No controla ningún robot.

Uso, desde esta carpeta y con un Python que tenga mujoco instalado (en macOS, mjpython):
    mjpython visor_g1.py [--g1 RUTA/g1_29dof.xml] [--puerto 8766]

En el emulador de Android, la app llega a este visor con http://10.0.2.2:8766/.
"""

import argparse
import json
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import mujoco
import mujoco.viewer

import escena

estado = {"ultimo": None, "recibido": 0.0}
lock = threading.Lock()
viewer_activo = threading.Event()
MAX_BYTES = 64 * 1024


class Receptor(BaseHTTPRequestHandler):
    def _responder(self, codigo, contenido):
        payload = json.dumps(contenido).encode("utf-8")
        self.send_response(codigo)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def do_GET(self):
        if self.path != "/health":
            return self._responder(404, {"error": "Ruta desconocida"})
        activo = viewer_activo.is_set()
        self._responder(200 if activo else 503, {"viewer_running": activo})

    def do_POST(self):
        if self.path != "/estado":
            return self._responder(404, {"error": "Ruta desconocida"})
        try:
            size = int(self.headers.get("Content-Length", "0"))
            if not 0 < size <= MAX_BYTES:
                raise ValueError("Longitud inválida")
            datos = json.loads(self.rfile.read(size))
            if not isinstance(datos, dict) or "x_m" not in datos or "y_m" not in datos:
                raise ValueError("Faltan x_m / y_m")
            float(datos["x_m"]), float(datos["y_m"])
        except (ValueError, TypeError):
            return self._responder(400, {"error": "JSON inválido"})
        layout = datos.get("layout")
        if layout and layout != escena.LAYOUT and not getattr(self.server, "aviso_layout", False):
            print("Aviso: el plano de la app no coincide con escena.LAYOUT; las mesas pueden no coincidir.")
            self.server.aviso_layout = True
        with lock:
            estado["ultimo"] = datos
            estado["recibido"] = time.monotonic()
        self._responder(202, {"status": "accepted"})

    def log_message(self, *args):
        pass  # Dos mensajes por segundo: no llenar la consola.


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--g1", default=escena.DEFAULT_G1, help="Ruta a g1_29dof.xml de unitree_mujoco")
    parser.add_argument("--host", default="127.0.0.1", help="Solo loopback por defecto: no exponer a la red")
    parser.add_argument("--puerto", type=int, default=8766)
    args = parser.parse_args()

    model = escena.build_model(args.g1)
    data = mujoco.MjData(model)
    poser = escena.Poser(model, data)

    servidor = ThreadingHTTPServer((args.host, args.puerto), Receptor)
    threading.Thread(target=servidor.serve_forever, daemon=True).start()
    print(f"Visor escuchando en http://{args.host}:{args.puerto}/estado (emulador: http://10.0.2.2:{args.puerto}/)")

    try:
        with mujoco.viewer.launch_passive(model, data) as viewer:
            viewer_activo.set()
            viewer.cam.lookat[:] = [4.5, -2.75, 0.4]
            viewer.cam.distance = 9.5
            viewer.cam.azimuth = 90
            viewer.cam.elevation = -50
            anterior = time.monotonic()
            while viewer.is_running():
                ahora = time.monotonic()
                dt, anterior = ahora - anterior, ahora
                with lock:
                    datos = estado["ultimo"]
                    viejo = datos is not None and ahora - estado["recibido"] > 3.0
                with viewer.lock():
                    # Sin datos nuevos el robot se queda quieto donde estaba (como en la app: "datos antiguos").
                    poser.update(dict(datos, speed_mps=0.0) if viejo else datos, ahora, dt)
                viewer.sync()
                time.sleep(1 / 60)
    finally:
        viewer_activo.clear()
        servidor.shutdown()
        servidor.server_close()


if __name__ == "__main__":
    main()
