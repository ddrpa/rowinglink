"""BLE bridge — scan and connect to FTMS rowing machines via Bluetooth LE."""

import asyncio
import sys
from typing import Optional, Any

from bleak import BleakClient, BleakScanner
from bleak.backends.device import BLEDevice

from adapters.base import Adapter
from ftms_types import FTMSCharacteristic
from udp import UdpSender

# FTMS Service UUID (Fitness Machine Service)
FTMS_SERVICE_UUID = "00001826-0000-1000-8000-00805f9b34fb"

# Characteristic UUIDs (BLE standard format)
CHAR_UUIDS = {
    FTMSCharacteristic.INDOOR_BIKE_DATA.value: "00002ad1-0000-1000-8000-00805f9b34fb",
    FTMSCharacteristic.FITNESS_MACHINE_STATUS.value: "00002ada-0000-1000-8000-00805f9b34fb",
    FTMSCharacteristic.TRAINING_STATUS.value: "00002ad2-0000-1000-8000-00805f9b34fb",
}


async def scan_ftms(timeout: float = 10.0) -> list[BLEDevice]:
    """Scan nearby BLE devices and show connectable candidates."""
    print(f"Scanning nearby BLE devices ({timeout}s)...")
    discovered = await BleakScanner.discover(timeout=timeout, return_adv=True)

    connectable_devices: list[BLEDevice] = []
    unknown_connectable_devices: list[BLEDevice] = []
    non_connectable_count = 0

    for dev, adv in discovered.values():
        is_connectable = getattr(adv, "connectable", None)
        if is_connectable is True:
            connectable_devices.append(dev)
        elif is_connectable is None:
            # Some BLE backends do not expose connectable metadata.
            unknown_connectable_devices.append(dev)
        else:
            non_connectable_count += 1

    visible_devices = connectable_devices + unknown_connectable_devices
    if not visible_devices:
        print("No connectable BLE devices found.")
    else:
        print(f"Found {len(visible_devices)} candidate device(s):")
        for i, dev in enumerate(visible_devices):
            name = dev.name or "Unknown"
            print(f"  [{i}] {name} ({dev.address})")

    if non_connectable_count > 0:
        print(f"Skipped {non_connectable_count} non-connectable broadcaster(s).")

    return visible_devices


async def bridge(
    device_address: str,
    udp_sender: UdpSender,
    adapter: Adapter,
    verbose: bool = False,
) -> None:
    """Connect to a rowing machine and bridge FTMS data to UDP.

    Args:
        device_address: BLE device MAC address.
        udp_sender: UdpSender instance for forwarding packets.
        verbose: Print parsed data to stdout.
    """
    print(f"Connecting to {device_address}...")
    print(f"Using adapter: {adapter.adapter_id}")
    power_sum = 0
    power_samples = 0

    def _notification_handler(characteristic: Any, data: bytearray):
        """Handle BLE notification — forward raw bytes to UDP."""
        nonlocal power_sum, power_samples
        char_uuid = characteristic.uuid if hasattr(characteristic, 'uuid') else str(characteristic)
        char_uuid = char_uuid.lower()

        raw = bytes(data)
        converted = adapter.convert_notification(char_uuid, raw)
        if converted is None:
            return
        if converted.dropped_reason:
            if verbose:
                print(f"  [{char_uuid}] Dropped: {converted.dropped_reason} ({raw.hex()})")
            return

        udp_sender.send(converted.udp_uuid, converted.udp_payload)

        # Verbose: parse and print
        if verbose:
            if converted.power_sample is not None:
                power_sum += converted.power_sample
                power_samples += 1

            if converted.display_lines:
                print(f"  [0x2AD1] {'  '.join(converted.display_lines)}  FTMSLen: {len(converted.udp_payload)}B")
            elif converted.udp_uuid == FTMSCharacteristic.FITNESS_MACHINE_STATUS.value:
                opcode = data[0] if data else 0
                status_names = {4: "Started", 5: "Stopped", 6: "Started by User", 7: "Stopped by User"}
                status = status_names.get(opcode, f"Unknown (0x{opcode:02x})")
                print(f"  [0x2ADA] Machine Status: {status}")

    async with BleakClient(device_address) as client:
        print(f"Connected to {device_address}")
        print(f"Services: {[s.uuid for s in client.services]}")

        # Subscribe to all notifiable characteristics, same as showcase mode.
        subscribed = []
        for service in client.services:
            for char in service.characteristics:
                if "notify" in char.properties:
                    await client.start_notify(char, _notification_handler)
                    subscribed.append(char)
                    print(f"  Subscribed notify: {char.uuid} (handle {char.handle})")

        if not subscribed:
            print("Error: No notify characteristics found on device.")
            return

        print(f"\nBridge active. Forwarding to {udp_sender.host}:{udp_sender.port}")
        print("Press Ctrl+C to stop.\n")

        try:
            # Keep running until disconnected or Ctrl+C
            while client.is_connected:
                await asyncio.sleep(1)
        except asyncio.CancelledError:
            pass
        finally:
            for char in subscribed:
                try:
                    await client.stop_notify(char)
                except Exception:
                    pass
            print("\nBridge stopped.")
            if verbose and power_samples > 0:
                avg_power = power_sum / power_samples
                print(f"Session average power: {avg_power:.1f} W ({power_samples} samples)")
