DROP TABLE IF EXISTS configs CASCADE;
CREATE TABLE configs (
  id serial PRIMARY KEY,
  chatId bigint not NULL,
  inputMode VARCHAR(32) NOT NULL DEFAULT '',
  theme varchar(32) NOT NULL
);