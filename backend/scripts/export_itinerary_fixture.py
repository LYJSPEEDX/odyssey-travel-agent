"""Export exact deterministic assembler output for Android compatibility tests."""

import json
import sys
from pathlib import Path


def main():
    root = Path(__file__).resolve().parents[2]
    sys.path.insert(0, str(root / "backend/tests"))
    from test_itinerary_contract import assembled

    target = root / "docs/itinerary-contract/examples/backend-generated-frontend-v2.json"
    target.write_text(json.dumps(assembled(), indent=2) + "\n")
    print(f"Exported {target}")


if __name__ == "__main__":
    main()
