$ErrorActionPreference = "Stop"
$container = "supabase_db_kipu"
$fixture = Join-Path $PSScriptRoot "debt_sprint5_migration_fixture.sql"
$migrationTest = Join-Path $PSScriptRoot "debt_sprint5_migration_test.sql"

npx --yes supabase@latest db reset --local --version 20261007144500
if ($LASTEXITCODE -ne 0) { throw "Unable to create the pre-S5 local database state." }

Get-Content $fixture -Raw | docker exec -i $container psql -U postgres -d postgres -v ON_ERROR_STOP=1
if ($LASTEXITCODE -ne 0) { throw "Unable to seed the legacy migration fixture." }

$previousErrorActionPreference = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$preflightOutput = npx --yes supabase@latest migration up --local 2>&1
$preflightExitCode = $LASTEXITCODE
$ErrorActionPreference = $previousErrorActionPreference
if ($preflightExitCode -eq 0 -or ($preflightOutput -join "`n") -notmatch "DEBT_EVENT_SEMANTICS_UNRESOLVED") {
    throw "Expected the S5 migration to stop on ambiguous legacy ADJUSTMENT/FORGIVENESS semantics. Output: $($preflightOutput -join "`n")"
}

$unmodified = docker exec $container psql -U postgres -d postgres -At -c "SELECT (SELECT count(*) FROM public.debt_events WHERE debt_id='55000000-0000-4000-8000-000000000003') || ':' || (SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='debt_events' AND column_name='principal_delta_minor');"
if ($LASTEXITCODE -ne 0 -or $unmodified.Trim() -ne "3:0") {
    throw "The failed preflight must roll back all schema changes and preserve the three legacy events; got '$unmodified'."
}

$resolution = @"
ALTER TABLE public.debt_events ADD COLUMN IF NOT EXISTS principal_delta_minor bigint;
UPDATE public.debt_events SET principal_delta_minor = -300
WHERE id = '55000000-0000-4000-8000-000000000007';
UPDATE public.debt_events SET principal_delta_minor = -200
WHERE id = '55000000-0000-4000-8000-000000000008';
"@
docker exec $container psql -U postgres -d postgres -v ON_ERROR_STOP=1 -c $resolution
if ($LASTEXITCODE -ne 0) { throw "Unable to apply the explicit legacy event resolution for the retry." }

$previousErrorActionPreference = $ErrorActionPreference
$ErrorActionPreference = "Continue"
npx --yes supabase@latest migration up --local
$migrationExitCode = $LASTEXITCODE
$ErrorActionPreference = $previousErrorActionPreference
if ($migrationExitCode -ne 0) { throw "S5 migrations did not apply after explicit legacy event resolution." }

docker exec $container psql -U postgres -d postgres -v ON_ERROR_STOP=1 -c "CREATE EXTENSION IF NOT EXISTS pgtap WITH SCHEMA extensions;" | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Unable to install pgTAP in the local verification database." }
$testSql = "SET search_path TO public, extensions;`n" + (Get-Content $migrationTest -Raw)
$testSql | docker exec -i $container psql -U postgres -d postgres -v ON_ERROR_STOP=1
if ($LASTEXITCODE -ne 0) { throw "Post-migration preservation assertions failed." }

Write-Output "PASS: ambiguous legacy events blocked the upgrade without partial DDL; explicit deltas enabled a data-preserving retry."
