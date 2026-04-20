"""Adapter interfaces for converting vendor payloads to FTMS UDP packets."""

from dataclasses import dataclass
from typing import Optional, Protocol

from ftms_types import RowingData


@dataclass
class AdapterOutput:
    """One converted UDP packet emitted by an adapter."""

    udp_uuid: int
    udp_payload: bytes
    parsed: Optional[RowingData] = None
    dropped_reason: Optional[str] = None
    display_lines: Optional[list[str]] = None
    power_sample: Optional[int] = None


class Adapter(Protocol):
    """Adapter contract for vendor-specific BLE payload conversion."""

    adapter_id: str
    description: str

    def convert_notification(self, char_uuid: str, raw: bytes) -> Optional[AdapterOutput]:
        """Convert a BLE notification payload into UDP FTMS output."""
        ...


class SimulationCapableAdapter(Adapter, Protocol):
    """Optional protocol for adapters that can generate simulated packets."""

    def run_simulation(
        self,
        host: str,
        port: int,
        duration: Optional[float],
        interval: float,
        verbose: bool,
    ) -> None:
        """Run adapter-specific simulation and send UDP packets."""
        ...
