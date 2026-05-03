import subprocess
from app.config.settings import settings
from app.utils.logger import logger

def load_to_postgis(gdb_path: str, table: str):
    logger.info("loader::load_to_postgis - Starting loader...")

    conn_str = (
        f"PG:host=localhost "
        f"dbname={settings.DB_NAME} "
        f"user={settings.DB_USER} "
        f"password={settings.DB_PASSWORD}"
    )

    cmd = [
        "ogr2ogr",
        "-f", "PostgreSQL",
        conn_str,
        gdb_path,
        "SSDMT",
        "-nln", f"staging.{table}",
        "-overwrite",
        "-progress",
        "-gt", "65536"
    ]

    logger.info(f"loader::load_to_postgis - Running command: {' '.join(cmd)}")

    result = subprocess.run(cmd, capture_output=True, text=True)
    
    logger.info(f"loader::load_to_postgis - STDOUT:\n{result.stdout}")
    logger.info(f"loader::load_to_postgis - STDERR:\n{result.stderr}")

    result.check_returncode()

    logger.info("loader::load_to_postgis - Loader completed.")

if __name__ == "__main__":
    load_to_postgis(r"/home/rtrevizoli/Downloads/Enel_SP_390_2024-12-31_V11_20250926-0906.gdb", "enel_sp_ssdmt")