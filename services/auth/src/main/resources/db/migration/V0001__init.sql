--
-- PostgreSQL database dump
--

-- Removed pg_dump \restrict meta-command

-- Dumped from database version 15.15 (Debian 15.15-1.pgdg13+1)
-- Dumped by pg_dump version 15.15 (Debian 15.15-1.pgdg13+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
-- SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: pgcrypto; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pgcrypto;


--
-- Name: EXTENSION pgcrypto; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION pgcrypto IS 'cryptographic functions';


--
-- Name: uuid-ossp; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";


--
-- Name: EXTENSION "uuid-ossp"; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION "uuid-ossp" IS 'generate universally unique identifiers (UUIDs)';


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: audit_logs; Type: TABLE; Schema: auth; Owner: rh_user
--

CREATE TABLE audit_logs (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    entity character varying(100),
    entity_id character varying(100),
    action character varying(100),
    actor uuid,
    metadata text,
    created_at timestamp with time zone DEFAULT now(),
    device_id character varying(255),
    event_type character varying(50) NOT NULL,
    failure_reason character varying(500),
    ip_address character varying(45),
    success boolean NOT NULL,
    "timestamp" timestamp(6) with time zone NOT NULL,
    user_agent character varying(500),
    user_id uuid,
    username character varying(150)
);


ALTER TABLE audit_logs OWNER TO rh_user;

-- Removed Profile Service tables (user_profiles, rider_addresses, etc) as they are now in 'profile' schema

--
-- Name: users; Type: TABLE; Schema: auth; Owner: rh_user
--

CREATE TABLE users (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    username character varying(150) NOT NULL,
    password_hash text NOT NULL,
    role character varying(20) DEFAULT 'USER'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now(),
    locked_until timestamp(6) with time zone
);


ALTER TABLE users OWNER TO rh_user;

--
-- Name: refresh_tokens; Type: TABLE; Schema: auth; Owner: rh_user
--

CREATE TABLE refresh_tokens (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    device_id character varying(255),
    expires_at timestamp(6) with time zone NOT NULL,
    ip character varying(255),
    jti uuid,
    revoked boolean NOT NULL,
    token_hash character varying(256) NOT NULL,
    user_agent character varying(255),
    user_id uuid NOT NULL,
    rotated boolean DEFAULT false NOT NULL
);


ALTER TABLE refresh_tokens OWNER TO rh_user;

--
-- Name: audit_logs audit_logs_pkey; Type: CONSTRAINT; Schema: auth; Owner: rh_user
--

ALTER TABLE ONLY audit_logs
    ADD CONSTRAINT audit_logs_pkey PRIMARY KEY (id);


--
-- Name: refresh_tokens refresh_tokens_pkey; Type: CONSTRAINT; Schema: auth; Owner: rh_user
--

ALTER TABLE ONLY refresh_tokens
    ADD CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: auth; Owner: rh_user
--

ALTER TABLE ONLY users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: users users_username_key; Type: CONSTRAINT; Schema: auth; Owner: rh_user
--

ALTER TABLE ONLY users
    ADD CONSTRAINT users_username_key UNIQUE (username);


--
-- Name: idx_audit_event_type; Type: INDEX; Schema: auth; Owner: rh_user
--

CREATE INDEX idx_audit_event_type ON audit_logs USING btree (event_type);


--
-- Name: idx_audit_logs_entity_id; Type: INDEX; Schema: auth; Owner: rh_user
--

CREATE INDEX idx_audit_logs_entity_id ON audit_logs USING btree (entity_id);


--
-- Name: idx_audit_timestamp; Type: INDEX; Schema: auth; Owner: rh_user
--

CREATE INDEX idx_audit_timestamp ON audit_logs USING btree ("timestamp");


--
-- Name: idx_audit_user_id; Type: INDEX; Schema: auth; Owner: rh_user
--

CREATE INDEX idx_audit_user_id ON audit_logs USING btree (user_id);


--
-- Name: idx_audit_username; Type: INDEX; Schema: auth; Owner: rh_user
--

CREATE INDEX idx_audit_username ON audit_logs USING btree (username);


--
-- PostgreSQL database dump complete
--

-- Removed pg_dump \unrestrict meta-command
