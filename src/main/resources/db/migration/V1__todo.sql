CREATE SEQUENCE todo_id_seq;

CREATE TABLE todo (
    id INT PRIMARY KEY,
    text TEXT NOT NULL,
    done BOOLEAN NOT NULL,
    index INT NOT NULL
)