"""Escena y poses del visor MuJoCo de Robot Tira Basura.

Es una VISUALIZACIÓN cinemática: no corre mj_step ni física, no hay controlador de caminata.
La app (el simulador del APK) decide dónde está el robot y qué paso ejecuta; este módulo solo
lo dibuja: mueve la base del G1, anima piernas y brazos y ubica bolsa, tacho y obstáculos.
"""

import math
import os

import mujoco

# Debe coincidir con SiteMap.DEMO del APK (la app manda su plano y el visor avisa si difiere).
LAYOUT = [
    "..................",
    ".S......##........",
    "........##....##..",
    "..###...##....##..",
    "..###.........##..",
    "..........###.....",
    "....##....###.....",
    "....##............",
    "..........##..###.",
    "..##......##..###.",
    "..................",
]
CELL = 0.5
MAX_OBSTACLES = 12
HIDDEN = [0.0, 0.0, -5.0]

DEFAULT_G1 = os.path.expanduser(
    "~/Downloads/sdk_emulator/unitree_mujoco/unitree_robots/g1/g1_29dof.xml"
)

MANIPULATION = {"RECOGER_BOLSA", "LEVANTAR_TAPA", "DEPOSITAR_BOLSA", "BAJAR_TAPA"}
STEP_SECONDS = {"RECOGER_BOLSA": 3.0, "LEVANTAR_TAPA": 2.5, "DEPOSITAR_BOLSA": 2.5, "BAJAR_TAPA": 2.0}


def world(x_m, y_m):
    """Plano de la app (y crece hacia el sur) → mundo MuJoCo (Y hacia el norte)."""
    return x_m, -y_m


def yaw_from_heading(heading_deg):
    """Rumbo de brújula de la app (0 = norte, 90 = este) → yaw de MuJoCo (0 = +X = este)."""
    return math.radians(90.0 - heading_deg)


def build_model(g1_path=DEFAULT_G1, layout=LAYOUT):
    spec = mujoco.MjSpec.from_file(g1_path)
    width, height = len(layout[0]) * CELL, len(layout) * CELL
    world_body = spec.worldbody

    spec.visual.global_.offwidth = 1920
    spec.visual.global_.offheight = 1080
    spec.visual.headlight.diffuse = [0.6, 0.6, 0.6]
    spec.visual.headlight.ambient = [0.35, 0.35, 0.35]
    floor_tex = spec.add_texture(
        name="baldosas", type=mujoco.mjtTexture.mjTEXTURE_2D, builtin=mujoco.mjtBuiltin.mjBUILTIN_CHECKER,
        rgb1=[0.82, 0.80, 0.76], rgb2=[0.74, 0.72, 0.68], width=256, height=256,
    )
    floor_mat = spec.add_material(name="piso", texrepeat=[width / CELL / 2, height / CELL / 2], reflectance=0.05)
    floor_mat.textures[mujoco.mjtTextureRole.mjTEXROLE_RGB] = floor_tex.name
    world_body.add_light(pos=[width / 2, -height / 2, 6], dir=[0, 0, -1], type=mujoco.mjtLightType.mjLIGHT_DIRECTIONAL, diffuse=[0.5, 0.5, 0.5])
    world_body.add_geom(
        name="piso", type=mujoco.mjtGeom.mjGEOM_PLANE, size=[width / 2 + 1, height / 2 + 1, 0.05],
        pos=[width / 2, -height / 2, 0], material="piso",
    )

    # Mesas: un bloque por celda ocupada del plano.
    for y, row in enumerate(layout):
        for x, char in enumerate(row):
            cx, cy = world((x + 0.5) * CELL, (y + 0.5) * CELL)
            if char == "#":
                world_body.add_geom(
                    type=mujoco.mjtGeom.mjGEOM_BOX, size=[CELL / 2 - 0.01, CELL / 2 - 0.01, 0.37],
                    pos=[cx, cy, 0.37], rgba=[0.55, 0.38, 0.22, 1],
                )
            elif char == "S":
                world_body.add_geom(
                    type=mujoco.mjtGeom.mjGEOM_BOX, size=[CELL / 2 - 0.02, CELL / 2 - 0.02, 0.005],
                    pos=[cx, cy, 0.005], rgba=[0.2, 0.55, 0.65, 1], contype=0, conaffinity=0,
                )

    def mocap(name, geoms):
        body = world_body.add_body(name=name, mocap=True, pos=HIDDEN)
        for g in geoms:
            body.add_geom(contype=0, conaffinity=0, **g)

    mocap("bolsa", [
        dict(type=mujoco.mjtGeom.mjGEOM_ELLIPSOID, size=[0.13, 0.13, 0.17], rgba=[0.08, 0.08, 0.08, 1]),
        dict(type=mujoco.mjtGeom.mjGEOM_SPHERE, size=[0.035], pos=[0, 0, 0.18], rgba=[0.12, 0.12, 0.12, 1]),
    ])
    mocap("tacho", [
        dict(type=mujoco.mjtGeom.mjGEOM_CYLINDER, size=[0.2, 0.32], pos=[0, 0, 0.32], rgba=[0.15, 0.45, 0.25, 1]),
    ])
    # La tapa gira sobre su borde trasero: el cuerpo está en la bisagra y el disco desplazado.
    mocap("tapa", [
        dict(type=mujoco.mjtGeom.mjGEOM_CYLINDER, size=[0.21, 0.015], pos=[0.21, 0, 0], rgba=[0.1, 0.35, 0.18, 1]),
    ])
    mocap("marca_bolsas", [dict(type=mujoco.mjtGeom.mjGEOM_BOX, size=[0.12, 0.12, 0.003], rgba=[0.55, 0.43, 0.39, 1])])
    for i in range(MAX_OBSTACLES):
        mocap(f"obstaculo_{i}", [
            dict(type=mujoco.mjtGeom.mjGEOM_BOX, size=[0.2, 0.2, 0.45], pos=[0, 0, 0.45], rgba=[0.85, 0.15, 0.1, 0.9]),
        ])
    return spec.compile()


