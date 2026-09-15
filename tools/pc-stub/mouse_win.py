"""Windows mouse emulation for the PC stub (SendInput). Mouse only."""

from __future__ import annotations

import sys
from typing import Literal

Button = Literal["left", "right", "middle"]
ClickAction = Literal["down", "up", "click", "double_click"]

_ENABLED = sys.platform == "win32"

if _ENABLED:
    import ctypes
    from ctypes import wintypes

    ULONG_PTR = ctypes.c_ulonglong if ctypes.sizeof(ctypes.c_void_p) == 8 else ctypes.c_ulong

    class MOUSEINPUT(ctypes.Structure):
        _fields_ = (
            ("dx", wintypes.LONG),
            ("dy", wintypes.LONG),
            ("mouseData", wintypes.DWORD),
            ("dwFlags", wintypes.DWORD),
            ("time", wintypes.DWORD),
            ("dwExtraInfo", ULONG_PTR),
        )

    class INPUT(ctypes.Structure):
        class _I(ctypes.Union):
            _fields_ = (("mi", MOUSEINPUT),)

        _anonymous_ = ("_input",)
        _fields_ = (
            ("type", wintypes.DWORD),
            ("_input", _I),
        )

    _SendInput = ctypes.windll.user32.SendInput
    _SendInput.argtypes = (wintypes.UINT, ctypes.POINTER(INPUT), ctypes.c_int)
    _SendInput.restype = wintypes.UINT

    INPUT_MOUSE = 0
    MOUSEEVENTF_MOVE = 0x0001
    MOUSEEVENTF_LEFTDOWN = 0x0002
    MOUSEEVENTF_LEFTUP = 0x0004
    MOUSEEVENTF_RIGHTDOWN = 0x0008
    MOUSEEVENTF_RIGHTUP = 0x0010
    MOUSEEVENTF_MIDDLEDOWN = 0x0020
    MOUSEEVENTF_MIDDLEUP = 0x0040
    MOUSEEVENTF_WHEEL = 0x0800
    MOUSEEVENTF_HWHEEL = 0x01000
    WHEEL_DELTA = 120

    _BUTTON_FLAGS: dict[Button, tuple[int, int]] = {
        "left": (MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_LEFTUP),
        "right": (MOUSEEVENTF_RIGHTDOWN, MOUSEEVENTF_RIGHTUP),
        "middle": (MOUSEEVENTF_MIDDLEDOWN, MOUSEEVENTF_MIDDLEUP),
    }

    def _send(*inputs: INPUT) -> None:
        n = len(inputs)
        arr = (INPUT * n)(*inputs)
        sent = _SendInput(n, arr, ctypes.sizeof(INPUT))
        if sent != n:
            raise OSError(f"SendInput sent {sent}/{n}")

    def _mouse_input(flags: int, dx: int = 0, dy: int = 0, data: int = 0) -> INPUT:
        inp = INPUT()
        inp.type = INPUT_MOUSE
        inp.mi = MOUSEINPUT(dx, dy, data, flags, 0, 0)
        return inp

    def move_relative(dx: int, dy: int) -> None:
        if dx == 0 and dy == 0:
            return
        _send(_mouse_input(MOUSEEVENTF_MOVE, dx=dx, dy=dy))

    def button(button: Button, action: ClickAction) -> None:
        down, up = _BUTTON_FLAGS[button]
        if action == "down":
            _send(_mouse_input(down))
        elif action == "up":
            _send(_mouse_input(up))
        elif action == "click":
            _send(_mouse_input(down), _mouse_input(up))
        elif action == "double_click":
            _send(
                _mouse_input(down),
                _mouse_input(up),
                _mouse_input(down),
                _mouse_input(up),
            )
        else:
            raise ValueError(f"Unknown click action: {action}")

    def scroll(delta_y: int = 0, delta_x: int = 0) -> None:
        # Spec: positive delta_y = up. Win32 wheel: positive = away from user (up).
        events: list[INPUT] = []
        if delta_y:
            events.append(_mouse_input(MOUSEEVENTF_WHEEL, data=int(delta_y)))
        if delta_x:
            events.append(_mouse_input(MOUSEEVENTF_HWHEEL, data=int(delta_x)))
        if events:
            _send(*events)

else:

    def move_relative(dx: int, dy: int) -> None:
        raise RuntimeError("Mouse emulation requires Windows")

    def button(button: Button, action: ClickAction) -> None:
        raise RuntimeError("Mouse emulation requires Windows")

    def scroll(delta_y: int = 0, delta_x: int = 0) -> None:
        raise RuntimeError("Mouse emulation requires Windows")


def is_available() -> bool:
    return _ENABLED
