package osu.mps.data.migration

import org.jetbrains.exposed.sql.transactions.TransactionManager

object DatabaseMigrations {

    // Each migration must be safe to run again when the application starts.
    fun run() {
        addMatchParserVersion()
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
}