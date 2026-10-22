import re
import uuid

from starlette.datastructures import Headers, MutableHeaders
from starlette.types import ASGIApp, Message, Receive, Scope, Send

SESSION_HEADER = "x-session-id"
_VALID = re.compile(r"^[A-Za-z0-9_-]{8,64}$")


class SessionIdMiddleware:
    """Anonymous session: reuse a well-formed X-Session-Id or mint one, and echo it back.

    Pure ASGI (not BaseHTTPMiddleware) so streaming responses such as SSE are not buffered.
    """

    def __init__(self, app: ASGIApp) -> None:
        self.app = app

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        supplied = Headers(scope=scope).get(SESSION_HEADER, "")
        session_id = supplied if _VALID.match(supplied) else uuid.uuid4().hex
        scope.setdefault("state", {})["session_id"] = session_id

        async def send_with_header(message: Message) -> None:
            if message["type"] == "http.response.start":
                MutableHeaders(scope=message)["X-Session-Id"] = session_id
            await send(message)

        await self.app(scope, receive, send_with_header)
