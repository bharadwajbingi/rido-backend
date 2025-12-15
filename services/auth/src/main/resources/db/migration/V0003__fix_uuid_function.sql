-- Fix unqualified UUID function in users table
-- V0001 uses gen_random_uuid() without schema qualification, which fails in some contexts
-- This migration ensures UUID generation works in all environments

-- Alter users table to use properly qualified UUID function
ALTER TABLE users 
  ALTER COLUMN id SET DEFAULT public.uuid_generate_v4();
