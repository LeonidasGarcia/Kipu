param(
    [string]$ContainerName = "supabase_db_kipu"
)

$ErrorActionPreference = "Stop"
$owner = "67000000-0000-4000-8000-000000000001"
$account = "67000000-0000-4000-8000-000000000002"
$existingDebt = "67000000-0000-4000-8000-000000000003"
$debtA = "67000000-0000-4000-8000-000000000004"
$debtB = "67000000-0000-4000-8000-000000000005"
$dollar = '$'

$setup = @"
DELETE FROM auth.users WHERE id = '$owner';
INSERT INTO auth.users (id, email) VALUES ('$owner', 'debt-quota-race@kipu.test');
INSERT INTO public.accounts (id, user_id, name, account_type, currency_code)
VALUES ('$account', '$owner', 'Quota race', 'SAVINGS', 'PEN');
INSERT INTO public.debts (id, user_id, obligation_type, counterparty_name, total_minor, currency_code)
VALUES ('$existingDebt', '$owner', 'PAYABLE', 'Existing debt', 1000, 'PEN');
CREATE OR REPLACE FUNCTION public.debt_quota_race_pause()
RETURNS trigger LANGUAGE plpgsql AS ${dollar}${dollar}
BEGIN
    IF NEW.user_id = '$owner' AND NEW.id IN ('$debtA', '$debtB') THEN
        PERFORM pg_sleep(1.5);
    END IF;
    RETURN NEW;
END;
${dollar}${dollar};
DROP TRIGGER IF EXISTS debt_quota_race_pause ON public.debts;
CREATE TRIGGER debt_quota_race_pause BEFORE INSERT ON public.debts
FOR EACH ROW EXECUTE FUNCTION public.debt_quota_race_pause();
"@

docker exec $ContainerName psql -U postgres -d postgres -v ON_ERROR_STOP=1 -c $setup | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Unable to prepare the local quota concurrency fixture." }

$operations = @(
    @{ Operation = "67000000-0000-4000-8000-000000000006"; Debt = $debtA },
    @{ Operation = "67000000-0000-4000-8000-000000000007"; Debt = $debtB }
)
$jobs = foreach ($operation in $operations) {
    Start-Job -ArgumentList $ContainerName, $owner, $operation.Operation, $operation.Debt -ScriptBlock {
        param($container, $user, $operationId, $debtId)
        $hashInput = "$operationId|$debtId|500|PEN|HISTORICAL"
        $sha256 = [System.Security.Cryptography.SHA256]::Create()
        $hashBytes = $sha256.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($hashInput))
        $requestHash = [System.BitConverter]::ToString($hashBytes).Replace("-", "").ToLowerInvariant()
        $request = @{
            contract_version = 1
            operation_id = $operationId
            request_hash = $requestHash
            debt_id = $debtId
            obligation_type = "PAYABLE"
            counterparty_name = "Concurrent debt"
            total_minor = 500
            currency_code = "PEN"
            opened_on = "2026-10-08"
            opening_mode = "HISTORICAL"
        } | ConvertTo-Json -Compress
        $requestSql = $request.Replace("'", "''")
        $sql = "SET ROLE authenticated; SELECT set_config('request.jwt.claim.sub', '$user', false); SELECT public.open_debt_v1('$requestSql'::jsonb);"
        $output = $sql | docker exec -i $container psql -U postgres -d postgres -v ON_ERROR_STOP=1 2>&1
        [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = ($output -join "`n") }
    }
}

$results = $jobs | Wait-Job | Receive-Job
$jobs | Remove-Job

docker exec $ContainerName psql -U postgres -d postgres -v ON_ERROR_STOP=1 -c "DROP TRIGGER IF EXISTS debt_quota_race_pause ON public.debts; DROP FUNCTION IF EXISTS public.debt_quota_race_pause();" | Out-Null

$applied = @($results | Where-Object { $_.ExitCode -eq 0 -and $_.Output -match 'APPLIED' }).Count
$quotaRejected = @($results | Where-Object { $_.ExitCode -ne 0 -and $_.Output -match 'FREE_DEBT_QUOTA_EXCEEDED' }).Count
$active = docker exec $ContainerName psql -U postgres -d postgres -At -c "SELECT count(*) FROM public.debts WHERE user_id='$owner' AND status='ACTIVE' AND deleted_at IS NULL;"
if ($LASTEXITCODE -ne 0) { throw "Unable to read the local quota concurrency result." }

$cleanup = "DROP TRIGGER IF EXISTS debt_quota_race_pause ON public.debts; DROP FUNCTION IF EXISTS public.debt_quota_race_pause(); DELETE FROM auth.users WHERE id='$owner';"
docker exec $ContainerName psql -U postgres -d postgres -v ON_ERROR_STOP=1 -c $cleanup | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Unable to remove the synthetic local quota fixture." }

if ($applied -ne 1 -or $quotaRejected -ne 1 -or [int]$active -ne 2) {
    $results | ForEach-Object { Write-Error $_.Output }
    throw "Expected one concurrent opening to apply, one to hit the combined Free quota, and two active debts; got applied=$applied rejected=$quotaRejected active=$active."
}

Write-Output "PASS: simultaneous openings were serialized; one applied, one was quota-rejected, active=$active."
