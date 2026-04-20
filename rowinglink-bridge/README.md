# RowingLink

BLE-UDP bridge for FTMS rowing machines to RowingLink mod.

## Install

```bash
pip install -r requirements.txt
```

## Usage

All commands run from inside the RowingLink folder:

```bash
python3 __main__.py <command> [options]
```

### run — Run selected adapter

```bash
# List available adapters
python3 __main__.py run --list-adapters

# Optional: scan nearby BLE devices
python3 __main__.py scan
# or
python3 __main__.py run --scan

# Bridge mode (with BLE device)
python3 __main__.py run --adapter mok-k10 --device AA:BB:CC:DD:EE:FF

# Simulation mode (no --device, requires simulation-capable adapter)
python3 __main__.py run --adapter simulate --duration 60 -v
```

| Option | Default | Description |
|---|---|---|
| `--host` | 127.0.0.1 | UDP target host |
| `--port` | 19840 | UDP target port |
| `--adapter` | | Adapter id under `adapters/` (required) |
| `--device` | | BLE device address; omit for simulation mode |
| `--duration` | infinite | Run duration (seconds) |
| `--interval` | 0.1 | Packet interval (seconds) |
| `--scan` | | Scan and exit |
| `--scan-timeout` | 10.0 | Scan duration (seconds) |
| `--list-adapters` | | List adapters and exit |

#### Adapter Architecture

- No default adapter is used. You must pass `--adapter`.
- Built-in adapters: `mok-k10`, `simulate`.
- To add a custom adapter:
  - Create exactly one file per adapter under `adapters/` (for example `my_vendor.py`).
  - Expose `create_adapter()` and return an object implementing `convert_notification(char_uuid, raw)`.
  - If needed, also implement `run_simulation(...)` for simulation mode.
  - Start with `--adapter my-vendor` (file stem with `_` mapped to `-`).

Simulation profile parameters (power/cadence/etc.) are adapter-owned and should be edited inside the adapter implementation.
