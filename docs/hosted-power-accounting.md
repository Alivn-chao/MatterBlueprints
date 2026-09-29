# Hosted power accounting

The host withdraws consumer EU from its central power source before invoking a remote controller's custom
`onRunningTick` hook. During that hook the remote controller temporarily sees `mEUt == 0`, so GregTech's base power
check does not drain or mutate any physical remote energy hatch. The original recipe EU/t is restored immediately.

This is intentional: hosted controllers stay formed but inactive, while the host is the sole owner of their recipe
power accounting. Mutating remote hatch buffers for compatibility causes visible power-state oscillation and can race
the normal energy-net tick.

## Admission before input consumption

Standard `ProcessingLogic` jobs now receive the remaining central power budget inside their parallel supplier,
which GT resolves after machine-specific power setup and before its parallel helper consumes inputs. The budget
accounts for the host power discount and the maintenance efficiency set by standard `checkProcessing`.
Native voltage and amperage are capped to that budget, so native machine-specific overclocking and parallel
selection share the same limit. Speed bonuses still shorten the resulting duration without raising EU/t.
The previous generic single-recipe overclock estimate did not constrain native processing and could admit a
job whose actual EU/t exceeded the host supply, causing repeated energy-credit waits.

Legacy controllers without `ProcessingLogic` still use the conservative estimation path and require separate
compatibility validation. Existing saved jobs retain their recorded EU/t and duration; supply interruptions and
previously oversized jobs can still wait for energy. Central hatch capacity is rated capacity, not a guarantee
that upstream generators or cables continuously deliver that power.
