package com.example.data.local

data class PlanStageSpec(
    val price: Double,
    val dailyEarning: Double,
    val totalRevenue: Double,
    val durationHours: Int
)

object PlanRotationSchedule {
    const val HOUR_MS = 60L * 60L * 1000L

    private val productStages: Map<String, List<PlanStageSpec>> = mapOf(
        "prod_1" to listOf(
            PlanStageSpec(price = 700.0, dailyEarning = 120.0, totalRevenue = 3600.0, durationHours = 24),
            PlanStageSpec(price = 900.0, dailyEarning = 155.0, totalRevenue = 4650.0, durationHours = 48),
            PlanStageSpec(price = 1200.0, dailyEarning = 205.0, totalRevenue = 6150.0, durationHours = 72)
        ),
        "prod_2" to listOf(
            PlanStageSpec(price = 1500.0, dailyEarning = 240.0, totalRevenue = 7200.0, durationHours = 36),
            PlanStageSpec(price = 1800.0, dailyEarning = 300.0, totalRevenue = 9000.0, durationHours = 54),
            PlanStageSpec(price = 2300.0, dailyEarning = 385.0, totalRevenue = 11550.0, durationHours = 72)
        ),
        "prod_3" to listOf(
            PlanStageSpec(price = 2000.0, dailyEarning = 340.0, totalRevenue = 10200.0, durationHours = 48),
            PlanStageSpec(price = 4000.0, dailyEarning = 690.0, totalRevenue = 20700.0, durationHours = 64),
            PlanStageSpec(price = 4800.0, dailyEarning = 830.0, totalRevenue = 24900.0, durationHours = 80)
        ),
        "prod_4" to listOf(
            PlanStageSpec(price = 3000.0, dailyEarning = 520.0, totalRevenue = 15600.0, durationHours = 60),
            PlanStageSpec(price = 5500.0, dailyEarning = 960.0, totalRevenue = 28800.0, durationHours = 72),
            PlanStageSpec(price = 6800.0, dailyEarning = 1190.0, totalRevenue = 35700.0, durationHours = 80)
        )
    )

    private val vipStages: Map<String, List<PlanStageSpec>> = mapOf(
        "vip_1" to listOf(
            PlanStageSpec(price = 800.0, dailyEarning = 140.0, totalRevenue = 4200.0, durationHours = 24),
            PlanStageSpec(price = 1050.0, dailyEarning = 185.0, totalRevenue = 5550.0, durationHours = 48),
            PlanStageSpec(price = 1350.0, dailyEarning = 240.0, totalRevenue = 7200.0, durationHours = 72)
        ),
        "vip_2" to listOf(
            PlanStageSpec(price = 1200.0, dailyEarning = 210.0, totalRevenue = 6300.0, durationHours = 36),
            PlanStageSpec(price = 1650.0, dailyEarning = 290.0, totalRevenue = 8700.0, durationHours = 56),
            PlanStageSpec(price = 2100.0, dailyEarning = 370.0, totalRevenue = 11100.0, durationHours = 76)
        ),
        "vip_3" to listOf(
            PlanStageSpec(price = 1500.0, dailyEarning = 260.0, totalRevenue = 7800.0, durationHours = 48),
            PlanStageSpec(price = 1800.0, dailyEarning = 315.0, totalRevenue = 9450.0, durationHours = 64),
            PlanStageSpec(price = 2500.0, dailyEarning = 440.0, totalRevenue = 13200.0, durationHours = 80)
        ),
        "vip_4" to listOf(
            PlanStageSpec(price = 2000.0, dailyEarning = 350.0, totalRevenue = 10500.0, durationHours = 54),
            PlanStageSpec(price = 4000.0, dailyEarning = 700.0, totalRevenue = 21000.0, durationHours = 72),
            PlanStageSpec(price = 4800.0, dailyEarning = 840.0, totalRevenue = 25200.0, durationHours = 80)
        ),
        "vip_5" to listOf(
            PlanStageSpec(price = 3000.0, dailyEarning = 540.0, totalRevenue = 16200.0, durationHours = 60),
            PlanStageSpec(price = 5500.0, dailyEarning = 990.0, totalRevenue = 29700.0, durationHours = 72),
            PlanStageSpec(price = 7000.0, dailyEarning = 1260.0, totalRevenue = 37800.0, durationHours = 80)
        )
    )

    fun nextProductStageSpec(product: ProductEntity): PlanStageSpec? {
        val stages = productStages[product.id] ?: return null
        val nextIndex = (product.rotationStage + 1) % stages.size
        return stages[nextIndex]
    }

    fun nextVipStageSpec(plan: MembershipPlanEntity): PlanStageSpec? {
        val stages = vipStages[plan.id] ?: return null
        val nextIndex = (plan.rotationStage + 1) % stages.size
        return stages[nextIndex]
    }

