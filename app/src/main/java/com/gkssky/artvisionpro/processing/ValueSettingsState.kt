package com.gkssky.artvisionpro.processing

data class ValueSettingsState(
    val simplifyAmount: Int = DEFAULT_SIMPLIFY_AMOUNT,
    val noiseRemovalAmount: Int = DEFAULT_NOISE_REMOVAL_AMOUNT,
    val edgeSmoothnessAmount: Int = DEFAULT_EDGE_SMOOTHNESS_AMOUNT,
) {
    fun bounded() = copy(
        simplifyAmount = simplifyAmount.coerceIn(0, 100),
        noiseRemovalAmount = noiseRemovalAmount.coerceIn(0, 100),
        edgeSmoothnessAmount = edgeSmoothnessAmount.coerceIn(0, 100),
    )
    companion object {
        const val DEFAULT_SIMPLIFY_AMOUNT = 0
        const val DEFAULT_NOISE_REMOVAL_AMOUNT = 25
        const val DEFAULT_EDGE_SMOOTHNESS_AMOUNT = 30
    }
}
