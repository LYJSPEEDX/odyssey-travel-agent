"""Regenerate sql/init.sql from the SQLAlchemy models.

uv run python -m scripts.gen_init_sql
"""

from pathlib import Path

from sqlalchemy.dialects import postgresql
from sqlalchemy.schema import CreateIndex, CreateTable

from app.config import get_settings
from app.models import Base

HEADER = """-- Odyssey backend: initial schema (PostgreSQL 13+).
-- Generated from app/models.py by scripts/gen_init_sql.py -- regenerate, do not hand-edit.
-- Idempotent: safe to run more than once.

"""


def main() -> None:
    dialect = postgresql.dialect()
    schema = get_settings().db_schema
    parts = [f'CREATE SCHEMA IF NOT EXISTS "{schema}";']
    for table in Base.metadata.sorted_tables:
        ddl = str(CreateTable(table, if_not_exists=True).compile(dialect=dialect)).strip()
        parts.append(ddl + ";")
        for index in sorted(table.indexes, key=lambda i: i.name):
            ddl = str(CreateIndex(index, if_not_exists=True).compile(dialect=dialect)).strip()
            parts.append(ddl + ";")
    out = Path(__file__).resolve().parent.parent / "sql" / "init.sql"
    out.write_text(HEADER + "\n\n".join(parts) + "\n")
    print(f"wrote {out}")


if __name__ == "__main__":
    main()