class Poser:
    """Calcula la pose del G1 y de los objetos a partir del último estado recibido de la app."""

    def __init__(self, model, data):
        self.m, self.d = model, data
        self.qadr = {
            mujoco.mj_id2name(model, mujoco.mjtObj.mjOBJ_JOINT, j): model.jnt_qposadr[j] for j in range(model.njnt)
        }
        self.ranges = {
            mujoco.mj_id2name(model, mujoco.mjtObj.mjOBJ_JOINT, j): model.jnt_range[j] for j in range(model.njnt)
        }
        self.mocap = {
            name: model.body_mocapid[mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_BODY, name)]
            for name in ["bolsa", "tacho", "tapa", "marca_bolsas"] + [f"obstaculo_{i}" for i in range(MAX_OBSTACLES)]
        }
        self.hand = mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_BODY, "right_wrist_yaw_link")
        self.feet = [mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_BODY, n) for n in ("left_ankle_roll_link", "right_ankle_roll_link")]
        self.base_z = 0.793
        self.x, self.y, self.yaw = None, None, 0.0
        self.phase = 0.0
        self.step = None
        self.step_started = 0.0
        self.joints = {}
        # Altura de los pies con las piernas estiradas: referencia para apoyar al robot en el piso.
        self._set_joints({})
        self._place_base(0, 0, 0, self.base_z)
        mujoco.mj_kinematics(model, data)
        self.foot_ref = min(data.xpos[f][2] for f in self.feet)

    # region helpers

    def _set_joints(self, targets):
        for name, adr in self.qadr.items():
            if name == "floating_base_joint":
                continue
            lo, hi = self.ranges[name]
            value = targets.get(name, 0.0)
            self.d.qpos[adr] = min(max(value, lo + 0.01), hi - 0.01) if hi > lo else value

    def _place_base(self, x, y, yaw, z):
        adr = self.qadr["floating_base_joint"]
        self.d.qpos[adr:adr + 3] = [x, y, z]
        self.d.qpos[adr + 3:adr + 7] = [math.cos(yaw / 2), 0, 0, math.sin(yaw / 2)]

    def _put(self, name, pos, quat=(1, 0, 0, 0)):
        i = self.mocap[name]
        self.d.mocap_pos[i] = pos
        self.d.mocap_quat[i] = quat

    # endregion

    def update(self, state, now, dt):
        """Aplica un estado (dict con el JSON de la app) a qpos y a los objetos. Llamar con el lock tomado."""
        if state is None:
            self._set_joints(_neutral())
            self._place_base(*world(0.75, 0.75), 0.0, self.base_z)
            return

        step = state.get("step")
        if step != self.step:
            self.step, self.step_started = step, now
        elapsed = now - self.step_started
        progress = min(elapsed / STEP_SECONDS.get(step, 1.0), 1.0)
        waiting_full = state.get("state") == "ESPERANDO_OPERADOR" and state.get("wait_reason") == "TACHO_LLENO"
        manipulating = step in MANIPULATION and state.get("state") in ("EN_MISION", "ESPERANDO_OPERADOR", "PAUSADO", "DETENIDO")

        # Base: se acerca suavemente a la posición informada (la app manda 2 veces por segundo).
        tx, ty = world(state["x_m"], state["y_m"])
        target_yaw = yaw_from_heading(state.get("heading_deg", 90))
        if manipulating:
            # El robot y el objeto comparten celda en la app: acá se para medio metro antes, mirándolo.
            tx -= 0.42 * math.cos(target_yaw)
            ty -= 0.42 * math.sin(target_yaw)
        if self.x is None:
            self.x, self.y, self.yaw = tx, ty, target_yaw
        k = min(1.0, dt * 6.0)
        self.x += (tx - self.x) * k
        self.y += (ty - self.y) * k
        self.yaw += _angle_diff(target_yaw, self.yaw) * min(1.0, dt * 8.0)

        speed = state.get("speed_mps", 0.0)
        carrying = state.get("bag") == "EN_MANO"

        # Postura según lo que hace.
        if speed > 0.01:
            self.phase += dt * 2 * math.pi * (1.1 + 2.0 * speed)
            pose = _walk(self.phase, carrying)
        elif step == "RECOGER_BOLSA" and manipulating:
            pose = _blend(_neutral(), _crouch_reach(), _bump(progress, 0.15, 0.55, 0.85))
            if progress > 0.6:
                pose = _merge(pose, _carry_arm(), _ramp(progress, 0.6, 0.95))
        elif step == "LEVANTAR_TAPA" and manipulating:
            pose = _merge(_carry_arm() if carrying else _neutral(), _lift_lid_arm(), _ramp(progress, 0.0, 0.6))
        elif step == "DEPOSITAR_BOLSA" and manipulating:
            pose = _merge(_lift_lid_arm(), _drop_arm(), _bump(progress, 0.1, 0.5, 0.9))
            if progress < 0.5:
                pose = _merge(pose, _carry_arm(), 1 - _ramp(progress, 0.1, 0.5))
        elif step == "BAJAR_TAPA" and manipulating:
            pose = _blend(_lift_lid_arm(), _neutral(), _ramp(progress, 0.2, 1.0))
        else:
            pose = _carry_arm() if carrying else _neutral()
            pose = _merge(_neutral(), pose, 1.0)
            self.phase = 0.0
        self._set_joints(pose)

        # Apoyar los pies en el piso: bajar la base lo que se acortaron las piernas.
        self._place_base(self.x, self.y, self.yaw, self.base_z)
        mujoco.mj_kinematics(self.m, self.d)
        lowest = min(self.d.xpos[f][2] for f in self.feet)
        self._place_base(self.x, self.y, self.yaw, self.base_z - (lowest - self.foot_ref))
        mujoco.mj_kinematics(self.m, self.d)

        self._place_objects(state, step, progress, manipulating, waiting_full)
        # Los cuerpos mocap recién movidos necesitan otra pasada para que su posición quede al día.
        mujoco.mj_kinematics(self.m, self.d)

    def _place_objects(self, state, step, progress, manipulating, waiting_full):
        bags, bin_ = state.get("bags"), state.get("bin")
        bag = state.get("bag")

        if bags:
            bx, by = world(*bags)
            self._put("marca_bolsas", [bx, by, 0.004])
        else:
            self._put("marca_bolsas", HIDDEN)

        if bin_:
            cx, cy = world(*bin_)
            self._put("tacho", [cx, cy, 0])
            # Bisagra del lado opuesto al robot para que la tapa se abra alejándose de él.
            hinge_yaw = math.atan2(self.y - cy, self.x - cx)
            hx, hy = cx - 0.21 * math.cos(hinge_yaw), cy - 0.21 * math.sin(hinge_yaw)
            if waiting_full:
                opening = 1.0
            elif step == "LEVANTAR_TAPA" and manipulating:
                opening = _ramp(progress, 0.2, 0.9)
            elif step == "DEPOSITAR_BOLSA" and manipulating:
                opening = 1.0
            elif step == "BAJAR_TAPA" and manipulating:
                opening = 1 - _ramp(progress, 0.2, 0.9)
            else:
                opening = 0.0
            pitch = -opening * math.radians(105)
            self._put("tapa", [hx, hy, 0.655], _quat_yaw_pitch(hinge_yaw, pitch))
        else:
            self._put("tacho", HIDDEN)
            self._put("tapa", HIDDEN)

        hand = self.d.xpos[self.hand]
        if bag == "EN_PISO" and bags:
            bx, by = world(*bags)
            if step == "RECOGER_BOLSA" and manipulating and progress > 0.55:
                self._put("bolsa", [hand[0], hand[1], hand[2] - 0.2])
            else:
                self._put("bolsa", [bx, by, 0.17])
        elif bag == "EN_MANO":
            if step == "DEPOSITAR_BOLSA" and manipulating and progress > 0.6 and bin_:
                cx, cy = world(*bin_)
                fall = _ramp(progress, 0.6, 0.9)
                self._put("bolsa", [cx, cy, 0.75 - 0.45 * fall])
            else:
                self._put("bolsa", [hand[0], hand[1], hand[2] - 0.2])
        elif bag == "JUNTO_AL_TACHO" and bin_:
            cx, cy = world(*bin_)
            self._put("bolsa", [cx + 0.35 * math.cos(self.yaw + 1.6), cy + 0.35 * math.sin(self.yaw + 1.6), 0.17])
        else:
            self._put("bolsa", HIDDEN)

        obstacles = state.get("obstacles") or []
        for i in range(MAX_OBSTACLES):
            if i < len(obstacles):
                ox, oy = world((obstacles[i][0] + 0.5) * CELL, (obstacles[i][1] + 0.5) * CELL)
                self._put(f"obstaculo_{i}", [ox, oy, 0])
            else:
                self._put(f"obstaculo_{i}", HIDDEN)


