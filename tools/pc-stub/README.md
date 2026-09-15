# Remote Mouse PC stub

Minimal FastAPI server for LAN testing of the Android client.

## Run

```bash
cd tools/pc-stub
python -m venv .venv
# Windows:
.venv\Scripts\activate
pip install -r requirements.txt
python main.py
```

On start the console prints LAN IP, `device_id`, and a **6-digit CODE**.

Allow **TCP/UDP 12345** in Windows Firewall for private networks.

## What it does

- UDP discovery on port `12345`
- `GET /status`, `POST /pair`
- WebSocket `/ws` (`heartbeat`, `save`, and logs all other commands)
- **Virtual mouse** on Windows via `SendInput`: `mouse_move`, `mouse_click`, `mouse_scroll`
  (keyboard / text are still no-op)

Phone and PC must be on the same Wi‑Fi.
