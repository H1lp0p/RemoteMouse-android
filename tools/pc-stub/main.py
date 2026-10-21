"""
Remote Mouse PC stub — FastAPI + UDP discovery for LAN testing.
Implements REST /pair, /status and WebSocket /ws per project specs.
Shows a pairing QR window on startup; device_id persists across restarts.
"""

from __future__ import annotations

import asyncio
import json
import random
import socket
import threading
import time
import uuid
from contextlib import asynccontextmanager
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import qrcode
import uvicorn
from fastapi import FastAPI, Request, WebSocket, WebSocketDisconnect
from fastapi.responses import JSONResponse
from PIL import Image, ImageTk

import mouse_win

PORT = 12345
DEVICE_NAME = "Python Stub PC"
MAX_CLIENTS = 5
MAX_FAILED_ATTEMPTS = 5
RATE_LIMIT_SECONDS = 30
DEVICE_ID_FILE = Path(__file__).resolve().parent / ".device_id"


def load_or_create_device_id() -> str:
    if DEVICE_ID_FILE.exists():
        stored = DEVICE_ID_FILE.read_text(encoding="utf-8").strip()
        if stored:
            return stored
    device_id = str(uuid.uuid4())
    DEVICE_ID_FILE.write_text(device_id + "\n", encoding="utf-8")
    return device_id


def log(kind: str, direction: str, peer: str, payload: Any) -> None:
    if isinstance(payload, (dict, list)):
        body = json.dumps(payload, ensure_ascii=False)
    else:
        body = str(payload)
    print(f"[{kind}] {direction} {peer} {body}", flush=True)


async def apply_mouse(handler: Any, *args: Any, peer: str, detail: dict[str, Any]) -> None:
    """Run mouse emulation off the event loop; log success/failure."""
    try:
        await asyncio.to_thread(handler, *args)
        log("MOUSE", "OK ", peer, detail)
    except Exception as exc:
        log("MOUSE", "ERR", peer, {**detail, "error": str(exc)})


def local_ip() -> str:
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.connect(("8.8.8.8", 80))
        ip = sock.getsockname()[0]
        sock.close()
        return ip
    except OSError:
        return "127.0.0.1"


@dataclass
class TrustedClient:
    client_id: str
    client_name: str
    fingerprint: dict[str, Any]


@dataclass
class StubState:
    device_id: str = field(default_factory=load_or_create_device_id)
    verification_code: str = field(
        default_factory=lambda: f"{random.randint(0, 999999):06d}"
    )
    started_at: float = field(default_factory=time.time)
    pairing_enabled: bool = True
    trusted: dict[str, TrustedClient] = field(default_factory=dict)
    failed_attempts: dict[str, list[float]] = field(default_factory=dict)
    active_ws: set[WebSocket] = field(default_factory=set)
    # client_id of last successfully paired peer (for save without auth token)
    last_paired: dict[str, dict[str, Any]] = field(default_factory=dict)
    media_title: str = '[Youtube] "I created the most strange android app"'
    media_artist: str = "Code-bullet"
    media_position: int = 839  # 13:59
    media_duration: int = 2595  # 43:15
    media_playing: bool = True
    media_can_seek: bool = True
    volume_level: int = 50
    volume_muted: bool = False
    text_field: str = ""


state = StubState()
udp_transport: asyncio.DatagramTransport | None = None
media_subscribers: set[WebSocket] = set()
text_subscribers: set[WebSocket] = set()
_media_anchor_t: float = time.time()
_media_anchor_pos: int = state.media_position


def qr_payload() -> dict[str, Any]:
    return {
        "device_id": state.device_id,
        "device_name": DEVICE_NAME,
        "ip": local_ip(),
        "port": PORT,
        "verification_code": state.verification_code,
    }


def build_qr_image(payload: dict[str, Any], box_size: int = 8) -> Image.Image:
    qr = qrcode.QRCode(
        version=None,
        error_correction=qrcode.constants.ERROR_CORRECT_M,
        box_size=box_size,
        border=2,
    )
    qr.add_data(json.dumps(payload, ensure_ascii=False, separators=(",", ":")))
    qr.make(fit=True)
    return qr.make_image(fill_color="black", back_color="white").convert("RGB")


