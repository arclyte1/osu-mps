package osu.mps.data.migration

import org.jetbrains.exposed.sql.transactions.TransactionManager

object DatabaseMigrations {

    // Each migration must be safe to run again when the application starts.
    fun run() {
        addMatchParserVersion()
        allowUnknownScoreDetails()
    }

    private fun addMatchParserVersion() {
        TransactionManager.current().exec(
            """
            DO $$ BEGIN
                IF to_regclass('public.match') IS NOT NULL THEN
                    IF NOT EXISTS (
                        SELECT 1 FROM pg_attribute
                        WHERE attrelid = 'public.match'::regclass
                          AND attname = 'parser_version' AND NOT attisdropped
                    ) THEN
                        ALTER TABLE public.match ADD COLUMN parser_version SMALLINT NOT NULL DEFAULT 3;
                        ALTER TABLE public.match ALTER COLUMN parser_version DROP DEFAULT;
                    ELSIF EXISTS (
                        SELECT 1 FROM pg_attribute
                        WHERE attrelid = 'public.match'::regclass
                          AND attname = 'parser_version' AND NOT attisdropped AND NOT attnotnull
                    ) THEN
                        UPDATE public.match SET parser_version = 3 WHERE parser_version IS NULL;
                        ALTER TABLE public.match ALTER COLUMN parser_version SET NOT NULL;
                    END IF;
                END IF;
            END $$;
            """.trimIndent()
        )
    }

    private fun allowUnknownScoreDetails() {
        TransactionManager.current().exec(
            """
            DO $$
            DECLARE
                column_name text;
            BEGIN
                IF to_regclass('public.score') IS NOT NULL THEN
                    FOREACH column_name IN ARRAY ARRAY['mods', 'match_slot', 'match_team', 'match_pass'] LOOP
                        IF EXISTS (
                            SELECT 1 FROM pg_attribute
                            WHERE attrelid = 'public.score'::regclass
                              AND attname = column_name AND NOT attisdropped AND attnotnull
                        ) THEN
                            EXECUTE format('ALTER TABLE public.score ALTER COLUMN %I DROP NOT NULL', column_name);
                        END IF;
                    END LOOP;
                END IF;
            END $$;
            """.trimIndent()
        )
    }
}