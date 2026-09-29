# Phase 6 — SIMD values and lifted execution

[Roadmap index](../PLAN.md)

## Phase 6 — SIMD values and lifted execution

- Introduce fixed-arity `Simd native Scalar` and `Simd lanes Scalar` contracts and Boolean masks.
- Lift supported pure numeric scalar operations and functions lane-wise. Implement mask selection so
  vector conditionals do not inherit scalar lazy-branch semantics incorrectly.
- Implement explicit `::` SIMD application as a semantic requirement: either produce verified SIMD
  execution or a clear compile-time diagnostic explaining impurity, unsupported operations, lane
  mismatch, or target limitations.
- Support composition, partial application, lambda mapping, and reductions using the same callable
  metadata. Implement inherited environment-scoped `simdOption grouping`, defaulting to
  language-defined `pairwise` and allowing target-dependent `hardware`; keep strict left grouping
  available through scalar `fold`.
- Add aligned/unaligned load/store lowering internally while exposing ordinary collection APIs;
  provide a portable fallback only where it preserves the explicit `::` contract.
- Test multiple lane widths and hardware capability profiles in interpreter/emulation and compiler
  modes; never make program meaning depend on host vector width.

