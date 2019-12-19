PGUSER=telecode
PGPASSWORD=telecode_pass
PGDATABASE=telecode
PGHOST=localhost
PGPORT=5432
ifeq ($(shell uname -s),Darwin)
  SUPER_USER?=$(USER)
  PSQL=psql -U $(SUPER_USER)
else
  PSQL=sudo -u postgres psql
endif

build:
	echo "Nothing to build"

start:
	sbt run

env:
	@echo export PGUSER=$(PGUSER)
	@echo export PGPASSWORD='$(PGPASSWORD)'
	@echo export PGDATABASE=$(PGDATABASE)
	@echo export PGHOST=$(PGHOST)
	@echo export PGPORT=$(PGPORT)

.db:
	${PSQL} -d postgres -c "CREATE USER ${PGUSER} WITH ENCRYPTED PASSWORD '${PGPASSWORD}' SUPERUSER CREATEDB;"
	sleep 1
	${PSQL} -d postgres -c "CREATE DATABASE ${PGDATABASE} WITH OWNER=${PGUSER};"
	sleep 1
	${PSQL} -d postgres -c "GRANT ALL PRIVILEGES ON DATABASE ${PGDATABASE} TO ${PGUSER};"
	sleep 1
	make extract_schema
	touch $@

initdb: .db

extract_schema:
	${PSQL} -f telecode.sql -d ${PGDATABASE}

cleandb:
	${PSQL} -d postgres -c "drop database if exists ${PGDATABASE};"
	${PSQL} -d postgres -c "drop user ${PGUSER};"
	rm .db