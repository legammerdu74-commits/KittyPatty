package be.chatpate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FoodAllocatorTest {
    @Test
    fun restrictedFoodDoesNotFallBackToSharedFoodWhenOutOfStock() {
        val products = listOf(
            product(id = "shared", stock = 4),
            product(id = "special", stock = 0, catIds = listOf("cat-1")),
        )

        val allocations = FoodAllocator.allocate(
            catIds = listOf("cat-1", "cat-2"),
            products = products,
            remainingServings = FoodAllocator.initialStock(products),
        )

        assertEquals("special", allocations[0].productId)
        assertFalse(allocations[0].inStock)
        assertEquals("shared", allocations[1].productId)
        assertTrue(allocations[1].inStock)
    }

    @Test
    fun sharedStockIsConsumedOncePerCatAndNeverGoesNegative() {
        val products = listOf(product(id = "shared", stock = 1))
        val remaining = FoodAllocator.initialStock(products)

        val allocations = FoodAllocator.allocate(
            catIds = listOf("cat-1", "cat-2"),
            products = products,
            remainingServings = remaining,
        )

        assertTrue(allocations[0].inStock)
        assertFalse(allocations[1].inStock)
        assertEquals(0L, remaining["shared"])
    }

    @Test
    fun putAsideProductsAreNotAllocated() {
        val products = listOf(
            product(id = "aside", stock = 20).copy(putAside = true),
            product(id = "available", stock = 1),
        )

        val allocations = FoodAllocator.allocate(
            catIds = listOf("cat-1"),
            products = products,
            remainingServings = FoodAllocator.initialStock(products),
        )

        assertEquals("available", allocations.single().productId)
    }

    @Test
    fun equallyStockedFoodsAreConsumedInBalancedTurns() {
        val products = listOf(
            product(id = "food-a", stock = 2),
            product(id = "food-b", stock = 2),
        )
        val remaining = FoodAllocator.initialStock(products)
        val selected = (1..4).map {
            FoodAllocator.allocate(
                catIds = listOf("cat-1"),
                products = products,
                remainingServings = remaining,
            ).single().productId
        }

        assertEquals(listOf("food-a", "food-b", "food-a", "food-b"), selected)
    }

    @Test
    fun allocationAvoidsRepeatingThePreviousFoodAndBalancesUsage() {
        val products = listOf(
            product(id = "food-a", stock = 4),
            product(id = "food-b", stock = 4),
        )
        val remaining = FoodAllocator.initialStock(products)
        val rotation = FoodRotationState().apply {
            record("cat-1", "food-a")
            record("cat-1", "food-a")
            record("cat-1", "food-b")
        }

        val selected = (1..4).map {
            FoodAllocator.allocate(
                catIds = listOf("cat-1"),
                products = products,
                remainingServings = remaining,
                rotation = rotation,
            ).single().productId
        }

        assertEquals(listOf("food-a", "food-b", "food-a", "food-b"), selected)
    }

    @Test
    fun onlyAvailableFoodMayRepeatWhenAlternativesAreOutOfStock() {
        val products = listOf(
            product(id = "food-a", stock = 2),
            product(id = "food-b", stock = 0),
        )
        val remaining = FoodAllocator.initialStock(products)
        val rotation = FoodRotationState().apply { record("cat-1", "food-a") }

        val allocation = FoodAllocator.allocate(
            catIds = listOf("cat-1"),
            products = products,
            remainingServings = remaining,
            rotation = rotation,
        ).single()

        assertEquals("food-a", allocation.productId)
        assertTrue(allocation.inStock)
    }

    @Test
    fun stockDepletionForecastFindsTheFirstDayWithoutFood() {
        val today = LocalDate.parse("2026-01-01")
        val product = product(id = "food-a", stock = 2)
        val plan = Plan(
            id = "daily",
            timeMin = 480,
            everyDays = 1,
            start = today.toString(),
            pate = Pate.POULET.key,
            qty = "1 portion / chat",
            catIds = listOf("cat-1"),
            autoAllocate = true,
        )
        val now = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val depletionDates = Planner.stockDepletionDates(
            state = AppState(plans = listOf(plan), products = listOf(product)),
            today = today,
            through = today.plusDays(5),
            now = now,
        )

        assertEquals(today.plusDays(2), depletionDates["cat-1"])
    }

    @Test
    fun untrackedFoodDoesNotLookLikeAnEmptyStockForecast() {
        val today = LocalDate.parse("2026-01-01")
        val product = product(id = "food-a", stock = 0).copy(stockCount = null)
        val plan = Plan(
            id = "daily",
            timeMin = 480,
            everyDays = 1,
            start = today.toString(),
            pate = Pate.POULET.key,
            qty = "1 portion / chat",
            catIds = listOf("cat-1"),
            autoAllocate = true,
        )
        val now = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val depletionDates = Planner.stockDepletionDates(
            state = AppState(plans = listOf(plan), products = listOf(product)),
            today = today,
            through = today.plusDays(5),
            now = now,
        )

        assertFalse(depletionDates.containsKey("cat-1"))
    }

    @Test
    fun fixedFoodPatternSkipsProductsPutAside() {
        val products = listOf(
            product(id = "aside", stock = 20).copy(putAside = true),
            product(id = "available", stock = 20),
        )
        val plan = Plan(
            id = "plan",
            timeMin = 480,
            everyDays = 1,
            start = "2026-01-01",
            pate = Pate.POULET.key,
            qty = "",
            patternId = "rotation",
        )

        val selected = plan.foodProductOn(
            LocalDate.parse("2026-01-01"),
            listOf(FoodPattern("rotation", "Rotation", listOf("aside", "available"))),
            products,
        )

        assertEquals("available", selected?.id)
    }

    private fun product(
        id: String,
        stock: Int,
        catIds: List<String> = emptyList(),
    ) = FoodProduct(
        id = id,
        name = id,
        kind = "poulet",
        brand = "",
        packPrice = "",
        stockCount = stock,
        catIds = catIds,
    )
}
