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
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: pgcrypto; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;


--
-- Name: EXTENSION pgcrypto; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION pgcrypto IS 'cryptographic functions';


--
-- Name: uuid-ossp; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;


--
-- Name: EXTENSION "uuid-ossp"; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION "uuid-ossp" IS 'generate universally unique identifiers (UUIDs)';


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: audit_logs; Type: TABLE; Schema: public; Owner: rh_user
--

CREATE TABLE public.audit_logs (
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


ALTER TABLE public.audit_logs OWNER TO rh_user;

--
-- Name: driver_documents; Type: TABLE; Schema: public; Owner: rh_user
--

CREATE TABLE public.driver_documents (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    driver_id uuid NOT NULL,
    type character varying(50) NOT NULL,
    document_number character varying(100) NOT NULL,
    url text NOT NULL,
    status character varying(50) DEFAULT 'PENDING'::character varying NOT NULL,
    reason text,
    uploaded_at timestamp with time zone DEFAULT now(),
    reviewed_by uuid
);


ALTER TABLE public.driver_documents OWNER TO rh_user;

--
-- Name: driver_stats; Type: TABLE; Schema: public; Owner: rh_user
--

CREATE TABLE public.driver_stats (
    driver_id uuid NOT NULL,
    total_trips integer DEFAULT 0,
    cancelled_trips integer DEFAULT 0,
    rating double precision DEFAULT 0.0,
    earnings double precision DEFAULT 0.0,
    updated_at timestamp with time zone DEFAULT now()
);


ALTER TABLE public.driver_stats OWNER TO rh_user;

--
-- Name: refresh_tokens; Type: TABLE; Schema: public; Owner: rh_user
--

CREATE TABLE public.refresh_tokens (
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


ALTER TABLE public.refresh_tokens OWNER TO rh_user;

--
-- Name: rider_addresses; Type: TABLE; Schema: public; Owner: rh_user
--

CREATE TABLE public.rider_addresses (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    rider_id uuid NOT NULL,
    label character varying(100) NOT NULL,
    lat double precision NOT NULL,
    lng double precision NOT NULL,
    created_at timestamp with time zone DEFAULT now()
);


ALTER TABLE public.rider_addresses OWNER TO rh_user;

--
-- Name: user_profiles; Type: TABLE; Schema: public; Owner: rh_user
--

CREATE TABLE public.user_profiles (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    name character varying(255) NOT NULL,
    phone character varying(50) NOT NULL,
    email character varying(255) NOT NULL,
    photo_url text,
    role character varying(50) NOT NULL,
    status character varying(50) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);


ALTER TABLE public.user_profiles OWNER TO rh_user;

--
-- Name: users; Type: TABLE; Schema: public; Owner: rh_user
--

CREATE TABLE public.users (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    username character varying(150) NOT NULL,
    password_hash text NOT NULL,
    role character varying(20) DEFAULT 'USER'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now(),
    locked_until timestamp(6) with time zone
);


ALTER TABLE public.users OWNER TO rh_user;

--
-- Name: audit_logs audit_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: rh_user
--

ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT audit_logs_pkey PRIMARY KEY (id);


--
-- Name: driver_documents driver_documents_pkey; Type: CONSTRAINT; Schema: public; Owner: rh_user
--

ALTER TABLE ONLY public.driver_documents
    ADD CONSTRAINT driver_documents_pkey PRIMARY KEY (id);


--
-- Name: driver_stats driver_stats_pkey; Type: CONSTRAINT; Schema: public; Owner: rh_user
--

ALTER TABLE ONLY public.driver_stats
    ADD CONSTRAINT driver_stats_pkey PRIMARY KEY (driver_id);


--
-- Name: refresh_tokens refresh_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: rh_user
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id);


--
-- Name: rider_addresses rider_addresses_pkey; Type: CONSTRAINT; Schema: public; Owner: rh_user
--

ALTER TABLE ONLY public.rider_addresses
    ADD CONSTRAINT rider_addresses_pkey PRIMARY KEY (id);


--
-- Name: user_profiles user_profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: rh_user
--

ALTER TABLE ONLY public.user_profiles
    ADD CONSTRAINT user_profiles_pkey PRIMARY KEY (id);


--
-- Name: user_profiles user_profiles_user_id_key; Type: CONSTRAINT; Schema: public; Owner: rh_user
--

ALTER TABLE ONLY public.user_profiles
    ADD CONSTRAINT user_profiles_user_id_key UNIQUE (user_id);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: rh_user
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: users users_username_key; Type: CONSTRAINT; Schema: public; Owner: rh_user
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_username_key UNIQUE (username);


--
-- Name: idx_audit_event_type; Type: INDEX; Schema: public; Owner: rh_user
--

CREATE INDEX idx_audit_event_type ON public.audit_logs USING btree (event_type);


--
-- Name: idx_audit_logs_entity_id; Type: INDEX; Schema: public; Owner: rh_user
--

CREATE INDEX idx_audit_logs_entity_id ON public.audit_logs USING btree (entity_id);


--
-- Name: idx_audit_timestamp; Type: INDEX; Schema: public; Owner: rh_user
--

CREATE INDEX idx_audit_timestamp ON public.audit_logs USING btree ("timestamp");


--
-- Name: idx_audit_user_id; Type: INDEX; Schema: public; Owner: rh_user
--

CREATE INDEX idx_audit_user_id ON public.audit_logs USING btree (user_id);


--
-- Name: idx_audit_username; Type: INDEX; Schema: public; Owner: rh_user
--

CREATE INDEX idx_audit_username ON public.audit_logs USING btree (username);


--
-- Name: idx_driver_documents_driver_id; Type: INDEX; Schema: public; Owner: rh_user
--

CREATE INDEX idx_driver_documents_driver_id ON public.driver_documents USING btree (driver_id);


--
-- Name: idx_driver_documents_status; Type: INDEX; Schema: public; Owner: rh_user
--

CREATE INDEX idx_driver_documents_status ON public.driver_documents USING btree (status);


--
-- Name: idx_rider_addresses_rider_id; Type: INDEX; Schema: public; Owner: rh_user
--

CREATE INDEX idx_rider_addresses_rider_id ON public.rider_addresses USING btree (rider_id);


--
-- Name: idx_user_profiles_email; Type: INDEX; Schema: public; Owner: rh_user
--

CREATE INDEX idx_user_profiles_email ON public.user_profiles USING btree (email);


--
-- Name: idx_user_profiles_phone; Type: INDEX; Schema: public; Owner: rh_user
--

CREATE INDEX idx_user_profiles_phone ON public.user_profiles USING btree (phone);


--
-- PostgreSQL database dump complete
--

-- Removed pg_dump \unrestrict meta-command
