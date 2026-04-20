"""Adapter for MOK K10 private rowing notifications."""

import time
from dataclasses import dataclass
from typing import Optional

from adapters.base import AdapterOutput
from ftms_encoder import encode_indoor_bike_data
from ftms_types import MachineState, RowingData

FTMS_INDOOR_BIKE_DATA_UUID = 0x2AD1


@dataclass
class MokK10Metrics:
    spm: int
    distance_decameter: int
    distance_meter: int
    duration_seconds: int
    calories_tenth_kcal: int
    calories_kcal: float
    strokes: int
    power: int
    level: int
    drag: int
    pace_500m_seconds: Optional[float]


def parse_mok_k10_payload(data: bytes) -> Optional[MokK10Metrics]:
    """Parse MOK K10 fixed-size 20-byte payload."""
    if len(data) != 20:
        return None

    spm = data[4]
    distance_decameter = (data[10] << 8) | data[11]
    distance_meter = distance_decameter * 10
    duration_seconds = (data[13] << 8) | data[12]
    calories_tenth_kcal = (data[5] << 8) | data[14]
    calories_kcal = calories_tenth_kcal * 0.1
    strokes = (data[8] << 8) | data[9]
    power = data[16]
    level = data[15]
    drag = data[3]

    pace_500m_seconds = None
    if distance_meter > 0:
        pace_500m_seconds = duration_seconds / distance_meter * 500

    return MokK10Metrics(
        spm=spm,
        distance_decameter=distance_decameter,
        distance_meter=distance_meter,
        duration_seconds=duration_seconds,
        calories_tenth_kcal=calories_tenth_kcal,
        calories_kcal=calories_kcal,
        strokes=strokes,
        power=power,
        level=level,
        drag=drag,
        pace_500m_seconds=pace_500m_seconds,
    )


def to_rowing_data(metrics: MokK10Metrics) -> RowingData:
    """Map mok-k10 private metrics into common rowing fields."""
    timestamp = int(time.time() * 1000)
    return RowingData(
        timestamp=timestamp,
        instantaneous_cadence=float(metrics.spm),
        total_distance=metrics.distance_meter,
        instantaneous_power=metrics.power,
        expended_energy=int(metrics.calories_kcal),
        elapsed_time=float(metrics.duration_seconds),
        machine_state=MachineState.UNKNOWN,
    )


def format_metrics(metrics: MokK10Metrics) -> list[str]:
    lines = [
        f"Spm: {metrics.spm} spm",
        f"Distance: {metrics.distance_meter} m",
        f"Distance Raw: {metrics.distance_decameter} x10m",
        f"Duration: {metrics.duration_seconds} s",
        f"Calories: {metrics.calories_kcal:.1f} kcal",
        f"Calories Raw: {metrics.calories_tenth_kcal} x0.1kcal",
        f"Strokes: {metrics.strokes} count",
        f"Power: {metrics.power} W",
        f"Level: {metrics.level}",
        f"Drag: {metrics.drag}",
    ]
    if metrics.pace_500m_seconds is not None:
        lines.append(f"Pace (500m): {metrics.pace_500m_seconds:.2f} s")
    return lines


class MokK10Adapter:
    adapter_id = "mok-k10"
    description = "MOK K10 20-byte private payload -> FTMS Indoor Bike Data"

    def convert_notification(self, char_uuid: str, raw: bytes) -> AdapterOutput | None:
        metrics = parse_mok_k10_payload(raw)
        if metrics is None:
            return AdapterOutput(
                udp_uuid=FTMS_INDOOR_BIKE_DATA_UUID,
                udp_payload=b"",
                dropped_reason="invalid mok-k10 payload",
            )
        parsed = to_rowing_data(metrics)

        return AdapterOutput(
            udp_uuid=FTMS_INDOOR_BIKE_DATA_UUID,
            udp_payload=encode_indoor_bike_data(parsed),
            parsed=parsed,
            display_lines=format_metrics(metrics),
            power_sample=metrics.power,
        )


def create_adapter() -> MokK10Adapter:
    return MokK10Adapter()
