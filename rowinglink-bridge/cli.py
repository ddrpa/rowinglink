"""RowingLink CLI — adapter-driven BLE/UDP runner."""

import argparse
import asyncio
import sys

from adapters import list_adapters, load_adapter
from adapters.base import SimulationCapableAdapter
from udp import UdpSender


def _print_adapter_list() -> None:
    names = list_adapters()
    if not names:
        print("No adapters found under adapters/.")
        return
    print("Available adapters:")
    for name in names:
        print(f"  - {name}")


def cmd_scan(args: argparse.Namespace) -> None:
    """Scan BLE devices."""
    from ble import scan_ftms

    asyncio.run(scan_ftms(timeout=args.scan_timeout))


def cmd_run(args: argparse.Namespace) -> None:
    """Run selected adapter in bridge or simulation mode."""
    from ble import scan_ftms, bridge

    if args.list_adapters:
        _print_adapter_list()
        return

    if args.scan:
        asyncio.run(scan_ftms(timeout=args.scan_timeout))
        return

    if not args.adapter:
        print("Error: --adapter is required. Use --list-adapters to view options.")
        sys.exit(1)

    try:
        adapter = load_adapter(args.adapter)
    except ValueError as exc:
        print(f"Error: {exc}")
        sys.exit(1)

    if args.device:
        udp_sender = UdpSender(host=args.host, port=args.port)
        if args.verbose:
            print(f"Mode: bridge")
            print(f"UDP target: {args.host}:{args.port}")
            print(f"Adapter: {adapter.adapter_id}")
        try:
            asyncio.run(bridge(args.device, udp_sender, adapter=adapter, verbose=args.verbose))
        except KeyboardInterrupt:
            print("\nInterrupted.")
        finally:
            udp_sender.close()
        return

    if not hasattr(adapter, "run_simulation"):
        print(
            "Error: --device not provided, but adapter does not support simulation mode."
        )
        print("Provide --device for BLE bridge mode, or choose a simulation-capable adapter.")
        sys.exit(1)

    sim_adapter = adapter  # type: SimulationCapableAdapter
    if args.verbose:
        print(f"Mode: simulation")
        print(f"UDP target: {args.host}:{args.port}")
        print(f"Adapter: {args.adapter}")
        if args.duration:
            print(f"Duration: {args.duration}s")
        print(f"Interval: {args.interval}s ({1/args.interval:.0f} Hz)")
        print()
    try:
        sim_adapter.run_simulation(
            host=args.host,
            port=args.port,
            duration=args.duration,
            interval=args.interval,
            verbose=args.verbose,
        )
    except KeyboardInterrupt:
        print("\nInterrupted.")


def main() -> None:
    parser = argparse.ArgumentParser(prog="RowingLink", description="BLE-UDP bridge")
    subparsers = parser.add_subparsers(dest="command", required=True)

    # --- run ---
    run_parser = subparsers.add_parser("run", help="Run selected adapter")
    run_parser.add_argument("--host", default="127.0.0.1", help="UDP target host (default: 127.0.0.1)")
    run_parser.add_argument("--port", type=int, default=19840, help="UDP target port (default: 19840)")
    run_parser.add_argument("--scan", action="store_true", help="Scan nearby BLE devices and exit")
    run_parser.add_argument("--scan-timeout", type=float, default=10.0, help="Scan duration in seconds (default: 10)")
    run_parser.add_argument("--adapter", type=str, help="Adapter id under adapters/ (required)")
    run_parser.add_argument("--list-adapters", action="store_true", help="List available adapters and exit")
    run_parser.add_argument("--device", type=str, help="BLE device address; if omitted, tries simulation mode")
    run_parser.add_argument("--duration", type=float, default=None, help="Simulation duration in seconds (default: infinite)")
    run_parser.add_argument("--interval", type=float, default=0.1, help="Simulation packet interval in seconds (default: 0.1)")
    run_parser.add_argument("-v", "--verbose", action="store_true", help="Print runtime logs")
    run_parser.set_defaults(func=cmd_run)

    # --- scan ---
    scan_parser = subparsers.add_parser("scan", help="Scan nearby BLE devices")
    scan_parser.add_argument("--scan-timeout", type=float, default=10.0, help="Scan duration in seconds (default: 10)")
    scan_parser.set_defaults(func=cmd_scan)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
