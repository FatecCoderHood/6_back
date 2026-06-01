# To test:
## 1. Go to docker folder
```bash
cd ./backend 
```

## 2. Search mongo and mongo-express container name:
```bash
docker ps
```
<img width="1767" height="57" alt="image" src="https://github.com/user-attachments/assets/33597f78-e617-48f7-a51b-90cdc9b03bdb" />

## 3. Put up mongo image on docker
```bash
docker compose up -d enersight-mongo mongo-express
```

## 4. Download these files 
> Search in my drive [Indicadores de Continuidade](https://drive.google.com/file/d/1PIFDSSSO3jhJFOC7_twfx7egiLqlygNu/view?usp=drive_link).

* indicadores-continuidade-coletivos-2010-2019.csv
* indicadores-continuidade-coletivos-2020-2029.csv
* indicadores-continuidade-coletivos-atributos.csv

## 5. Follow the steps to upload the files in mongo:
In docker folder, make this commands:

### 5.1. Copy the files to docker path
#### indicadores-continuidade-coletivos-2010-2019.csv
```bash
docker cp indicadores-continuidade-coletivos-2010-2019.csv enersight-mongo:/indicadores-continuidade-coletivos-2010-2019.csv
```

#### indicadores-continuidade-coletivos-2020-2029.csv
```bash
docker cp indicadores-continuidade-coletivos-2020-2029.csv enersight-mongo:/indicadores-continuidade-coletivos-2020-2029.csv
```

#### indicadores-continuidade-coletivos-limite.csv
```bash
docker cp indicadores-continuidade-coletivos-limite.csv enersight-mongo:/indicadores-continuidade-coletivos-limite.csv
```
 
### 5.2. Verify if files are in docker path
#### indicadores-continuidade-coletivos-2010-2019.csv
```bash
docker exec -it enersight-mongo ls -lh /indicadores-continuidade-coletivos-2010-2019.csv
```

#### indicadores-continuidade-coletivos-2020-2029.csv
```bash
    docker exec -it enersight-mongo ls -lh /indicadores-continuidade-coletivos-2020-2029.csv
```

#### indicadores-continuidade-coletivos-limite.csv
```bash
docker exec -it enersight-mongo ls -lh /indicadores-continuidade-coletivos-limite.csv
```

### 5.3. Upload to mongo the ANEEL data:
#### indicadores-continuidade-coletivos-2010-2019.csv
```bash
docker exec -it enersight-mongo mongoimport --username app_user --password app_password --authenticationDatabase enersight_app  --db enersight_app --collection indicadores_continuidade-2010-2019 --type csv --headerline --file /indicadores-continuidade-coletivos-2010-2019.csv --numInsertionWorkers 4
```

#### indicadores-continuidade-coletivos-2020-2029.csv
```bash
docker exec -it enersight-mongo mongoimport --username app_user --password app_password --authenticationDatabase enersight_app  --db enersight_app --collection indicadores_continuidade-2020-2029 --type csv --headerline --file /indicadores-continuidade-coletivos-2020-2029.csv --numInsertionWorkers 4
```

#### indicadores-continuidade-coletivos-limite.csv
```bash
docker exec -it enersight-mongo mongoimport --username app_user --password app_password --authenticationDatabase enersight_app  --db enersight_app --collection indicadores_continuidade-limite --type csv --headerline --file /indicadores-continuidade-coletivos-limite.csv --numInsertionWorkers 4
```

## 6. Verify the colletions
> You can use mongo-express at: http://0.0.0.0:8081/db/aneel/ 

<img width="1158" height="348" alt="image" src="https://github.com/user-attachments/assets/54297fa5-b257-4e10-81da-f8a3b18c8ddc" />