# region poses (radianes; valores elegidos a ojo, no son trayectorias del robot real)
# Convenciones del G1 verificadas con renders: pitch negativo de hombro/cadera = hacia adelante;
# codo en 0 = antebrazo horizontal (90°), valores positivos lo estiran hacia abajo.

def _neutral():
    return {
        "left_shoulder_roll_joint": 0.2, "right_shoulder_roll_joint": -0.2,
        "left_elbow_joint": 1.25, "right_elbow_joint": 1.25,
        "left_hip_pitch_joint": -0.1, "right_hip_pitch_joint": -0.1,
        "left_knee_joint": 0.25, "right_knee_joint": 0.25,
        "left_ankle_pitch_joint": -0.15, "right_ankle_pitch_joint": -0.15,
    }


def _walk(phase, carrying):
    s = math.sin(phase)
    pose = _neutral()
    pose.update({
        "left_hip_pitch_joint": -0.1 - 0.4 * s,
        "right_hip_pitch_joint": -0.1 + 0.4 * s,
        "left_knee_joint": 0.25 + 0.55 * max(0.0, s),
        "right_knee_joint": 0.25 + 0.55 * max(0.0, -s),
        "left_shoulder_pitch_joint": 0.35 * s,
        "right_shoulder_pitch_joint": -0.35 * s,
    })
    pose["left_ankle_pitch_joint"] = -0.5 * (pose["left_knee_joint"] + pose["left_hip_pitch_joint"])
    pose["right_ankle_pitch_joint"] = -0.5 * (pose["right_knee_joint"] + pose["right_hip_pitch_joint"])
    return _merge(pose, _carry_arm(), 1.0) if carrying else pose


