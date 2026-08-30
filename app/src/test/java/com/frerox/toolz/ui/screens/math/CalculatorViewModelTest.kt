package com.frerox.toolz.ui.screens.math

import android.content.Context
import android.content.ContextWrapper
import com.frerox.toolz.data.math.MathHistory
import com.frerox.toolz.data.math.MathHistoryDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalculatorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeDao: FakeMathHistoryDao
    private lateinit var context: Context

    class FakeMathHistoryDao : MathHistoryDao {
        private val _history = MutableStateFlow<List<MathHistory>>(emptyList())
        val historyFlow = _history.asStateFlow()

        override fun getAllHistory(): Flow<List<MathHistory>> = historyFlow

        override suspend fun insert(history: MathHistory) {
            _history.value = listOf(history) + _history.value
        }

        override suspend fun clearAll() {
            _history.value = emptyList()
        }

        override suspend fun getAllHistorySync(): List<MathHistory> = _history.value

        override suspend fun insertHistories(entries: List<MathHistory>) {
            _history.value = entries + _history.value
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeDao = FakeMathHistoryDao()
        context = object : ContextWrapper(null) {}
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testBasicAdditionAndHistoryPersistence() = runTest {
        val viewModel = CalculatorViewModel(context, fakeDao)
        advanceUntilIdle()

        viewModel.onDigit("5")
        viewModel.onOperator("+")
        viewModel.onDigit("7")
        viewModel.onEquals()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("12", state.display)
        assertEquals("5+7 =", state.formula)
        assertNull(state.error)

        // Verify history persisted in Room DAO
        assertEquals(1, fakeDao.getAllHistorySync().size)
        assertEquals("5+7", fakeDao.getAllHistorySync()[0].expression)
        assertEquals("12", fakeDao.getAllHistorySync()[0].result)
        assertEquals(1, state.history.size)
        assertEquals("5+7" to "12", state.history[0])
    }

    @Test
    fun testFloatingPointPrecision() = runTest {
        val viewModel = CalculatorViewModel(context, fakeDao)
        advanceUntilIdle()

        // 0.1 + 0.2 should be 0.3 without IEEE 754 jitter (0.30000000000000004)
        viewModel.onDigit("0.1")
        viewModel.onOperator("+")
        viewModel.onDigit("0.2")
        viewModel.onEquals()
        advanceUntilIdle()

        assertEquals("0.3", viewModel.uiState.value.display)
    }

    @Test
    fun testLargeNumbers() = runTest {
        val viewModel = CalculatorViewModel(context, fakeDao)
        advanceUntilIdle()

        // 1,000,000,000 * 1,000 = 1,000,000,000,000 (1 trillion) -> clean decimal string
        viewModel.onDigit("1000000000")
        viewModel.onOperator("×")
        viewModel.onDigit("1000")
        viewModel.onEquals()
        advanceUntilIdle()

        assertEquals("1000000000000", viewModel.uiState.value.display)
    }

    @Test
    fun testScientificNotationForExtremeNumbers() = runTest {
        val viewModel = CalculatorViewModel(context, fakeDao)
        advanceUntilIdle()

        // 10^18
        viewModel.onDigit("10")
        viewModel.onOperator("^")
        viewModel.onDigit("18")
        viewModel.onEquals()
        advanceUntilIdle()

        val result = viewModel.uiState.value.display
        assertTrue(result.contains("E18") || result.contains("e18") || result.contains("E+18"))
    }

    @Test
    fun testLongCompoundOperation() = runTest {
        val viewModel = CalculatorViewModel(context, fakeDao)
        advanceUntilIdle()

        // (150 + 25) × 400 - (300 ÷ 12) + 100
        viewModel.onDigit("(")
        viewModel.onDigit("150")
        viewModel.onOperator("+")
        viewModel.onDigit("25")
        viewModel.onDigit(")")
        viewModel.onOperator("×")
        viewModel.onDigit("400")
        viewModel.onOperator("-")
        viewModel.onDigit("(")
        viewModel.onDigit("300")
        viewModel.onOperator("÷")
        viewModel.onDigit("12")
        viewModel.onDigit(")")
        viewModel.onOperator("+")
        viewModel.onDigit("100")

        assertEquals("70075", viewModel.uiState.value.liveResult)

        viewModel.onEquals()
        advanceUntilIdle()

        assertEquals("70075", viewModel.uiState.value.display)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun testImplicitMultiplicationAndPercentages() = runTest {
        val viewModel = CalculatorViewModel(context, fakeDao)
        advanceUntilIdle()

        // 5(2+3) -> 25
        viewModel.onDigit("5")
        viewModel.onDigit("(")
        viewModel.onDigit("2")
        viewModel.onOperator("+")
        viewModel.onDigit("3")
        viewModel.onDigit(")")
        viewModel.onEquals()
        advanceUntilIdle()

        assertEquals("25", viewModel.uiState.value.display)

        // 500 * 20% -> 100
        viewModel.onDigit("500")
        viewModel.onOperator("×")
        viewModel.onDigit("20")
        viewModel.onDigit("%")
        viewModel.onEquals()
        advanceUntilIdle()

        assertEquals("100", viewModel.uiState.value.display)
    }

    @Test
    fun testClearHistoryRemovesFromDao() = runTest {
        val viewModel = CalculatorViewModel(context, fakeDao)
        advanceUntilIdle()

        viewModel.onDigit("8")
        viewModel.onOperator("×")
        viewModel.onDigit("9")
        viewModel.onEquals()
        advanceUntilIdle()

        assertEquals(1, fakeDao.getAllHistorySync().size)

        viewModel.clearHistory()
        advanceUntilIdle()

        assertEquals(0, fakeDao.getAllHistorySync().size)
        assertEquals(0, viewModel.uiState.value.history.size)
    }
}
