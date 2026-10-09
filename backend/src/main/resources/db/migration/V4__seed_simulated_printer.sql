-- The single simulated printer for the V1 demonstration. It is not connected
-- to any hardware; its outcomes are reported through the simulation endpoints.
-- The bed starts cleared so the first attempt can begin.

INSERT INTO printer (id, name, simulated, bed_state, bed_state_changed_at)
VALUES ('0d3d0000-0000-4000-8000-000000000101', 'SIM-X1C-01 (simulated)', true, 'CLEAR', now());
