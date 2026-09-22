-- Migration: 20260921000002_cards_rpc.sql
-- Description: Card registration verification and RLS hardening

-- Ensure cards RLS is enabled and forced
ALTER TABLE public.cards ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.cards FORCE ROW LEVEL SECURITY;

-- Grant execution to authenticated users
GRANT EXECUTE ON FUNCTION public.register_card_v1(jsonb) TO authenticated;
GRANT EXECUTE ON FUNCTION public.delete_unused_card_v1(jsonb) TO authenticated;
