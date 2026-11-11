package be.chatpate

class FoodRotationState {
    val lastProductByCat = mutableMapOf<String, String>()
    private val usageByCat = mutableMapOf<String, MutableMap<String, Int>>()

    fun uses(catId: String, productId: String): Int =
        usageByCat[catId]?.get(productId) ?: 0

    fun record(catId: String, productId: String) {
        if (productId.isBlank()) return
        val usage = usageByCat.getOrPut(catId) { mutableMapOf() }
        usage[productId] = (usage[productId] ?: 0) + 1
        lastProductByCat[catId] = productId
    }
}

object FoodAllocator {
    fun knownStockEmpty(
        catId: String,
        products: List<FoodProduct>,
        remainingServings: Map<String, Long>,
    ): Boolean? {
        val applicable = products.filterNot { it.deleted || it.putAside }
        val restricted = applicable.filter { catId in it.catIds }
        val eligible = if (restricted.isNotEmpty()) restricted else applicable.filter { it.catIds.isEmpty() }
        if (eligible.any { it.availableServings == null }) return null
        return eligible.none { (remainingServings[it.id] ?: it.availableServings ?: 0L) > 0L }
    }

    fun allocate(
        catIds: List<String>,
        products: List<FoodProduct>,
        remainingServings: MutableMap<String, Long>,
        warningThreshold: Int = 0,
        lowThreshold: Int = 0,
        rotation: FoodRotationState = FoodRotationState(),
    ): List<FoodAllocation> = catIds.distinct().map { catId ->
        val applicable = products.filterNot { it.deleted || it.putAside }
        val restricted = applicable.filter { catId in it.catIds }
        val eligible = if (restricted.isNotEmpty()) restricted else applicable.filter { it.catIds.isEmpty() }
        val tracked = eligible.filter { it.availableServings != null }
        val available = tracked.filter { (remainingServings[it.id] ?: it.availableServings ?: 0L) > 0L }
        val candidates = available.ifEmpty { tracked }
        val previousProductId = rotation.lastProductByCat[catId]
        val withoutRepeat = if (candidates.size > 1 && candidates.any { it.id != previousProductId }) {
            candidates.filterNot { it.id == previousProductId }
        } else {
            candidates
        }
        val product = withoutRepeat.minWithOrNull(
            compareBy<FoodProduct> { rotation.uses(catId, it.id) }
                .thenByDescending { remainingServings[it.id] ?: it.availableServings ?: 0L }
                .thenBy { products.indexOf(it) },
        )

        if (product == null) {
            FoodAllocation(catId = catId, productId = "", inStock = false)
        } else {
            val remaining = remainingServings[product.id] ?: product.availableServings ?: 0L
            val inStock = remaining > 0L
            if (inStock) remainingServings[product.id] = remaining - 1L
            rotation.record(catId, product.id)
            FoodAllocation(
                catId = catId,
                productId = product.id,
                inStock = inStock,
                stockWarning = inStock && remaining - 1L <= warningThreshold,
                lowStock = inStock && remaining - 1L <= lowThreshold,
            )
        }
    }

    fun initialStock(products: List<FoodProduct>): MutableMap<String, Long> =
        products.mapNotNull { product ->
            product.availableServings?.let { product.id to it }
        }.toMap().toMutableMap()
}
