from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict
from sqlalchemy import URL


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    db_host: str = "127.0.0.1"
    db_port: int = 5432
    db_user: str = "odyssey"
    db_password: str = ""
    db_name: str = "odyssey"
    db_schema: str = "odyssey"

    llm_provider: str | None = None
    llm_model: str | None = None
    llm_api_key: str | None = None
    llm_base_url: str | None = None
    llm_temperature: float = 0.2
    llm_timeout_s: float = 120.0
    planning_timeout_s: float = Field(default=90.0, gt=0, le=300)
    research_timeout_s: float = Field(default=600.0, gt=0, le=3600)

    kiwi_mcp_url: str = "https://mcp.kiwi.com"
    trivago_mcp_url: str = "https://mcp.trivago.com/mcp"

    google_maps_api_key: str | None = None
    google_maps_mcp_url: str = "https://mapstools.googleapis.com/mcp"

    @property
    def database_url(self) -> URL:
        # URL.create escapes the password, so characters such as '@' are safe.
        return URL.create(
            "postgresql+psycopg",
            username=self.db_user,
            password=self.db_password,
            host=self.db_host,
            port=self.db_port,
            database=self.db_name,
        )


@lru_cache
def get_settings() -> Settings:
    return Settings()
