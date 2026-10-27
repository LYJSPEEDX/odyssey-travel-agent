"""Minimal MCP client for remote Streamable-HTTP servers.

Written directly on httpx rather than the official SDK: the servers we use answer either plain
JSON or an SSE stream, and a thin client keeps both cases (and errors) fully under our control.
"""

import json
from dataclasses import dataclass
from typing import Any

import httpx

PROTOCOL_VERSION = "2025-06-18"


class McpError(RuntimeError):
    """The MCP server or the transport failed."""


@dataclass
class McpToolResult:
    text: str
    structured: dict | None
    is_error: bool


class McpClient:
    def __init__(
        self,
        url: str,
        *,
        headers: dict[str, str] | None = None,
        timeout: float = 60.0,
        client_name: str = "odyssey-backend",
    ) -> None:
        self.url = url
        self._timeout = timeout
        self._client_name = client_name
        self._headers = {
            "content-type": "application/json",
            "accept": "application/json, text/event-stream",
            **(headers or {}),
        }
        self._http: httpx.AsyncClient | None = None
        self._next_id = 0

    async def __aenter__(self) -> "McpClient":
        self._http = httpx.AsyncClient(timeout=self._timeout)
        try:
            result = await self._request(
                "initialize",
                {
                    "protocolVersion": PROTOCOL_VERSION,
                    "capabilities": {},
                    "clientInfo": {"name": self._client_name, "version": "0.1.0"},
                },
            )
            self._headers["mcp-protocol-version"] = result.get("protocolVersion", PROTOCOL_VERSION)
            await self._notify("notifications/initialized")
        except BaseException:
            await self._http.aclose()
            raise
        return self

    async def __aexit__(self, *exc_info: object) -> None:
        if self._http:
            await self._http.aclose()
            self._http = None

    async def list_tools(self) -> list[dict]:
        return (await self._request("tools/list", {})).get("tools", [])

    async def call_tool(self, name: str, arguments: dict[str, Any]) -> McpToolResult:
        result = await self._request("tools/call", {"name": name, "arguments": arguments})
        text = "\n".join(
            part.get("text", "") for part in result.get("content", []) if part.get("type") == "text"
        )
        return McpToolResult(
            text=text,
            structured=result.get("structuredContent"),
            is_error=bool(result.get("isError")),
        )

    async def _post(self, payload: dict) -> httpx.Response:
        assert self._http is not None, "use 'async with McpClient(...)'"
        last: Exception | None = None
        for _ in range(2):  # one retry for transport hiccups
            try:
                response = await self._http.post(self.url, headers=self._headers, json=payload)
            except httpx.TransportError as exc:
                last = exc
                continue
            if response.status_code >= 500:
                last = McpError(f"{self.url} returned HTTP {response.status_code}")
                continue
            if response.status_code >= 400:
                raise McpError(
                    f"{self.url} returned HTTP {response.status_code}: {response.text[:200]}"
                )
            if sid := response.headers.get("mcp-session-id"):
                self._headers["mcp-session-id"] = sid
            return response
        raise McpError(f"request to {self.url} failed: {last}") from last

    async def _notify(self, method: str) -> None:
        await self._post({"jsonrpc": "2.0", "method": method})

    async def _request(self, method: str, params: dict) -> dict:
        self._next_id += 1
        request_id = self._next_id
        response = await self._post(
            {"jsonrpc": "2.0", "id": request_id, "method": method, "params": params}
        )
        message = _find_response(response, request_id)
        if "error" in message:
            err = message["error"]
            raise McpError(f"{method} failed ({err.get('code')}): {err.get('message')}")
        return message.get("result", {})


def _find_response(response: httpx.Response, request_id: int) -> dict:
    """The reply is either a JSON body or an SSE stream of JSON-RPC messages."""
    if "text/event-stream" in response.headers.get("content-type", ""):
        for line in response.text.splitlines():
            if line.startswith("data:"):
                try:
                    message = json.loads(line[5:])
                except json.JSONDecodeError:
                    continue
                if isinstance(message, dict) and message.get("id") == request_id:
                    return message
        raise McpError("no matching response in event stream")
    try:
        message = response.json()
    except json.JSONDecodeError as exc:
        raise McpError(f"response is not JSON: {response.text[:200]}") from exc
    if not isinstance(message, dict):
        raise McpError("unexpected response shape")
    return message
