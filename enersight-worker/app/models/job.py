import uuid
from sqlalchemy import Enum as SQLEnum
from app.enums.ingestion_status import IngestionStatus
from sqlalchemy import String, Text, Integer
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base

class IngestionJob(Base):
    __tablename__ = "ingestion_jobs"
    __table_args__ = {"schema": "staging"}

    job_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True)
    org_id: Mapped[str] = mapped_column(String(16))
    title: Mapped[str] = mapped_column(String(255))
    description: Mapped[str] = mapped_column(Text)
    url: Mapped[str] = mapped_column(Text)

    ingestion_status: Mapped[IngestionStatus] = mapped_column(
        SQLEnum(IngestionStatus, name="ingestion_status_enum", values_callable=lambda enum: [e.value for e in enum]),
        default=IngestionStatus.PENDING,
        nullable=False
    )
    attempts: Mapped[int] = mapped_column(Integer)