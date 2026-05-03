CREATE TABLE aneel_dominio_indicadores_indqual (
    id SERIAL PRIMARY KEY,
    indicator_code VARCHAR(255) NOT NULL UNIQUE,
    indicator_description TEXT
);
