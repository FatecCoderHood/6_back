import geopandas as gpd

gdf = gpd.read_file(
    "/home/rtrevizoli/Downloads/Enel_SP_390_2024-12-31_V11_20250926-0906.gdb",
    layer="SSDMT",
    engine="fiona"
)

print(gdf.head())