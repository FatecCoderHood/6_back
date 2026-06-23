# 🗄️ Database Migrations com Flyway

> Documentação técnica sobre a estratégia de versionamento de banco de dados adotada no projeto **EnerSight**, com foco na ferramenta Flyway e nas decisões de design que guiaram sua implementação.

---

## Sumário

- [O Problema: Por que versionar o banco de dados?](#o-problema-por-que-versionar-o-banco-de-dados)
- [A Solução: Flyway](#a-solução-flyway)
- [Por que Flyway e não outras ferramentas?](#por-que-flyway-e-não-outras-ferramentas)
- [Arquitetura da Solução](#arquitetura-da-solução)
- [Convenção de Nomenclatura](#convenção-de-nomenclatura)
- [Histórico de Migrations](#histórico-de-migrations)
- [Separação de Responsabilidades: `flyway_user` vs `app_user`](#separação-de-responsabilidades-flyway_user-vs-app_user)
- [Integração com Spring Boot](#integração-com-spring-boot)
- [Flyway e o Princípio de Infraestrutura como Código](#flyway-e-o-princípio-de-infraestrutura-como-código)
- [Boas Práticas Adotadas](#boas-práticas-adotadas)
- [Fluxo DevOps](#fluxo-devops)

---

## O Problema: Por que versionar o banco de dados?

Em projetos de software, o código-fonte é versionado com ferramentas como Git — qualquer desenvolvedor pode clonar o repositório e ter o estado exato da aplicação. O banco de dados, historicamente, ficava de fora dessa disciplina.

Martin Fowler, em seu seminal artigo *Evolutionary Database Design* (2003, coescrito com Pramod Sadalage), identifica esse problema com precisão:

> "O banco de dados é frequentemente tratado como um cidadão de segunda classe no processo de desenvolvimento. Mudanças são feitas manualmente, sem rastreamento, criando um abismo entre o que o código espera e o que o banco realmente tem."

Esse abismo se manifesta de formas concretas:

- Um novo desenvolvedor clona o repositório, mas o banco local está desatualizado — a aplicação quebra.
- Um deploy em produção falha porque uma coluna esperada pelo código ainda não existe no banco.
- É impossível saber *quando* e *por quê* uma tabela foi alterada.
- Ambientes (desenvolvimento, homologação, produção) ficam com schemas divergentes ao longo do tempo.

A solução para esse problema é aplicar ao banco de dados a mesma disciplina que aplicamos ao código: **versionamento incremental, rastreável e automatizado**.

---

## A Solução: Flyway

O **Flyway** é uma ferramenta open-source de database migration que resolve esse problema de forma elegante. Seu modelo de funcionamento é direto:

1. Cada alteração no banco de dados é escrita como um arquivo SQL com um número de versão.
2. O Flyway mantém uma tabela de controle (`flyway_schema_history`) que registra quais migrations já foram executadas.
3. A cada inicialização da aplicação (ou chamada explícita), o Flyway compara as migrations disponíveis com as já executadas e aplica apenas as pendentes, **em ordem**.

O resultado é que o estado do banco de dados se torna **completamente derivável a partir do repositório Git**. Qualquer ambiente, a partir de um banco vazio, chegará ao mesmo schema ao rodar a aplicação.

---

## Por que Flyway e não outras ferramentas?

Existem alternativas no ecossistema, sendo a principal o **Liquibase**. A escolha pelo Flyway foi intencional e baseada nos seguintes critérios:

### SQL Puro como Linguagem Nativa

O Flyway usa SQL como linguagem padrão para as migrations. Isso tem implicações práticas importantes:

- Qualquer desenvolvedor ou DBA que conheça SQL pode ler, entender e auditar as migrations sem precisar aprender uma nova DSL (Domain Specific Language).
- O Liquibase oferece formatos XML, YAML e JSON além do SQL — o que adiciona abstração sem necessariamente adicionar valor para um projeto com banco único e bem definido.
- Migrações em SQL puro são portáveis e executáveis diretamente no cliente de banco de dados para fins de debugging, sem depender de tooling externo.

### Modelo Mental Simples

O modelo do Flyway é linear e imutável: uma vez aplicada, uma migration nunca é modificada. Esse invariante simplifica o raciocínio sobre o estado do banco. Não há "rollback automático" — o que reforça a necessidade de pensar em migrações como operações deliberadas e permanentes, alinhado com as práticas de ambientes de produção.

### Integração com Spring Boot

O Flyway possui integração nativa com o ecossistema Spring Boot via `spring-boot-starter-flyway`. A ferramenta se torna parte do ciclo de vida da aplicação: as migrations são executadas automaticamente no startup, antes mesmo do contexto JPA ser inicializado. Isso garante que o Hibernate nunca encontre um schema inconsistente.

### Maturidade e Suporte ao PostgreSQL

O Flyway tem suporte dedicado ao PostgreSQL via o módulo `flyway-database-postgresql`, incluindo features específicas como suporte a schemas, sequências e tipos customizados — todos utilizados neste projeto.

---

## Arquitetura da Solução

```
enersight-api/
└── src/
    └── main/
        └── resources/
            └── db/
                └── migration/
                    ├── V1__create_aneel_dominio_indicadores_indqual.sql
                    ├── V2__copy_aneel_dominio_indicadores_indqual.sql
                    ├── V3__create_core_schema.sql
                    ├── V4__create_ingestion_jobs_table.sql
                    ├── V5__create_users_table.sql
                    ├── V6__create_terms_of_use_table.sql
                    ├── V7__create_user_terms_acceptance_table.sql
                    ├── V8__seed_terms_of_use.sql
                    ├── V9__seed_admin_user.sql
                    ├── V10__add_pending_role.sql
                    ├── V11__seed_admin_terms_acceptance.sql
                    └── V12__drop_uta_user_fk.sql
```

O diretório `db/migration` é o único ponto de verdade para a estrutura do banco de dados. Cada arquivo representa uma unidade atômica de mudança.

---

## Convenção de Nomenclatura

O Flyway utiliza um padrão de nomenclatura com semântica bem definida:

```
V{versão}__{descrição}.sql
```

| Componente | Detalhe |
|---|---|
| `V` | Prefixo obrigatório para versioned migrations |
| `{versão}` | Número inteiro sequencial (ex: `1`, `2`, `10`) |
| `__` | Separador duplo underscore obrigatório |
| `{descrição}` | Nome em snake_case descrevendo a mudança |

**Exemplos do projeto:**

```
V3__create_core_schema.sql       → Criação do schema "core"
V5__create_users_table.sql       → Criação da tabela de usuários
V10__add_pending_role.sql        → Adição do papel PENDING ao CHECK constraint
V12__drop_uta_user_fk.sql        → Remoção de foreign key
```

A descrição deve ser um verbo no infinitivo seguido do objeto da operação. Isso torna a `flyway_schema_history` legível como um log de auditoria do banco de dados.

---

## Histórico de Migrations

A seguir, o histórico completo das migrations aplicadas no projeto, com contexto sobre cada decisão:

### `V1` — Tabela de indicadores ANEEL
Criação da tabela `aneel_dominio_indicadores_indqual` no schema público. Esta tabela é o ponto de entrada dos dados regulatórios da ANEEL, contendo os códigos e descrições dos indicadores de qualidade do setor elétrico.

### `V2` — Seed de indicadores ANEEL
Populamento inicial da tabela de indicadores com os códigos oficiais (DEC, FEC, DIC, etc.). Esta migration demonstra um padrão importante: **dados de referência imutáveis são versionados junto com a estrutura**, garantindo que qualquer ambiente tenha os dados necessários para operar.

### `V3` — Criação dos schemas `core` e `staging`
Migration central da arquitetura do banco. Cria dois schemas separados com permissões distintas:
- `core`: dados de domínio da aplicação (usuários, termos, etc.)
- `staging`: dados intermediários do pipeline de ingestão

Esta migration também estabelece a separação de usuários do banco, tema detalhado na seção seguinte.

### `V4` — Tabela `staging.ingestion_jobs`
Criação da tabela de controle de jobs de ingestão de dados, incluindo um `ENUM` customizado para status (`pending`, `processing`, `done`, `failed`) e constraints de unicidade na URL.

### `V5` — Tabela `core.users`
Estrutura de usuários com UUID como chave primária, constraint de unicidade no email e CHECK constraint para o campo `role`.

### `V6` — Tabela `core.terms_of_use`
Tabela para os termos de uso da plataforma, com suporte a versioning de termos e tipos (`MANDATORY`/`NON_MANDATORY`).

### `V7` — Tabela `core.user_terms_acceptance`
Relacionamento entre usuários e termos de uso com rastreamento de aceite/revogação.

### `V8` — Seed de termos de uso
Dados iniciais de termos de uso (Terms of Service e Marketing Communications). Assim como a V2, dados de inicialização são tratados como migrations.

### `V9` — Seed do usuário administrador
Criação do usuário admin inicial com senha pré-hasheada via bcrypt. Credenciais de bootstrap versionadas de forma segura (apenas o hash, nunca a senha em claro).

### `V10` — Evolução do CHECK constraint de role
Adição do papel `PENDING` ao constraint de `role` na tabela `users`. Esta migration exemplifica uma **mudança evolutiva**: em vez de reescrever V5, cria-se uma nova migration que altera o que é necessário. O histórico permanece intacto.

### `V11` — Seed de aceite de termos para o admin
Popula a aceitação de todos os termos ativos para o usuário admin usando CTEs, garantindo consistência mesmo que os IDs sejam gerados dinamicamente.

### `V12` — Remoção de foreign key
Drop de uma constraint de FK em `user_terms_acceptance`. Documenta explicitamente uma decisão de relaxar uma restrição de integridade referencial, algo que em um ambiente sem migrations seria quase impossível de rastrear.

---

## Separação de Responsabilidades: `flyway_user` vs `app_user`

Uma das decisões de design mais importantes desta implementação é a **separação entre o usuário que gerencia o schema e o usuário que opera a aplicação**.

```properties
# Usuário da aplicação (JPA/Hibernate)
spring.datasource.username=app_user

# Usuário do Flyway (DDL)
spring.flyway.user=flyway_user
```

### Por que isso importa?

O **Princípio do Menor Privilégio** (Principle of Least Privilege) dita que cada componente de um sistema deve ter apenas as permissões necessárias para realizar sua função. Aplicado ao banco de dados:

- `flyway_user`: possui permissão para executar DDL (`CREATE TABLE`, `ALTER TABLE`, `DROP`). É usado **exclusivamente** durante o processo de migration.
- `app_user`: possui apenas permissões DML (`SELECT`, `INSERT`, `UPDATE`, `DELETE`). É o usuário da aplicação em runtime.

Isso significa que mesmo que um atacante consiga executar código arbitrário através da aplicação, ele **não conseguirá** fazer `DROP TABLE` ou alterar a estrutura do banco — o usuário da aplicação simplesmente não tem essa permissão.

A migration V3 implementa isso explicitamente:

```sql
-- flyway_user cria os schemas
CREATE SCHEMA IF NOT EXISTS core AUTHORIZATION flyway_user;

-- app_user recebe apenas o necessário
GRANT USAGE ON SCHEMA core TO app_user;

-- Privilégios futuros são configurados por padrão
ALTER DEFAULT PRIVILEGES FOR ROLE flyway_user IN SCHEMA core
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app_user;
```

---

## Integração com Spring Boot

A configuração do Flyway no projeto está em `application.properties`:

```properties
# Flyway habilitado
spring.flyway.enabled=true

# Usuário dedicado para DDL
spring.flyway.user=flyway_user
spring.flyway.password=flyway_password

# Localização dos scripts
spring.flyway.locations=classpath:db/migration

# URL do banco (com fallback para desenvolvimento local)
spring.flyway.url=${DB_URL:jdbc:postgresql://localhost:5432/enersight_app}
```

Uma configuração crítica que trabalha em conjunto é:

```properties
spring.jpa.hibernate.ddl-auto=validate
```

Com `validate`, o Hibernate **não toca** no schema — ele apenas verifica se as entidades JPA são compatíveis com o schema existente. Toda a responsabilidade de criação e alteração de estrutura pertence ao Flyway. Sem essa configuração, o Hibernate poderia criar ou alterar tabelas de forma não rastreada, anulando os benefícios do versionamento.

**Ordem de execução no startup:**

```
Spring Boot inicia
    └─► Flyway detecta migrations pendentes
        └─► Flyway aplica migrations em ordem
            └─► Hibernate valida o schema
                └─► Contexto da aplicação é disponibilizado
```

---

## Flyway e o Princípio de Infraestrutura como Código

A adoção do Flyway no projeto é uma instância concreta do princípio mais amplo de **Infrastructure as Code (IaC)**: toda infraestrutura — incluindo o schema do banco de dados — deve ser descrita em código, versionada e tratada com o mesmo rigor que o código da aplicação.

Robert C. Martin, em *Clean Architecture*, argumenta que as decisões de banco de dados são detalhes que não devem ditar a arquitetura da aplicação, mas precisam ser gerenciadas com disciplina. O Flyway permite exatamente isso: a aplicação define *o que* precisa do banco, e as migrations documentam *como* esse estado foi alcançado ao longo do tempo.

No contexto de DevOps, isso tem implicações diretas no pipeline de CI/CD:

- **Reproducibilidade**: qualquer ambiente pode ser criado do zero com o mesmo schema, executando a aplicação uma vez.
- **Auditabilidade**: o histórico Git das migrations é o log de auditoria do schema.
- **Automação**: deploys aplicam migrations automaticamente, sem intervenção manual.
- **Rollback controlado**: ao fazer rollback do código, o desenvolvedor sabe exatamente quais migrations precisam ser desfeitas (via migrations de rollback explícitas, se necessário).

---

## Boas Práticas Adotadas

### Imutabilidade
Migrations já aplicadas **nunca são modificadas**. O Flyway valida checksums — qualquer alteração em um arquivo já executado causa falha no startup. Se uma mudança é necessária, uma nova migration é criada.

### Atomicidade
Cada migration resolve um problema único e bem delimitado. A migration V10 (`add_pending_role`) poderia ter sido incluída na V5 (`create_users_table`), mas foi criada separadamente porque a necessidade surgiu depois. Isso preserva o histórico de decisões.

### Dados e Estrutura Juntos
Seeds de dados de referência (V2, V8, V9, V11) são tratados como migrations de primeira classe. Dados que a aplicação precisa para funcionar são parte do schema, não de scripts avulsos.

### Descrições Significativas
O double underscore e a descrição no nome do arquivo não são apenas convenção cosmética — eles aparecem na tabela `flyway_schema_history` e servem como documentação inline do histórico do banco.

### Uso de Constraints Explícitas
Todas as tabelas usam constraints nomeadas (`CONSTRAINT uq_users_email`, `CONSTRAINT fk_uta_user`, `CONSTRAINT chk_users_role`). Nomes explícitos facilitam referências em migrations futuras e tornam as mensagens de erro do banco mais legíveis.

---

## Fluxo DevOps

```
Desenvolvedor escreve nova migration
    │
    ▼
Git commit + push
    │
    ▼
CI/CD pipeline
    │
    ├─► Build da aplicação
    │
    ├─► Testes de integração
    │   └─► Flyway aplica migrations em banco de teste
    │
    └─► Deploy
        └─► Flyway aplica migrations pendentes em produção
            └─► Aplicação disponível com schema atualizado
```

O banco de dados se torna um artefato gerenciado pelo pipeline, não uma dependência gerenciada manualmente. Essa é a essência da abordagem DevOps aplicada à camada de dados.

---

## Dependências

```xml
<!-- Spring Boot starter com auto-configuração -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-flyway</artifactId>
</dependency>

<!-- Driver específico para PostgreSQL -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

---

## Referências

- Fowler, M. & Sadalage, P. (2003). *Evolutionary Database Design*. martinfowler.com
- Flyway Documentation. *Version-based migrations*. flywaydb.org
- Martin, R. C. (2017). *Clean Architecture: A Craftsman's Guide to Software Structure and Design*. Prentice Hall.
- Humble, J. & Farley, D. (2010). *Continuous Delivery*. Addison-Wesley.