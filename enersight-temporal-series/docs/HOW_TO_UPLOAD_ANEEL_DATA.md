# To test:
1º go to docker folder: /6_back/docker$ 

2º  search mongo and mongo-express container name:
```
# 1
docker ps
```
<img width="1767" height="57" alt="image" src="https://github.com/user-attachments/assets/33597f78-e617-48f7-a51b-90cdc9b03bdb" />

3º put up mongo image on docker
```
docker compose up enersight-mongo mongo-express
```

4º you would need to do the files download. search in my drive https://drive.google.com/file/d/1PIFDSSSO3jhJFOC7_twfx7egiLqlygNu/view?usp=drive_link:
indicadores-continuidade-coletivos-2010-2019.csv
indicadores-continuidade-coletivos-2020-2029.csv
indicadores-continuidade-coletivos-atributos.csv

5º follow the steps for upload the files in mongo:

### In docker folder, make this commands:

#### For copy the files to docker path:
- docker cp indicadores-continuidade-coletivos-2010-2019.csv enersight-mongo:/indicadores-continuidade-coletivos-2010-2019.csv
- docker cp indicadores-continuidade-coletivos-2020-2029.csv enersight-mongo:/indicadores-continuidade-coletivos-2020-2029.csv
- docker cp indicadores-continuidade-coletivos-limite.csv enersight-mongo:/indicadores-continuidade-coletivos-limite.csv
 
#### For verify if files are in docker path:
- docker exec -it enersight-mongo ls -lh /indicadores-continuidade-coletivos-limite.csv
- docker exec -it enersight-mongo ls -lh /indicadores-continuidade-coletivos-2010-2019.csv
- docker exec -it enersight-mongo ls -lh /indicadores-continuidade-coletivos-2020-2029.csv

#### For upload to mongo the ANEEL data:
- docker exec -it enersight-mongo mongoimport --username root --password password --authenticationDatabase admin --db aneel --collection indicadores_continuidade-limite --type csv --headerline --file /indicadores-continuidade-coletivos-limite.csv --numInsertionWorkers 4
- docker exec -it enersight-mongo mongoimport --username root --password password --authenticationDatabase admin --db aneel --collection indicadores_continuidade-2010-2019 --type csv --headerline --file /indicadores-continuidade-coletivos-2010-2019.csv --numInsertionWorkers 4
- docker exec -it enersight-mongo mongoimport --username root --password password --authenticationDatabase admin --db aneel --collection indicadores_continuidade-2020-2029 --type csv --headerline --file /indicadores-continuidade-coletivos-2020-2029.csv --numInsertionWorkers 4


6º go to http://0.0.0.0:8081/db/aneel/ and verify if these files are in the collection:
<img width="1158" height="348" alt="image" src="https://github.com/user-attachments/assets/54297fa5-b257-4e10-81da-f8a3b18c8ddc" />



  