def open_qr_window() -> None:
    """Show pairing QR + connection info in a separate Tk window."""
    try:
        import tkinter as tk
        from tkinter import font as tkfont
    except Exception as exc:
        print(f"[QR  ] window unavailable: {exc}", flush=True)
        # Keep the process alive for headless / server-only use.
        try:
            while True:
                time.sleep(3600)
        except KeyboardInterrupt:
            return

    payload = qr_payload()
    image = build_qr_image(payload)

    root = tk.Tk()
    root.title("Remote Mouse — Pairing QR")
    root.resizable(False, False)

    frame = tk.Frame(root, padx=20, pady=16)
    frame.pack(fill="both", expand=True)

    title_font = tkfont.Font(family="Segoe UI", size=14, weight="bold")
    body_font = tkfont.Font(family="Consolas", size=11)

    tk.Label(frame, text=DEVICE_NAME, font=title_font).pack(anchor="w")
    tk.Label(
        frame,
        text=(
            f"device_id : {payload['device_id']}\n"
            f"address   : {payload['ip']}:{payload['port']}\n"
            f"CODE      : {payload['verification_code']}"
        ),
        font=body_font,
        justify="left",
        anchor="w",
    ).pack(anchor="w", pady=(8, 12))

    photo = ImageTk.PhotoImage(image)
    qr_label = tk.Label(frame, image=photo)
    qr_label.image = photo  # keep reference
    qr_label.pack()

    tk.Label(
        frame,
        text="Scan with the phone app (+ or QR-code).",
        font=tkfont.Font(family="Segoe UI", size=10),
        fg="#555555",
    ).pack(anchor="w", pady=(12, 0))

    root.protocol("WM_DELETE_WINDOW", root.destroy)
    root.mainloop()


def _effective_media_position() -> int:
    if state.media_playing:
        elapsed = int(time.time() - _media_anchor_t)
        return min(state.media_duration, max(0, _media_anchor_pos + elapsed))
    return state.media_position


def _commit_media_position(position: int | None = None, playing: bool | None = None) -> None:
    global _media_anchor_t, _media_anchor_pos
    pos = _effective_media_position() if position is None else position
    state.media_position = max(0, min(state.media_duration, pos))
    _media_anchor_pos = state.media_position
    _media_anchor_t = time.time()
    if playing is not None:
        state.media_playing = playing


def media_update_payload() -> dict[str, Any]:
    state.media_position = _effective_media_position()
    if state.media_playing and state.media_position >= state.media_duration:
        _commit_media_position(state.media_duration, playing=False)
    return {
        "type": "media_update",
        "title": state.media_title,
        "artist": state.media_artist,
        "position_seconds": state.media_position,
        "duration_seconds": state.media_duration,
        "is_playing": state.media_playing,
        "can_seek": state.media_can_seek,
    }


def volume_update_payload() -> dict[str, Any]:
    return {
        "type": "volume_update",
        "level": state.volume_level,
        "muted": state.volume_muted,
    }


def text_update_payload() -> dict[str, Any]:
    return {
        "type": "text_update",
        "text": state.text_field,
    }


async def broadcast_text_update(exclude: WebSocket | None = None) -> None:
    payload = text_update_payload()
    raw = json.dumps(payload)
    dead: list[WebSocket] = []
    for peer_ws in list(text_subscribers):
        if exclude is not None and peer_ws is exclude:
            continue
        peer = peer_ws.client.host if peer_ws.client else "?"
        try:
            await peer_ws.send_text(raw)
            log("WS  ", "OUT", peer, payload)
        except Exception:
            dead.append(peer_ws)
    for peer_ws in dead:
        text_subscribers.discard(peer_ws)
        state.active_ws.discard(peer_ws)


