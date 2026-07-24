-- Drops the stray camelCase `volumeMaster` key that AccountService wrote alongside the correct
-- `volume_master` key. Root cause: AccountService serialized AccountSettings with a bare
-- `new ObjectMapper()` (no ParameterNamesModule), so Jackson ignored the record's
-- @JsonProperty("volume_master") and fell back to the accessor name `volumeMaster()`. AccountDao's
-- shallow `COALESCE(...) || :settingsJson::jsonb` merge then left the differently-spelled key sitting
-- alongside the real one forever. The code fix (share DatabaseManager's configured mapper) stops new
-- writes; this migration cleans up rows already polluted. `volume_master` — the correct key — is untouched.
UPDATE accounts SET settings = settings - 'volumeMaster' WHERE settings ? 'volumeMaster';
