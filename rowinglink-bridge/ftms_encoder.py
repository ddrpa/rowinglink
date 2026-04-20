"""Encode normalized rowing metrics into FTMS Indoor Bike Data payloads."""

import struct

from ftms_types import RowingData


def _clamp(value: int, minimum: int, maximum: int) -> int:
    return max(minimum, min(value, maximum))


def encode_indoor_bike_data(rowing: RowingData) -> bytes:
    """Build FTMS Indoor Bike Data (0x2AD1) value bytes from RowingData.

    Included fields:
    - Instantaneous Cadence (bit 2, uint16, 0.5 rpm)
    - Total Distance (bit 4, uint24, meter)
    - Instantaneous Power (bit 6, sint16, watt)
    - Expended Energy (bit 8, uint16 + uint16 + uint8)
    - Elapsed Time (bit 11, uint16, second)
    """
    flags = 0
    payload = bytearray()

    if rowing.instantaneous_cadence is not None:
        flags |= 1 << 2
        cadence_raw = _clamp(int(round(rowing.instantaneous_cadence * 2)), 0, 0xFFFF)
        payload += struct.pack("<H", cadence_raw)

    if rowing.total_distance is not None:
        flags |= 1 << 4
        dist = _clamp(int(rowing.total_distance), 0, 0xFFFFFF)
        payload += struct.pack("<I", dist)[:3]

    if rowing.instantaneous_power is not None:
        flags |= 1 << 6
        power = _clamp(int(rowing.instantaneous_power), -32768, 32767)
        payload += struct.pack("<h", power)

    if rowing.expended_energy is not None:
        flags |= 1 << 8
        total_energy = _clamp(int(rowing.expended_energy), 0, 0xFFFF)
        # Energy per hour / per minute are unknown from private payload.
        payload += struct.pack("<H", total_energy)
        payload += struct.pack("<H", 0)
        payload += struct.pack("<B", 0)

    if rowing.elapsed_time is not None:
        flags |= 1 << 11
        elapsed = _clamp(int(rowing.elapsed_time), 0, 0xFFFF)
        payload += struct.pack("<H", elapsed)

    return struct.pack("<H", flags) + bytes(payload)
