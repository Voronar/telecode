# telecode

## How to run

1. Create `.env`-file from `.env.example`-file
2. Enter valid data into `.env` according to `.env.example` instruction
3. Install `postgres`

   - `macos`-specific command (run `initdb /usr/local/var/postgres` after install)

4. run `make .db` for db stuff creation (`make dbclean` for db stuff cleaning)
5. run `make start` for application start
