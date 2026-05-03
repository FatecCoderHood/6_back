from pydantic import BaseModel, HttpUrl
from typing import Optional
from uuid import UUID

class IngestionJob(BaseModel):
    job_id: UUID
    org_id: str
    title: str
    description: Optional[str]
    url: HttpUrl