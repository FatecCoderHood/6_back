from app.ingestion.pipeline import IngestionPipeline

def run(source: str):
    pipeline = IngestionPipeline()
    pipeline.run(source)