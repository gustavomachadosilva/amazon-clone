-- Catalog full-text search objects (#220). Run by Spring SQL initialization on EVERY startup,
-- after Hibernate ddl-auto has created or updated catalog.products
-- (spring.jpa.defer-datasource-initialization=true), so every statement must be idempotent.
--
-- Spring ScriptUtils splits this file on semicolons without understanding dollar quotes, so
-- function bodies must never contain one (and these comments avoid quotes and semicolons too).
--
-- Extensions live in public (shared, trusted extensions). Every module object lives in the
-- catalog schema (Contrato de Modularidade regra 5).

CREATE EXTENSION IF NOT EXISTS unaccent WITH SCHEMA public;
CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;
CREATE EXTENSION IF NOT EXISTS fuzzystrmatch WITH SCHEMA public;

-- unaccent(text) is only STABLE (it looks its dictionary up through the search_path), so it
-- cannot be used in an index expression. Pinning the dictionary makes this wrapper IMMUTABLE.
CREATE OR REPLACE FUNCTION catalog.immutable_unaccent(text) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
    AS $$ SELECT public.unaccent('public.unaccent'::regdictionary, $1) $$;

-- Lowercased, unaccented concatenation of the searchable fields. Target of the literal (LIKE)
-- terms, the ones containing a percent sign or an underscore, and the vocabulary of the typo
-- correction. Plain concatenation with coalesce (not concat_ws, which is only STABLE) keeps the
-- function immutable and inlinable.
CREATE OR REPLACE FUNCTION catalog.product_search_text(name text, brand text, category text, description text)
    RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
    AS $$ SELECT lower(catalog.immutable_unaccent(coalesce(name, '') || ' ' || coalesce(brand, '') || ' '
                                                  || coalesce(category, '') || ' ' || coalesce(description, ''))) $$;

-- Weighted document: name A, brand and category B, description C. The english configuration
-- stems words (laptops matches laptop) and ignores English stop words.
CREATE OR REPLACE FUNCTION catalog.product_search_vector(name text, brand text, category text, description text)
    RETURNS tsvector
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
    AS $$ SELECT setweight(to_tsvector('english'::regconfig, catalog.immutable_unaccent(coalesce(name, ''))), 'A')
              || setweight(to_tsvector('english'::regconfig, catalog.immutable_unaccent(coalesce(brand, ''))), 'B')
              || setweight(to_tsvector('english'::regconfig, catalog.immutable_unaccent(coalesce(category, ''))), 'B')
              || setweight(to_tsvector('english'::regconfig, catalog.immutable_unaccent(coalesce(description, ''))), 'C') $$;

-- q is a to_tsquery expression built by ProductTextQuery: only letters, digits, the AND operator
-- and the prefix marker. A query made only of stop words (the, off) normalizes to an empty
-- tsquery, which would match nothing, so it is treated as no text condition instead.
CREATE OR REPLACE FUNCTION catalog.product_fts_matches(name text, brand text, category text, description text, q text)
    RETURNS boolean
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
    AS $$ SELECT numnode(to_tsquery('english'::regconfig, catalog.immutable_unaccent(q))) = 0
              OR catalog.product_search_vector(name, brand, category, description)
                 @@ to_tsquery('english'::regconfig, catalog.immutable_unaccent(q)) $$;

-- Full-text rank of the whole weighted document. Normalization 1 divides by 1 + log(document
-- length), so a long name repeating a word no longer outranks a short one by sheer frequency, and
-- 32 maps the result into the range 0 to 1 (rank / (rank + 1)). Only product_relevance uses it,
-- to break ties inside a relevance tier. No index depends on it.
CREATE OR REPLACE FUNCTION catalog.product_search_rank(name text, brand text, category text, description text, q text)
    RETURNS real
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
    AS $$ SELECT ts_rank(catalog.product_search_vector(name, brand, category, description),
                         to_tsquery('english'::regconfig, catalog.immutable_unaccent(q)), 1 | 32) $$;

-- Name head (#221): the unaccented name up to the first whole word that introduces a
-- compatibility tail (for, compatible, fits, replacement, replaces, para, compativel), any case.
-- In a name like Remote Replacement for Samsung TV the head is Remote, so the product that IS
-- the searched thing ranks above the accessory made FOR it. When the name starts with a marker
-- the head would be empty and the whole name is used instead.
CREATE OR REPLACE FUNCTION catalog.product_name_head(name text)
    RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
    AS $$ SELECT coalesce(nullif(btrim(regexp_replace(catalog.immutable_unaccent(coalesce(name, '')),
                                                      '\m(for|compatible|fits|replacement|replaces|para|compativel)\M.*$',
                                                      '', 'i')), ''),
                          coalesce(name, '')) $$;

-- Relevance score of the search sort (#221), used only in ORDER BY (the match itself is
-- product_fts_matches). Tiers, highest first, added up:
--   8  every term in the name head
--   4  any term in the brand
--   2  every term anywhere in the name
--   2  two or more terms found as a contiguous phrase in the name
--   1  every term in the category
-- plus product_search_rank, which is below 1 and so only orders products inside the same tier.
-- q_all joins the terms with the AND operator, q_any with OR and q_phrase with the followed-by
-- operator, all built by ProductTextQuery. Callers add id ASC as the final tie-break.
CREATE OR REPLACE FUNCTION catalog.product_relevance(name text, brand text, category text, description text,
                                                     q_all text, q_any text, q_phrase text)
    RETURNS real
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
    AS $$ SELECT (8 * (to_tsvector('english'::regconfig, catalog.immutable_unaccent(catalog.product_name_head(name)))
                       @@ to_tsquery('english'::regconfig, catalog.immutable_unaccent(q_all)))::int
                + 4 * (to_tsvector('english'::regconfig, catalog.immutable_unaccent(coalesce(brand, '')))
                       @@ to_tsquery('english'::regconfig, catalog.immutable_unaccent(q_any)))::int
                + 2 * (to_tsvector('english'::regconfig, catalog.immutable_unaccent(coalesce(name, '')))
                       @@ to_tsquery('english'::regconfig, catalog.immutable_unaccent(q_all)))::int
                + 2 * (numnode(to_tsquery('english'::regconfig, catalog.immutable_unaccent(q_phrase))) > 1
                       AND to_tsvector('english'::regconfig, catalog.immutable_unaccent(coalesce(name, '')))
                           @@ to_tsquery('english'::regconfig, catalog.immutable_unaccent(q_phrase)))::int
                + 1 * (to_tsvector('english'::regconfig, catalog.immutable_unaccent(coalesce(category, '')))
                       @@ to_tsquery('english'::regconfig, catalog.immutable_unaccent(q_all)))::int
               )::real
               + catalog.product_search_rank(name, brand, category, description, q_all) $$;

CREATE INDEX IF NOT EXISTS products_search_vector_idx ON catalog.products
    USING gin (catalog.product_search_vector(name, brand, category, description));

CREATE INDEX IF NOT EXISTS products_search_text_trgm_idx ON catalog.products
    USING gin (catalog.product_search_text(name, brand, category, description) public.gin_trgm_ops);
