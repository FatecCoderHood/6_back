import uuid
from app.utils.logger import logger


def try_parse_uuid(value: str) -> uuid.UUID | None:
    try:
        return uuid.UUID(value)
    except (ValueError, TypeError):
        logger.warning(f"parser::try_parse_uuid - Failed to parse UUID: {value}")
        return None