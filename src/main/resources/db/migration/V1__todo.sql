CREATE TABLE todo (
    id SERIAL PRIMARY KEY,
    text TEXT NOT NULL,
    done BOOLEAN NOT NULL,
    index INT NOT NULL
)