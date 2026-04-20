"""Core data types for FTMS rowing machine data."""

from dataclasses import dataclass
from typing import Optional
from enum import Enum


class MachineState(Enum):
    """Fitness machine operational state."""
    UNKNOWN = 0
    STARTED = 4
    STOPPED = 5
    STARTED_BY_USER = 6
    STOPPED_BY_USER = 7


class FTMSCharacteristic(Enum):
    """FTMS GATT characteristic UUIDs."""
    INDOOR_BIKE_DATA = 0x2AD1
    TRAINING_STATUS = 0x2AD2
    FITNESS_MACHINE_STATUS = 0x2ADA


@dataclass
class RowingData:
    """Parsed FTMS rowing machine data.
    
    All fields are optional except timestamp and machine_state.
    Numeric fields use appropriate types (float for decimals, int for whole numbers).
    """
    timestamp: int  # milliseconds since epoch
    instantaneous_speed: Optional[float] = None  # km/h
    average_speed: Optional[float] = None  # km/h
    instantaneous_cadence: Optional[float] = None  # rpm
    average_cadence: Optional[float] = None  # rpm
    total_distance: Optional[int] = None  # meters
    resistance_level: Optional[float] = None
    instantaneous_power: Optional[int] = None  # watts
    average_power: Optional[int] = None  # watts
    expended_energy: Optional[int] = None  # kcal
    metabolic_equivalent: Optional[float] = None  # METs
    elapsed_time: Optional[float] = None  # seconds
    remaining_time: Optional[float] = None  # seconds
    machine_state: MachineState = MachineState.UNKNOWN
