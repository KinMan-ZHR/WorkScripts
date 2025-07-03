package amount.allocation;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class AmountAllocationUtilsTest {

    // 验证分摊结果总和是否等于总金额
    private void assertSumEquals(BigDecimal[] amounts, BigDecimal expectedSum) {
        BigDecimal actualSum = Arrays.stream(amounts)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(expectedSum, actualSum, "分摊结果总和应等于总金额");
    }

    // 验证分摊结果是否按预期分配
    private void assertAllocationEquals(BigDecimal[] actual, BigDecimal[] expected) {
        assertEquals(expected.length, actual.length, "分摊数量不匹配");
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i],
                    String.format("位置 %d 的分摊金额不匹配，预期 %s，实际 %s",
                            i, expected[i].toPlainString(), actual[i].toPlainString()));
        }
    }

    @Test
    void testEvenAllocation_NoRemainder() {
        BigDecimal total = new BigDecimal("100.00");
        int quantity = 4;

        BigDecimal[] result = AmountAllocationUtils.allocate(
                total,
                quantity,
                AmountAllocationUtils.evenAllocator(),
                AmountAllocationUtils.sequential()
        );

        assertSumEquals(result, total);
        assertAllocationEquals(result, new BigDecimal[] {
                new BigDecimal("25.00"),
                new BigDecimal("25.00"),
                new BigDecimal("25.00"),
                new BigDecimal("25.00")
        });
    }

    @Test
    void testEvenAllocation_WithRemainder() {
        BigDecimal total = new BigDecimal("100.01");
        int quantity = 4;

        BigDecimal[] result = AmountAllocationUtils.allocate(
                total,
                quantity,
                AmountAllocationUtils.evenAllocator(),
                AmountAllocationUtils.sequential()
        );

        assertSumEquals(result, total);
        assertAllocationEquals(result, new BigDecimal[] {
                new BigDecimal("25.01"),
                new BigDecimal("25.00"),
                new BigDecimal("25.00"),
                new BigDecimal("25.00")
        });
    }

    @Test
    void testWeightedAllocation_Simple() {
        BigDecimal total = new BigDecimal("100.00");
        int quantity = 3;
        BigDecimal[] weights = {
                new BigDecimal("1"),
                new BigDecimal("2"),
                new BigDecimal("3")
        };

        BigDecimal[] result = AmountAllocationUtils.allocate(
                total,
                quantity,
                AmountAllocationUtils.weightedAllocator(weights),
                AmountAllocationUtils.sequential()
        );

        assertSumEquals(result, total);
        // 预期结果：[16.67, 33.33, 50.00]
        assertAllocationEquals(result, new BigDecimal[] {
                new BigDecimal("16.67"),
                new BigDecimal("33.33"),
                new BigDecimal("50.00")
        });
    }

    @Test
    void testWeightedAllocation_WithRemainder() {
        BigDecimal total = new BigDecimal("100.01");
        int quantity = 3;
        BigDecimal[] weights = {
                new BigDecimal("1"),
                new BigDecimal("2"),
                new BigDecimal("3")
        };

        BigDecimal[] result = AmountAllocationUtils.allocate(
                total,
                quantity,
                AmountAllocationUtils.weightedAllocator(weights),
                AmountAllocationUtils.sequentialByMaxValue()
        );

        assertSumEquals(result, total);
        // 预期结果：[16.66, 33.33 +0.01, 50.0 + 0.01]
        assertAllocationEquals(result, new BigDecimal[] {
                new BigDecimal("16.66"),
                new BigDecimal("33.34"),
                new BigDecimal("50.01")
        });
    }

    @Test
    void testWeightedRemainderStrategy() {
        BigDecimal total = new BigDecimal("100.05");
        int quantity = 3;
        BigDecimal[] weights = {
                new BigDecimal("1"),
                new BigDecimal("2"),
                new BigDecimal("3")
        };

        BigDecimal[] result = AmountAllocationUtils.allocate(
                total,
                quantity,
                AmountAllocationUtils.weightedAllocator(weights),
                AmountAllocationUtils.weightedRemainder(weights)
        );

        assertSumEquals(result, total);
        // 预期结果：
        // 基础分摊：[16.67, 33.35, 50.03]
        // 零头 0.01 按权重分配：[0.00, 0.00, 0.01]
        assertAllocationEquals(result, new BigDecimal[] {
                new BigDecimal("16.67"),
                new BigDecimal("33.35"),
                new BigDecimal("50.03")
        });
    }

    @Test
    void testLargeRemainder_EnhancedSequential() {
        BigDecimal total = new BigDecimal("100.12");
        int quantity = 3;
        BigDecimal[] weights = {
                new BigDecimal("1"),
                new BigDecimal("2"),
                new BigDecimal("3")
        };

        BigDecimal[] result = AmountAllocationUtils.allocate(
                total,
                quantity,
                AmountAllocationUtils.weightedAllocator(weights),
                AmountAllocationUtils.sequential()
        );

        assertSumEquals(result, total);
        // 预期结果：
        // 基础分摊：[16.68, 33.37, 50.06]
        // 零头 0.01 按顺序分配：[0.01, 0.00, 0.00]
        assertAllocationEquals(result, new BigDecimal[] {
                new BigDecimal("16.69"),
                new BigDecimal("33.37"),
                new BigDecimal("50.06")
        });
    }

    @Test
    void testMinValueRemainderStrategy() {
        BigDecimal total = new BigDecimal("100.04");
        int quantity = 3;
        BigDecimal[] weights = {
                new BigDecimal("1"),
                new BigDecimal("2"),
                new BigDecimal("3")
        };

        BigDecimal[] result = AmountAllocationUtils.allocate(
                total,
                quantity,
                AmountAllocationUtils.weightedAllocator(weights),
                AmountAllocationUtils.minValue()
        );

        assertSumEquals(result, total);
        // 预期结果：
        // 基础分摊：[16.67, 33.34, 50.02]
        // 零头 0.01 按权重分配：[0.00, 0.00, 0.01]
        assertAllocationEquals(result, new BigDecimal[] {
                new BigDecimal("16.68"),
                new BigDecimal("33.34"),
                new BigDecimal("50.02")
        });
    }

    @Test
    void testMaxValueRemainderStrategy() {
        BigDecimal total = new BigDecimal("100.04");
        int quantity = 3;
        BigDecimal[] weights = {
                new BigDecimal("1"),
                new BigDecimal("2"),
                new BigDecimal("3")
        };

        BigDecimal[] result = AmountAllocationUtils.allocate(
                total,
                quantity,
                AmountAllocationUtils.weightedAllocator(weights),
                AmountAllocationUtils.maxValue()
        );

        assertSumEquals(result, total);
        // 预期结果：
        // 基础分摊：[16.67, 33.34, 50.02]
        // 零头 0.01 按权重分配：[0.00, 0.00, 0.01]
        assertAllocationEquals(result, new BigDecimal[] {
                new BigDecimal("16.67"),
                new BigDecimal("33.34"),
                new BigDecimal("50.03")
        });
    }

    @Test
    void testCustomPrecision() {
        BigDecimal total = new BigDecimal("100.000");
        int quantity = 3;
        BigDecimal precision = new BigDecimal("0.001");

        BigDecimal[] result = AmountAllocationUtils.allocate(
                total,
                quantity,
                AmountAllocationUtils.evenAllocator(),
                AmountAllocationUtils.sequential(),
                precision
        );

        assertSumEquals(result, total);
        assertAllocationEquals(result, new BigDecimal[] {
                new BigDecimal("33.334"),
                new BigDecimal("33.333"),
                new BigDecimal("33.333")
        });
    }

    @Test
    void testIllegalArguments() {
        // 测试负数总金额
        assertThrows(IllegalArgumentException.class, () -> {
            AmountAllocationUtils.allocate(
                    new BigDecimal("-100.00"),
                    3,
                    AmountAllocationUtils.evenAllocator(),
                    AmountAllocationUtils.sequential()
            );
        });

        // 测试零分摊数量
        assertThrows(IllegalArgumentException.class, () -> {
            AmountAllocationUtils.allocate(
                    new BigDecimal("100.00"),
                    0,
                    AmountAllocationUtils.evenAllocator(),
                    AmountAllocationUtils.sequential()
            );
        });

        // 测试无效精度
        assertThrows(IllegalArgumentException.class, () -> {
            AmountAllocationUtils.allocate(
                    new BigDecimal("100.00"),
                    3,
                    AmountAllocationUtils.evenAllocator(),
                    AmountAllocationUtils.sequential(),
                    new BigDecimal("0")
            );
        });

        // 测试空权重数组
        assertThrows(IllegalArgumentException.class, () -> {
            AmountAllocationUtils.weightedAllocator(null).baseAllocate(new BigDecimal("100"), 3, new BigDecimal("0.01"));
        });

        // 测试权重数组长度不匹配
        assertThrows(IllegalArgumentException.class, () -> {
            AmountAllocationUtils.weightedAllocator(new BigDecimal[] {
                    new BigDecimal("1"),
                    new BigDecimal("2")
            }).baseAllocate(new BigDecimal("100"), 3, new BigDecimal("0.01"));
        });

        // 测试总权重为零
        assertThrows(IllegalArgumentException.class, () -> {
            AmountAllocationUtils.weightedAllocator(new BigDecimal[] {
                    new BigDecimal("0"),
                    new BigDecimal("0")
            }).baseAllocate(new BigDecimal("100"), 2, new BigDecimal("0.01"));
        });
    }
    @Test
    public void testEvenAllocator_BasicCase() {
        // 测试基本情况：100元均摊到3个位置，精度0.01元
        BigDecimal totalAmount = new BigDecimal("100");
        int quantity = 3;
        BigDecimal precision = new BigDecimal("0.01");

        // 执行均摊
        BigDecimal[] result = AmountAllocationUtils.evenAllocator()
                .baseAllocate(totalAmount, quantity, precision);

        // 验证结果长度
        assertEquals(quantity, result.length, "结果数组长度应等于分摊数量");

        assertAllocationEquals(result, new BigDecimal[] {
                new BigDecimal("33.33"),
                new BigDecimal("33.33"),
                new BigDecimal("33.33")
        });
    }

    @Test
    public void testEvenAllocator_ExactDivision() {
        // 测试能整除的情况：99元均摊到3个位置，精度0.01元
        BigDecimal totalAmount = new BigDecimal("99");
        int quantity = 3;
        BigDecimal precision = new BigDecimal("0.01");

        // 执行均摊
        BigDecimal[] result = AmountAllocationUtils.evenAllocator()
                .baseAllocate(totalAmount, quantity, precision);

        // 验证每个位置的金额相等
        BigDecimal expected = new BigDecimal("33.00");
        for (BigDecimal amount : result) {
            assertEquals(expected, amount, "每个位置的金额应等于99÷3=33.00");
        }
    }
}