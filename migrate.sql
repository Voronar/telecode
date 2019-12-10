DROP TABLE IF EXISTS configs CASCADE;
CREATE TABLE configs (
  id serial PRIMARY KEY,
  chatId int not NULL,
  -- themeChanging BOOLEAN NOT NULL DEFAULT false,
  theme varchar(32) NOT NULL
);