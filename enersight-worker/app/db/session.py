from sqlalchemy.orm import sessionmaker
from app.db.connection import get_engine

engine = get_engine()

SessionLocal = sessionmaker(
    bind=engine,
    autocommit=False,
    autoflush=False,
    expire_on_commit=False
)