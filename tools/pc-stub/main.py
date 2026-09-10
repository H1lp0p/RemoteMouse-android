"""
Remote Mouse PC stub — FastAPI + UDP discovery for LAN testing.
Implements REST /pair, /status and WebSocket /ws per project specs.
"""

from __future__ import annotations

import asyncio
import json
import random
import socket
import time
import uuid
from contextlib import asynccontextmanager
from dataclasses import dataclass, field
from typing import Any

import uvicorn
from fastapi import FastAPI, Request, WebSocket, WebSocketDisconnect
from fastapi.responses import JSONResponse

PORT = 12345
DEVICE_NAME = "Python Stub PC"
MAX_CLIENTS = 5
MAX_FAILED_ATTEMPTS = 5
RATE_LIMIT_SECONDS = 30


def log(kind: str, direction: str, peer: str, payload: Any) -> None:
    if isinstance(payload, (dict, list)):
        body = json.dumps(payload, ensure_ascii=False)
    else:
        body = str(payload)
    print(f"[{kind}] {direction} {peer} {body}", flush=True)


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
    device_id: str = field(default_factory=lambda: str(uuid.uuid4()))
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


state = StubState()
udp_transport: asyncio.DatagramTransport | None = None
media_subscribers: set[WebSocket] = set()
_media_anchor_t: float = time.time()
_media_anchor_pos: int = state.media_position


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
    print("=" * 60, flush=True)
    print("Remote Mouse Python stub is running", flush=True)
    print(f"  LAN IP     : {ip}", flush=True)
    print(f"  REST/WS    : http://{ip}:{PORT}  |  ws://{ip}:{PORT}/ws", flush=True)
    print(f"  UDP port   : {PORT}", flush=True)
    print(f"  device_id  : {state.device_id}", flush=True)
    print(f"  CODE       : {state.verification_code}", flush=True)
    print("Open the debug connect screen on the phone and tap Search.", flush=True)
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
            else:
                # Acknowledge other commands for observability; no-op on input emulation.
                log("WS  ", "OK ", peer, {"accepted": msg_type})
    except WebSocketDisconnect:
        log("WS  ", "IN ", peer, {"event": "disconnected"})
    finally:
        media_subscribers.discard(ws)
        state.active_ws.discard(ws)


def main() -> None:
    uvicorn.run(app, host="0.0.0.0", port=PORT, log_level="warning")


if __name__ == "__main__":
    main()
