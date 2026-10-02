-- One physical database, one schema per module (logical separation, no cross-module joins).
-- Runs once, before Hibernate creates any table. Objects that depend on the tables (search
-- extensions, functions and indexes) live in ../post-ddl/, run by Spring on every startup.
CREATE SCHEMA IF NOT EXISTS users;
CREATE SCHEMA IF NOT EXISTS catalog;
CREATE SCHEMA IF NOT EXISTS orders;
CREATE SCHEMA IF NOT EXISTS cart;
CREATE SCHEMA IF NOT EXISTS reviews;
CREATE SCHEMA IF NOT EXISTS lists;