    fun syncProductIfNeeded(product: ProductEntity, nowMs: Long): ProductEntity? {
        val stages = productStages[product.id]
        if (stages == null) {
            if (product.nextSwitchAt <= 0L || nowMs >= product.nextSwitchAt) {
                val hours = product.rotationHours.coerceIn(24, 80)
                return product.copy(
                    rotationHours = hours,
                    nextSwitchAt = nowMs + hours * HOUR_MS
                )
            }
            return null
        }

        if (product.nextSwitchAt <= 0L) {
            val matchedIndex = stages.indexOfFirst { kotlin.math.abs(it.price - product.price) < 1.0 }
                .let { if (it >= 0) it else product.rotationStage.coerceIn(0, stages.lastIndex) }
            val spec = stages[matchedIndex]
            return product.copy(
                price = spec.price,
                dailyEarning = spec.dailyEarning,
                totalRevenue = spec.totalRevenue,
                specifications = buildProductSpecs(product, spec),
                rotationStage = matchedIndex,
                rotationHours = spec.durationHours,
                nextSwitchAt = nowMs + spec.durationHours * HOUR_MS
            )
        }

        if (nowMs < product.nextSwitchAt) return null

        var stageIdx = product.rotationStage.coerceIn(0, stages.lastIndex)
        var switchAt = product.nextSwitchAt
        var guard = 0
        while (nowMs >= switchAt && guard < 120) {
            stageIdx = (stageIdx + 1) % stages.size
            switchAt += stages[stageIdx].durationHours * HOUR_MS
            guard++
        }
        val spec = stages[stageIdx]
        if (switchAt <= nowMs) {
            switchAt = nowMs + spec.durationHours * HOUR_MS
        }
        return product.copy(
            price = spec.price,
            dailyEarning = spec.dailyEarning,
            totalRevenue = spec.totalRevenue,
            specifications = buildProductSpecs(product, spec),
            rotationStage = stageIdx,
            rotationHours = spec.durationHours,
            nextSwitchAt = switchAt
        )
    }

    fun syncVipPlanIfNeeded(plan: MembershipPlanEntity, nowMs: Long): MembershipPlanEntity? {
        val stages = vipStages[plan.id]
        if (stages == null) {
            if (plan.nextSwitchAt <= 0L || nowMs >= plan.nextSwitchAt) {
                val hours = plan.rotationHours.coerceIn(24, 80)
                return plan.copy(
                    rotationHours = hours,
                    nextSwitchAt = nowMs + hours * HOUR_MS
                )
            }
            return null
        }

        if (plan.nextSwitchAt <= 0L) {
            val matchedIndex = stages.indexOfFirst { kotlin.math.abs(it.price - plan.price) < 1.0 }
                .let { if (it >= 0) it else plan.rotationStage.coerceIn(0, stages.lastIndex) }
            val spec = stages[matchedIndex]
            return plan.copy(
                price = spec.price,
                dailyEarning = spec.dailyEarning,
                totalRevenue = spec.totalRevenue,
                benefits = buildVipBenefits(plan, spec),
                rotationStage = matchedIndex,
                rotationHours = spec.durationHours,
                nextSwitchAt = nowMs + spec.durationHours * HOUR_MS
            )
        }

        if (nowMs < plan.nextSwitchAt) return null

        var stageIdx = plan.rotationStage.coerceIn(0, stages.lastIndex)
        var switchAt = plan.nextSwitchAt
        var guard = 0
        while (nowMs >= switchAt && guard < 120) {
            stageIdx = (stageIdx + 1) % stages.size
            switchAt += stages[stageIdx].durationHours * HOUR_MS
            guard++
        }
        val spec = stages[stageIdx]
        if (switchAt <= nowMs) {
            switchAt = nowMs + spec.durationHours * HOUR_MS
        }
        return plan.copy(
            price = spec.price,
            dailyEarning = spec.dailyEarning,
            totalRevenue = spec.totalRevenue,
            benefits = buildVipBenefits(plan, spec),
            rotationStage = stageIdx,
            rotationHours = spec.durationHours,
            nextSwitchAt = switchAt
        )
    }

    private fun buildProductSpecs(product: ProductEntity, spec: PlanStageSpec): String {
        val prefix = product.specifications.substringBefore("Daily Earning:").trimEnd(' ', '•')
        return if (prefix.isNotBlank()) {
            "$prefix • Daily Earning: ₹${spec.dailyEarning.toInt()}"
        } else {
            "Cycle: ${product.cycleDays} Days • Daily Earning: ₹${spec.dailyEarning.toInt()}"
        }
    }

    private fun buildVipBenefits(plan: MembershipPlanEntity, spec: PlanStageSpec): String {
        val rest = plan.benefits.lineSequence().drop(1).joinToString("\n")
        val head = "• Daily Earning: ₹${spec.dailyEarning.toInt()} for ${plan.validityDays} days (₹${spec.totalRevenue.toInt()} Total)"
        return if (rest.isNotBlank()) "$head\n$rest" else head
    }
}
