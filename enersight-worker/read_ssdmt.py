import geopandas as gpd
import os
import fiona
# /home/rtrevizoli/Downloads/Enel_SP_390_2024-12-31_V11_20250926-0906.gdb
dir_extracao = "/home/rtrevizoli/Downloads"
nome_gdb = "Enel_SP_390_2024-12-31_V11_20250926-0906.gdb"
caminho_gdb = os.path.join(dir_extracao, nome_gdb)

# A camada que você especificou para carregar
camada_para_carregar = 'SSDMT'

print(f"Tentando carregar a camada '{camada_para_carregar}' do GeoDatabase: {caminho_gdb}")

# Carregar a camada específica do GeoDatabase
try:
    gdf_ssdmt = gpd.read_file(caminho_gdb, layer=camada_para_carregar)
    print(f"Camada '{camada_para_carregar}' carregada com sucesso!")
    display(gdf_ssdmt.head())
except Exception as e:
    print(f"Erro ao carregar a camada '{camada_para_carregar}': {e}")
    print(f"Verificando as camadas disponíveis no GDB: {fiona.listlayers(caminho_gdb)}")