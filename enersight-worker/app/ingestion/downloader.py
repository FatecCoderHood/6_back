import re
import requests
from pathlib import Path
from app.utils.logger import logger

def get_filename_from_response(response, url: str) -> str:
    content_disposition = response.headers.get("Content-Disposition")

    if content_disposition:
        match = re.search(r'filename="([^"]+)"', content_disposition)
        if match:
            return match.group(1)

    # fallback
    return url.split("/")[-1]


def download(url: str, dest: Path) -> Path:
    dest.mkdir(parents=True, exist_ok=True)

    with requests.get(url, stream=True, headers={"User-Agent": "Mozilla/5.0", "Accept": "*/*",}) as r:
        logger.debug(f"downloader::download - Content-Length: {r.headers.get('Content-Length')}")
        logger.debug(f"downloader::download - Content-Type: {r.headers.get('Content-Type')}")
        r.raise_for_status()

        filename = get_filename_from_response(r, url)
        file_path = dest / filename

        logger.info(f"downloader::download - Saving file as: {file_path}")

        with open(file_path, "wb") as f:
            for chunk in r.iter_content(chunk_size=8192):
                if chunk:
                    f.write(chunk)

    return file_path