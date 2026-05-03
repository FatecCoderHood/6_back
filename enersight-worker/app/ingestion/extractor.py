import zipfile
from pathlib import Path
from app.utils.logger import logger

def extract(file_path: Path, dest: Path) -> Path:
    logger.info(f"extractor::extract - Extracting {file_path} to {dest}")
    if file_path.suffix == ".zip":
        with zipfile.ZipFile(file_path, 'r') as zip_ref:
            zip_ref.extractall(dest)
        return dest

    logger.info(f"extractor::extract - File {file_path} is not a zip file")
    return file_path