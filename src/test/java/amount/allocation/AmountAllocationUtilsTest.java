package amount.allocation;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import java.math.BigDecimal;
import java.util.function.BiFunction;

import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class AmountAllocationUtilsTest {

    private MockedStatic<Math> mockedMath;

    // 提升为类成员变量
    private BigDecimal totalAmount;
    private int quantity;

    @Before
    public void setUp() {
        // 初始化BigDecimal数值
        totalAmount = new BigDecimal("100.00");
        quantity = 3;
    }

    @After
    public void tearDown() {
        if (mockedMath != null) {
            mockedMath.close();
        }
    }

    /**
     * 测试 allocate 方法的正常情况
     */
    @Test
    public void testAllocate_NormalCase() {
        BiFunction<BigDecimal, Integer, BigDecimal[]> strategy = AmountAllocationUtils.evenAllocation(0);

        BigDecimal[] result = AmountAllocationUtils.allocate(totalAmount, quantity, strategy);

        assertNotNull(result);
        assertEquals(quantity, result.length);
        assertEquals(new BigDecimal("100.00"), result[0].add(result[1]).add(result[2]));
    }

    /**
     * 测试 allocate 方法当总金额为负时抛出异常
     */
    @Test(expected = IllegalArgumentException.class)
    public void testAllocate_NegativeTotalAmount() {
        BiFunction<BigDecimal, Integer, BigDecimal[]> strategy = AmountAllocationUtils.evenAllocation(0);
        AmountAllocationUtils.allocate(new BigDecimal("-100.00"), 3, strategy);
    }

    /**
     * 测试 allocate 方法当总金额为null时抛出异常
     */
    @Test(expected = IllegalArgumentException.class)
    public void testAllocate_NullTotalAmount() {
        BiFunction<BigDecimal, Integer, BigDecimal[]> strategy = AmountAllocationUtils.evenAllocation(0);
        AmountAllocationUtils.allocate(null, 3, strategy);
    }

    /**
     * 测试 allocate 方法当数量非正时抛出异常
     */
    @Test(expected = IllegalArgumentException.class)
    public void testAllocate_InvalidQuantity() {
        BiFunction<BigDecimal, Integer, BigDecimal[]> strategy = AmountAllocationUtils.evenAllocation(0);
        AmountAllocationUtils.allocate(new BigDecimal("100.00"), 0, strategy);
    }

    /**
     * 测试 evenAllocation 策略的均摊逻辑
     */
    @Test
    public void testEvenAllocation_StrategyLogic() {
        int remainderPosition = 1;
        BiFunction<BigDecimal, Integer, BigDecimal[]> strategy = AmountAllocationUtils.evenAllocation(remainderPosition);

        BigDecimal[] result = strategy.apply(totalAmount, quantity);

        assertNotNull(result);
        assertEquals(quantity, result.length);

        // 验证均摊计算
        assertEquals(new BigDecimal("33.33"), result[0]);
        assertEquals(new BigDecimal("33.34"), result[1]);
        assertEquals(new BigDecimal("33.33"), result[2]); // 零头在第3个位置

        // 验证总和
        assertEquals(totalAmount, result[0].add(result[1]).add(result[2]));
    }

    /**
     * 测试 weightedAllocation 策略的基本功能
     */
    @Test
    public void testWeightedAllocation_BasicFunctionality() {
        BigDecimal[] weights = {
                new BigDecimal("1"),
                new BigDecimal("2"),
                new BigDecimal("3")
        };
        int remainderPosition = 1;

        BiFunction<BigDecimal, Integer, BigDecimal[]> strategy = AmountAllocationUtils.weightedAllocation(weights, remainderPosition);

        BigDecimal[] result = strategy.apply(totalAmount, quantity);

        assertNotNull(result);
        assertEquals(quantity, result.length);

        // 验证总和
        assertEquals(totalAmount, result[0].add(result[1]).add(result[2]));
    }

    /**
     * 测试 weightedAllocation 当权重数组为null时抛出异常
     */
    @Test(expected = IllegalArgumentException.class)
    public void testWeightedAllocation_NullWeights() {
        BiFunction<BigDecimal, Integer, BigDecimal[]> strategy = AmountAllocationUtils.weightedAllocation(null, 0);
        strategy.apply(new BigDecimal("100.00"), 3);
    }

    /**
     * 测试 weightedAllocation 当权重数组长度不匹配时抛出异常
     */
    @Test(expected = IllegalArgumentException.class)
    public void testWeightedAllocation_InvalidWeightsLength() {
        BigDecimal[] weights = {new BigDecimal("1"), new BigDecimal("2")};
        BiFunction<BigDecimal, Integer, BigDecimal[]> strategy = AmountAllocationUtils.weightedAllocation(weights, 0);
        strategy.apply(new BigDecimal("100.00"), 3);
    }

    /**
     * 测试 weightedAllocation 当总权重为0时抛出异常
     */
    @Test(expected = IllegalArgumentException.class)
    public void testWeightedAllocation_ZeroTotalWeight() {
        BigDecimal[] weights = {
                new BigDecimal("0"),
                new BigDecimal("0"),
                new BigDecimal("0")
        };
        BiFunction<BigDecimal, Integer, BigDecimal[]> strategy = AmountAllocationUtils.weightedAllocation(weights, 0);
        strategy.apply(new BigDecimal("100.00"), 3);
    }

    /**
     * 测试 remainderPositionRandom 方法返回值范围正确
     */
    @Test
    public void testRemainderPositionRandom_ValidRange() {
        int quantity = 5;
        int result = AmountAllocationUtils.remainderPositionRandom(quantity);
        assertTrue(result >= 0 && result < quantity);
    }


    /**
     * 测试 remainderPositionMax 方法能找到最大值位置
     */
    @Test
    public void testRemainderPositionMax_CorrectPosition() {
        BigDecimal[] amounts = {
                new BigDecimal("10.00"),
                new BigDecimal("20.00"),
                new BigDecimal("15.00")
        };

        int result = AmountAllocationUtils.remainderPositionMax(amounts);
        assertEquals(1, result); // 最大值在索引1
    }

    /**
     * 测试 remainderPositionMax 方法当所有金额相等时返回第一个位置
     */
    @Test
    public void testRemainderPositionMax_AllEqual() {
        BigDecimal[] amounts = {
                new BigDecimal("10.00"),
                new BigDecimal("10.00"),
                new BigDecimal("10.00")
        };

        int result = AmountAllocationUtils.remainderPositionMax(amounts);
        assertEquals(0, result); // 所有相等时返回第一个位置
    }

    /**
     * 测试 remainderPositionMin 方法能找到最小值位置
     */
    @Test
    public void testRemainderPositionMin_CorrectPosition() {
        BigDecimal[] amounts = {
                new BigDecimal("10.00"),
                new BigDecimal("20.00"),
                new BigDecimal("5.00")
        };

        int result = AmountAllocationUtils.remainderPositionMin(amounts);
        assertEquals(2, result); // 最小值在索引2
    }

    /**
     * 测试 remainderPositionMin 方法当所有金额相等时返回第一个位置
     */
    @Test
    public void testRemainderPositionMin_AllEqual() {
        BigDecimal[] amounts = {
                new BigDecimal("10.00"),
                new BigDecimal("10.00"),
                new BigDecimal("10.00")
        };

        int result = AmountAllocationUtils.remainderPositionMin(amounts);
        assertEquals(0, result); // 所有相等时返回第一个位置
    }
}
