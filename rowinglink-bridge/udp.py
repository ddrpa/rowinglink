"""UDP sender for forwarding FTMS packets to the RowingLink mod."""

import struct
import socket


class UdpSender:
    """Sends FTMS packets with 2-byte UUID header to the mod's UDP port."""

    def __init__(self, host: str = "127.0.0.1", port: int = 19840):
        self._host = host
        self._port = port
        self._sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)

    def send(self, uuid: int, raw_data: bytes) -> None:
        """Pack 2-byte LE UUID header + raw characteristic value and send."""
        packet = struct.pack('<H', uuid) + raw_data
        self._sock.sendto(packet, (self._host, self._port))

    def close(self) -> None:
        self._sock.close()

    @property
    def host(self) -> str:
        return self._host

    @property
    def port(self) -> int:
        return self._port
