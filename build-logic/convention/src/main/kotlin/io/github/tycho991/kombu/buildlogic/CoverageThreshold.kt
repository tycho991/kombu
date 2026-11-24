package io.github.tycho991.kombu.buildlogic

/** Minimum coverage of one module in percent; `null` means the unit is not enforced. */
internal data class CoverageThreshold(val lines: Int, val branches: Int?)

/** Per-layer thresholds from the code-quality spec ("Enforced test coverage"). */
@Suppress("MagicNumber") // The numbers are the thresholds defined by the code-quality spec.
internal val COVERAGE_THRESHOLDS: Map<ModuleLayer, CoverageThreshold> =
    mapOf(
        ModuleLayer.PURE to CoverageThreshold(lines = 90, branches = 80),
        ModuleLayer.TOOLING to CoverageThreshold(lines = 90, branches = 80),
        ModuleLayer.DATA to CoverageThreshold(lines = 80, branches = 70),
        ModuleLayer.SYNC to CoverageThreshold(lines = 80, branches = 70),
        ModuleLayer.APP to CoverageThreshold(lines = 70, branches = null),
    )