def _crouch_reach():
    return {
        "left_hip_pitch_joint": -1.0, "right_hip_pitch_joint": -1.0,
        "left_knee_joint": 1.7, "right_knee_joint": 1.7,
        "left_ankle_pitch_joint": -0.7, "right_ankle_pitch_joint": -0.7,
        "waist_pitch_joint": 0.45,
        "left_shoulder_pitch_joint": -0.7, "right_shoulder_pitch_joint": -0.7,
        "left_shoulder_roll_joint": 0.15, "right_shoulder_roll_joint": -0.15,
        "left_elbow_joint": 1.3, "right_elbow_joint": 1.3,
    }


def _carry_arm():
    return {"right_shoulder_pitch_joint": -0.3, "right_shoulder_roll_joint": -0.1, "right_elbow_joint": 0.25}


def _lift_lid_arm():
    return {"left_shoulder_pitch_joint": -1.5, "left_shoulder_roll_joint": 0.25, "left_elbow_joint": 0.9}


def _drop_arm():
    return {"right_shoulder_pitch_joint": -1.2, "right_shoulder_roll_joint": 0.1, "right_elbow_joint": 0.8}

# endregion


def _merge(base, overlay, w):
    out = dict(base)
    for k, v in overlay.items():
        out[k] = out.get(k, 0.0) * (1 - w) + v * w
    return out


def _blend(a, b, w):
    keys = set(a) | set(b)
    return {k: a.get(k, 0.0) * (1 - w) + b.get(k, 0.0) * w for k in keys}


def _ramp(x, start, end):
    if x <= start:
        return 0.0
    if x >= end:
        return 1.0
    t = (x - start) / (end - start)
    return t * t * (3 - 2 * t)


def _bump(x, rise, hold, fall):
    """0 → 1 entre rise y hold, 1 hasta fall, después vuelve a 0 hacia el final."""
    if x < hold:
        return _ramp(x, rise, hold)
    return 1.0 - _ramp(x, fall, 1.0)


def _angle_diff(a, b):
    return (a - b + math.pi) % (2 * math.pi) - math.pi


def _quat_yaw_pitch(yaw, pitch):
    cy, sy = math.cos(yaw / 2), math.sin(yaw / 2)
    cp, sp = math.cos(pitch / 2), math.sin(pitch / 2)
    return [cy * cp, -sy * sp, cy * sp, sy * cp]