class UdpDiscoveryProtocol(asyncio.DatagramProtocol):
    def connection_made(self, transport: asyncio.BaseTransport) -> None:
        global udp_transport
        udp_transport = transport  # type: ignore[assignment]

    def datagram_received(self, data: bytes, addr: tuple[str, int]) -> None:
        peer = f"{addr[0]}:{addr[1]}"
        try:
            message = json.loads(data.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            log("UDP", "IN ", peer, f"invalid payload: {exc}")
            return

        log("UDP", "IN ", peer, message)
        if message.get("type") != "discovery":
            return

        target = message.get("target_device_id")
        if target and target != state.device_id:
            return

        client_id = message.get("client_id")
        is_trusted = bool(client_id and client_id in state.trusted)
        response = {
            "type": "discovery_response",
            "device_id": state.device_id,
            "device_name": DEVICE_NAME,
            "ip": local_ip(),
            "port": PORT,
            "is_trusted": is_trusted,
            "requires_pairing": not is_trusted,
        }
        payload = json.dumps(response).encode("utf-8")
        if udp_transport is not None:
            udp_transport.sendto(payload, addr)
            log("UDP", "OUT", peer, response)


def fingerprint_key(fp: dict[str, Any] | None) -> str:
    if not fp:
        return "unknown"
    return "|".join(
        str(fp.get(k, ""))
        for k in ("android_id", "model", "manufacturer", "os_version")
    )


def is_rate_limited(key: str) -> tuple[bool, int]:
    now = time.time()
    window = [t for t in state.failed_attempts.get(key, []) if now - t < RATE_LIMIT_SECONDS]
    state.failed_attempts[key] = window
    if len(window) >= MAX_FAILED_ATTEMPTS:
        retry = int(RATE_LIMIT_SECONDS - (now - window[0]))
        return True, max(retry, 1)
    return False, 0


def record_failure(key: str) -> None:
    state.failed_attempts.setdefault(key, []).append(time.time())


@asynccontextmanager
async def lifespan(app: FastAPI):
    loop = asyncio.get_running_loop()
    transport, _ = await loop.create_datagram_endpoint(
        UdpDiscoveryProtocol,
        local_addr=("0.0.0.0", PORT),
        allow_broadcast=True,
    )
    ip = local_ip()
    payload = qr_payload()
    print("=" * 60, flush=True)
    print("Remote Mouse Python stub is running", flush=True)
    print(f"  LAN IP     : {ip}", flush=True)
    print(f"  REST/WS    : http://{ip}:{PORT}  |  ws://{ip}:{PORT}/ws", flush=True)
    print(f"  UDP port   : {PORT}", flush=True)
    print(f"  device_id  : {state.device_id}", flush=True)
    print(f"  CODE       : {state.verification_code}", flush=True)
    print(f"  QR JSON    : {json.dumps(payload, ensure_ascii=False)}", flush=True)
    if mouse_win.is_available():
        print("  Mouse emu  : ON (Win32 SendInput)", flush=True)
    else:
        print("  Mouse emu  : OFF (non-Windows host)", flush=True)
    print("Scan the QR window or use UDP discovery + code on the phone.", flush=True)
    print("=" * 60, flush=True)
    try:
        yield
    finally:
        transport.close()


app = FastAPI(title="Remote Mouse PC Stub", lifespan=lifespan)


@app.get("/status")
async def status(request: Request):
    peer = request.client.host if request.client else "?"
    log("REST", "IN ", peer, {"method": "GET", "path": "/status"})
    body = {
        "version": "0.1.0-stub",
        "uptime_seconds": int(time.time() - state.started_at),
        "connected_clients": len(state.active_ws),
        "max_clients": MAX_CLIENTS,
        "is_pairing_enabled": state.pairing_enabled,
    }
    log("REST", "OUT", peer, body)
    return body


@app.post("/pair")
async def pair(request: Request):
    peer = request.client.host if request.client else "?"
    payload = await request.json()
    log("REST", "IN ", peer, payload)

    if not state.pairing_enabled and payload.get("pairing_mode") == "new":
        body = {"error": "pairing_disabled", "message": "New connections are blocked"}
        log("REST", "OUT", peer, body)
        return JSONResponse(status_code=403, content=body)

    mode = payload.get("pairing_mode")
    device_id = payload.get("device_id")
    client_id = payload.get("client_id")
    client_name = payload.get("client_name") or "Android"
    fingerprint = payload.get("fingerprint") or {}
    rate_key = f"{peer}|{fingerprint_key(fingerprint)}"

    limited, retry_after = is_rate_limited(rate_key)
    if limited:
        body = {
            "error": "rate_limited",
            "message": "Too many failed attempts",
            "retry_after_seconds": retry_after,
        }
        log("REST", "OUT", peer, body)
        return JSONResponse(
            status_code=429,
            content=body,
            headers={"Retry-After": str(retry_after)},
        )

    if device_id != state.device_id:
        body = {"error": "unknown_device", "message": "device_id mismatch"}
        log("REST", "OUT", peer, body)
        return JSONResponse(status_code=401, content=body)

    if mode == "new":
        code = str(payload.get("verification_code") or "")
        if code != state.verification_code:
            record_failure(rate_key)
            body = {
                "error": "invalid_code",
                "message": "Invalid verification code",
            }
            log("REST", "OUT", peer, body)
            return JSONResponse(status_code=401, content=body)
        state.last_paired[peer] = {
            "client_id": client_id,
            "client_name": client_name,
            "fingerprint": fingerprint,
        }
        body = {
            "paired": True,
            "server_info": {"name": DEVICE_NAME, "version": "0.1.0-stub"},
        }
        log("REST", "OUT", peer, body)
        return body

    if mode == "saved":
        trusted = state.trusted.get(client_id or "")
        if not trusted:
            body = {
                "error": "device_not_trusted",
                "message": "This client_id is not in the trusted devices list",
            }
            log("REST", "OUT", peer, body)
            return JSONResponse(status_code=401, content=body)
        state.last_paired[peer] = {
            "client_id": client_id,
            "client_name": client_name,
            "fingerprint": fingerprint,
        }
        body = {
            "paired": True,
            "server_info": {"name": DEVICE_NAME, "version": "0.1.0-stub"},
        }
        log("REST", "OUT", peer, body)
        return body

    body = {"error": "bad_request", "message": f"Unknown pairing_mode: {mode}"}
    log("REST", "OUT", peer, body)
    return JSONResponse(status_code=400, content=body)


@app.websocket("/ws")
async def websocket_endpoint(ws: WebSocket):
    await ws.accept()
    peer = ws.client.host if ws.client else "?"
    state.active_ws.add(ws)
    log("WS  ", "IN ", peer, {"event": "connected"})
    # Volume only; media is pushed on media_subscribe while Player is open.
    vol = volume_update_payload()
    await ws.send_text(json.dumps(vol))
    log("WS  ", "OUT", peer, vol)
    try:
        while True:
            raw = await ws.receive_text()
            try:
                message = json.loads(raw)
            except json.JSONDecodeError:
                log("WS  ", "IN ", peer, raw)
                err = {"type": "error", "code": 1001, "message": "Invalid JSON"}
                await ws.send_text(json.dumps(err))
                log("WS  ", "OUT", peer, err)
                continue

            log("WS  ", "IN ", peer, message)
            msg_type = message.get("type")

            if msg_type == "heartbeat":
                ack = {
                    "type": "heartbeat_ack",
                    "timestamp": message.get("timestamp"),
                }
                await ws.send_text(json.dumps(ack))
                log("WS  ", "OUT", peer, ack)
            elif msg_type == "save":
                paired = state.last_paired.get(peer, {})
                client_id = paired.get("client_id") or str(uuid.uuid4())
                client_name = message.get("client_name") or paired.get("client_name") or "Android"
                fingerprint = paired.get("fingerprint") or {}
                state.trusted[client_id] = TrustedClient(
                    client_id=client_id,
                    client_name=client_name,
                    fingerprint=fingerprint,
                )
                out = {
                    "type": "save-success",
                    "device_id": state.device_id,
                    "device_name": DEVICE_NAME,
                }
                await ws.send_text(json.dumps(out))
                log("WS  ", "OUT", peer, out)
                print(f"[TRUST] saved client_id={client_id} name={client_name}", flush=True)
            elif msg_type == "media_subscribe":
                media_subscribers.add(ws)
                out = media_update_payload()
                await ws.send_text(json.dumps(out))
                log("WS  ", "OUT", peer, out)
            elif msg_type == "media_unsubscribe":
                media_subscribers.discard(ws)
                log("WS  ", "OK ", peer, {"media_unsubscribe": True})
            elif msg_type == "media_play_pause":
                _commit_media_position(playing=not state.media_playing)
                out = media_update_payload()
                await ws.send_text(json.dumps(out))
                log("WS  ", "OUT", peer, out)
            elif msg_type == "media_next":
                state.media_title = '[Youtube] "Next track from stub"'
                state.media_artist = "Stub Artist"
                state.media_duration = 2595
                _commit_media_position(0, playing=True)
                out = media_update_payload()
                await ws.send_text(json.dumps(out))
                log("WS  ", "OUT", peer, out)
            elif msg_type == "media_prev":
                state.media_title = '[Youtube] "I created the most strange android app"'
                state.media_artist = "Code-bullet"
                state.media_duration = 2595
                _commit_media_position(0, playing=True)
                out = media_update_payload()
                await ws.send_text(json.dumps(out))
                log("WS  ", "OUT", peer, out)
            elif msg_type == "media_seek":
                delta = int(message.get("seconds") or 0)
                _commit_media_position(_effective_media_position() + delta)
                out = media_update_payload()
                await ws.send_text(json.dumps(out))
                log("WS  ", "OUT", peer, out)
            elif msg_type == "volume_set":
                state.volume_level = max(0, min(100, int(message.get("level") or 0)))
                state.volume_muted = False
                out = volume_update_payload()
                await ws.send_text(json.dumps(out))
                log("WS  ", "OUT", peer, out)
            elif msg_type == "volume_step":
                step = 5 if message.get("direction") == "up" else -5
                state.volume_level = max(0, min(100, state.volume_level + step))
                out = volume_update_payload()
                await ws.send_text(json.dumps(out))
                log("WS  ", "OUT", peer, out)
            elif msg_type == "volume_mute":
                state.volume_muted = bool(message.get("mute"))
                out = volume_update_payload()
                await ws.send_text(json.dumps(out))
                log("WS  ", "OUT", peer, out)
            elif msg_type == "text_subscribe":
                text_subscribers.add(ws)
                out = text_update_payload()
                await ws.send_text(json.dumps(out))
                log("WS  ", "OUT", peer, out)
            elif msg_type == "text_unsubscribe":
                text_subscribers.discard(ws)
                log("WS  ", "OK ", peer, {"text_unsubscribe": True})
            elif msg_type in ("text_set", "text_input"):
                state.text_field = str(message.get("text") or "")
                # Echo to sender + other Text-control subscribers for sync.
                out = text_update_payload()
                await ws.send_text(json.dumps(out))
                log("WS  ", "OUT", peer, out)
                await broadcast_text_update(exclude=ws)
            elif msg_type == "mouse_move":
                dx = int(message.get("dx") or 0)
                dy = int(message.get("dy") or 0)
                info: dict[str, Any] = {"accepted": "mouse_move", "dx": dx, "dy": dy}
                # Real RTT is measured on the phone (same clock); stub only prints the
                # previous sample piggybacked as prev_rtt_ms. Do NOT use stub_now - client_ts.
                prev_rtt = message.get("prev_rtt_ms")
                if prev_rtt is not None:
                    try:
                        info["rtt_ms"] = int(prev_rtt)
                    except (TypeError, ValueError):
                        info["prev_rtt_invalid"] = prev_rtt
                log("WS  ", "OK ", peer, info)
                await apply_mouse(
                    mouse_win.move_relative,
                    dx,
                    dy,
                    peer=peer,
                    detail={"op": "move", "dx": dx, "dy": dy},
                )
                ts = message.get("timestamp")
                if ts is not None:
                    try:
                        ack = {"type": "mouse_move_ack", "timestamp": int(ts)}
                        await ws.send_text(json.dumps(ack))
                        log("WS  ", "OUT", peer, ack)
                    except (TypeError, ValueError):
                        pass
            elif msg_type == "mouse_click":
                button = str(message.get("button") or "left")
                action = str(message.get("action") or "click")
                log("WS  ", "OK ", peer, {"accepted": "mouse_click", "button": button, "action": action})
                await apply_mouse(
                    mouse_win.button,
                    button,
                    action,
                    peer=peer,
                    detail={"op": "click", "button": button, "action": action},
                )
            elif msg_type == "mouse_scroll":
                delta_y = int(message.get("delta_y") or 0)
                delta_x = int(message.get("delta_x") or 0)
                log(
                    "WS  ",
                    "OK ",
                    peer,
                    {"accepted": "mouse_scroll", "delta_x": delta_x, "delta_y": delta_y},
                )
                await apply_mouse(
                    mouse_win.scroll,
                    delta_y,
                    delta_x,
                    peer=peer,
                    detail={"op": "scroll", "delta_x": delta_x, "delta_y": delta_y},
                )
            else:
                # Acknowledge other commands for observability; no-op on input emulation.
                log("WS  ", "OK ", peer, {"accepted": msg_type})
    except WebSocketDisconnect:
        log("WS  ", "IN ", peer, {"event": "disconnected"})
    finally:
        media_subscribers.discard(ws)
        text_subscribers.discard(ws)
        state.active_ws.discard(ws)


def main() -> None:
    # Uvicorn on a background thread; Tk QR window owns the main thread (Windows-friendly).
    server = threading.Thread(
        target=lambda: uvicorn.run(app, host="0.0.0.0", port=PORT, log_level="warning"),
        name="uvicorn",
        daemon=True,
    )
    server.start()
    open_qr_window()


if __name__ == "__main__":
    main()
