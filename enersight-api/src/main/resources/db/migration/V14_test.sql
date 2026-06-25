CREATE TABLE test (
    id SERIAL PRIMARY KEY,
    indicator_code VARCHAR(255) NOT NULL UNIQUE,
    indicator_description TEXT
);
