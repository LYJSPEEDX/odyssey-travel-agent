"""Copy the canonical schema into the deployable backend runtime."""

from pathlib import Path


def main():
    root = Path(__file__).resolve().parents[2]
    source = root / "docs/itinerary-contract/frontend-itinerary-v2.schema.json"
    target = root / "backend/app/contracts/frontend-itinerary-v2.schema.json"
    target.write_bytes(source.read_bytes())
    print(f"Synced {target}")


if __name__ == "__main__":
    main()
