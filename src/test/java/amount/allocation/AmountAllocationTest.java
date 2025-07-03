package amount.allocation;

import amount.allocation.AmountAllocationUtils.BaseAllocator;
import amount.allocation.AmountAllocationUtils.RemainderStrategy;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;

@RunWith(MockitoJUnitRunner.class)
public class AmountAllocationTest {
    @Test
    public void testAmountAllocation() {
            BigDecimal totalAmount = new BigDecimal("3.05");
            int quantity = 3;

            // 自定义 BaseAllocator：返回 [1.00, 1.00, 1.00] → 总和 = 3.00
            BaseAllocator baseAllocator = (total, qty, precision) -> {
                BigDecimal[] amounts = new BigDecimal[qty];
                Arrays.fill(amounts, new BigDecimal("1.00"));
                return amounts;
            };

            // 使用 sequential 零头策略
            RemainderStrategy remainderStrategy = AmountAllocationUtils.sequential();

            BigDecimal[] result = AmountAllocationUtils.allocate(
                    totalAmount,
                    quantity,
                    baseAllocator,
                    remainderStrategy
            );

            System.out.println("分摊结果：");
            for (BigDecimal bd : result) {
                System.out.println(bd.toPlainString());
            }

            BigDecimal sum = Arrays.stream(result)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            System.out.println("最终总和：" + sum.toPlainString());
        }

    @Test
    public void OverBaseAmountTest() {
        // 总金额
        BigDecimal totalAmount = new BigDecimal("3.00");
        int quantity = 3;

        // 自定义 BaseAllocator：返回 [1.01, 1.01, 1.01]
        BaseAllocator baseAllocator = (total, qty, precision) -> {
            BigDecimal[] amounts = new BigDecimal[qty];
            Arrays.fill(amounts, new BigDecimal("1.01"));  // ❗❗❗ 故意超出
            return amounts;
        };

        // 使用 sequential 零头策略（不影响验证逻辑）
        RemainderStrategy remainderStrategy = AmountAllocationUtils.sequential();

        try {
            BigDecimal[] result = AmountAllocationUtils.allocate(
                    totalAmount,
                    quantity,
                    baseAllocator,
                    remainderStrategy
            );

            System.out.println("调整后结果：");
            for (BigDecimal bd : result) {
                System.out.println(bd.toPlainString());
            }

            BigDecimal sum = Arrays.stream(result)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            System.out.println("调整后总和：" + sum.toPlainString());

        } catch (IllegalStateException e) {
            System.err.println("✅ 触发异常：" + e.getMessage());
        }
    }
    @Test
    public void testWeightedOverallocatedCase() {
        BigDecimal totalAmount = new BigDecimal("3.00");
        int quantity = 3;

        // 权重相等，理论上每份应为 1.00
        BigDecimal[] weights = {
                new BigDecimal("1"),
                new BigDecimal("1"),
                new BigDecimal("1")
        };

        // 自定义加权分配器：强制舍入方式为 DOWN，但因中间计算误差导致偏高
        BaseAllocator baseAllocator = (total, qty, precision) -> {
            BigDecimal totalWeight = Arrays.stream(weights).reduce(BigDecimal.ZERO, BigDecimal::add);
            String precisionStr = precision.stripTrailingZeros().toPlainString();
            int dotIndex = precisionStr.indexOf('.');
            int scale = dotIndex < 0 ? 0 : precisionStr.length() - dotIndex - 1;

            BigDecimal[] amounts = new BigDecimal[qty];
            for (int i = 0; i < qty; i++) {
                BigDecimal weightRatio = weights[i].divide(totalWeight, 10, RoundingMode.HALF_UP);
                BigDecimal amount = total.multiply(weightRatio).setScale(scale, RoundingMode.DOWN);
                amounts[i] = amount;
            }

            // 手动引入一个“尾差”导致总和偏大
            amounts[2] = amounts[2].add(new BigDecimal("0.01"));

            return amounts;
        };

        RemainderStrategy remainderStrategy = AmountAllocationUtils.sequential();

        BigDecimal[] result = AmountAllocationUtils.allocate(
                totalAmount, quantity, baseAllocator, remainderStrategy);

        BigDecimal sum = Arrays.stream(result).reduce(BigDecimal.ZERO, BigDecimal::add);
        System.out.println("调整后总和：" + sum.toPlainString());
    }

}
