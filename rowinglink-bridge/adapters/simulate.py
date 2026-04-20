"""Simulation adapter that generates FTMS packets over UDP."""

import math
import struct
import time
from typing import Optional

from adapters.base import AdapterOutput
from ftms_types import FTMSCharacteristic
from udp import UdpSender


class SimulateAdapter:
    adapter_id = "simulate"
    description = "Built-in FTMS simulator adapter"
    min_power = 50
    max_power = 200
    stroke_rate = 30.0

    def convert_notification(self, char_uuid: str, raw: bytes) -> AdapterOutput | None:
        return None

    def _build_indoor_bike_data(
        self,
        power: int,
        cadence: float,
        total_distance: int,
        elapsed_time: float,
    ) -> bytes:
        flags = 0x0001 | 0x0004 | 0x0010 | 0x0040 | 0x0800
        speed_kmh = 2.8 * (power ** (1.0 / 3.0))
        speed_raw = int(speed_kmh / 0.01)
        cadence_raw = int(cadence)
        dist_raw = int(total_distance) & 0xFFFFFF
        elapsed_raw = int(elapsed_time / 0.25)

        data = struct.pack("<H", flags)
        data += struct.pack("<H", speed_raw)
        data += struct.pack("<H", cadence_raw)
        data += struct.pack("<I", dist_raw)[:3]
        data += struct.pack("<h", power)
        data += struct.pack("<H", elapsed_raw)
        return data

    def _build_machine_status(self, opcode: int) -> bytes:
        return struct.pack("<B", opcode)

    def run_simulation(
        self,
        host: str,
        port: int,
        duration: Optional[float],
        interval: float,
        verbose: bool,
    ) -> None:
        sender = UdpSender(host, port)
        min_power = self.min_power
        max_power = self.max_power
        stroke_rate = self.stroke_rate
        start_time = time.time()
        total_distance = 0

        sender.send(
            FTMSCharacteristic.FITNESS_MACHINE_STATUS.value,
            self._build_machine_status(0x04),
        )

        try:
            while True:
                elapsed = time.time() - start_time
                if duration is not None and elapsed >= duration:
                    break

                cycle_period = 60.0 / stroke_rate
                phase = (elapsed % cycle_period) / cycle_period * 2 * math.pi
                power_range = max_power - min_power
                power = int(min_power + power_range * (0.5 + 0.5 * math.sin(phase)))
                cadence = stroke_rate + math.sin(elapsed * 0.7) * 1.5

                raw = self._build_indoor_bike_data(
                    power, cadence, total_distance, elapsed
                )
                sender.send(FTMSCharacteristic.INDOOR_BIKE_DATA.value, raw)

                speed_kmh = 2.8 * (power ** (1.0 / 3.0))
                total_distance += speed_kmh / 3.6 * interval

                if verbose:
                    print(
                        f"[{elapsed:6.1f}s] "
                        f"Power: {power:3d}W  "
                        f"Cadence: {cadence:4.1f} spm  "
                        f"Speed: {speed_kmh:4.1f} km/h  "
                        f"Dist: {int(total_distance)}m"
                    )

                time.sleep(interval)
        except KeyboardInterrupt:
            pass
        finally:
            sender.send(
                FTMSCharacteristic.FITNESS_MACHINE_STATUS.value,
                self._build_machine_status(0x07),
            )
            sender.close()
            if verbose:
                total_elapsed = time.time() - start_time
                print(
                    f"\nSimulation ended. Duration: {total_elapsed:.1f}s, "
                    f"Distance: {int(total_distance)}m"
                )


def create_adapter() -> SimulateAdapter:
    return SimulateAdapter()
