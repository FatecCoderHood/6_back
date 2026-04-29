import subprocess
from app.config.settings import settings

def load_to_postgis(gdb_path: str, table: str):
    conn_str = (
        f"PG:host={settings.DB_HOST} "
        f"dbname={settings.DB_NAME} "
        f"user={settings.DB_USER} "
        f"password={settings.DB_PASSWORD}"
    )

    cmd = [
        "ogr2ogr",
        "-f", "PostgreSQL",
        conn_str,
        gdb_path,
        "-nln", f"staging.{table}",
        "-append",
        "-progress",
        "-gt", "65536"
    ]

    subprocess.run(cmd, check=True)