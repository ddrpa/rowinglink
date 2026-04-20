"""Adapter registry and dynamic loader."""

from importlib import import_module
from pathlib import Path
from typing import Dict, List

from adapters.base import Adapter


def _adapter_modules() -> Dict[str, str]:
    modules: Dict[str, str] = {}
    adapters_dir = Path(__file__).resolve().parent
    for path in adapters_dir.glob("*.py"):
        if path.stem in {"__init__", "base"}:
            continue
        modules[path.stem.replace("_", "-")] = f"adapters.{path.stem}"
    return modules


def list_adapters() -> List[str]:
    """Return adapter ids discovered under adapters/."""
    return sorted(_adapter_modules().keys())


def load_adapter(adapter_id: str) -> Adapter:
    """Load adapter by id (for example: mok-k10)."""
    modules = _adapter_modules()
    module_name = modules.get(adapter_id)
    if module_name is None:
        known = ", ".join(list_adapters()) or "(none)"
        raise ValueError(f"Unknown adapter '{adapter_id}'. Available: {known}")

    module = import_module(module_name)
    if not hasattr(module, "create_adapter"):
        raise ValueError(
            f"Adapter module '{module_name}' must expose create_adapter()."
        )
    adapter = module.create_adapter()
    return adapter
