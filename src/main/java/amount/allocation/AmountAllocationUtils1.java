package amount.allocation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.function.BiFunction;

/**
 * 重构后的金额分摊工具类
 * 核心功能：接收总金额、分摊算法、分摊数量、零头策略，返回包含零头调整的结果
 * 返回值：二维数组，[0]为基础分摊金额，[1]为每个位置的零头调整值
 */
public class AmountAllocationUtils1 {

    /**
     * 核心分摊方法：按指定算法分摊总金额，并根据零头策略生成零头调整数组
     *
     * @param totalAmount      待分摊总金额（必须≥0）
     * @param coreAllocator    分摊核心算法（输入总金额和数量，返回基础分摊数组）
     * @param quantity         分摊数量（必须≥1）
     * @param remainderStrategy 零头分摊策略（输入总零头和数量，返回每个位置的零头调整值）
     * @return 二维数组：[0]基础分摊金额，[1]零头调整数组（总和=总零头）
     * @throws IllegalArgumentException 参数校验失败时抛出
     */
    public static BigDecimal[][] allocate(
            BigDecimal totalAmount,
            BiFunction<BigDecimal, Integer, BigDecimal[]> coreAllocator,
            int quantity,
            BiFunction<BigDecimal, Integer, BigDecimal[]> remainderStrategy
    ) {
        // 参数校验
        validateParams(totalAmount, quantity);

        // 1. 执行核心分摊算法，获取基础金额数组
        BigDecimal[] baseAmounts = coreAllocator.apply(totalAmount, quantity);
        // 校验基础数组长度
        if (baseAmounts.length != quantity) {
            throw new IllegalArgumentException("核心算法返回的数组长度与分摊数量不一致");
        }

        // 2. 计算总零头（总金额 - 基础金额总和）
        BigDecimal baseSum = Arrays.stream(baseAmounts).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalRemainder = totalAmount.subtract(baseSum);
        if (totalRemainder.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("核心算法计算的基础金额总和超过总金额");
        }

        // 3. 执行零头分摊策略，获取每个位置的零头调整值
        BigDecimal[] remainderAdjustments = remainderStrategy.apply(totalRemainder, quantity);
        // 校验零头数组
        if (remainderAdjustments.length != quantity) {
            throw new IllegalArgumentException("零头策略返回的数组长度与分摊数量不一致");
        }
        validateRemainderSum(remainderAdjustments, totalRemainder);

        return new BigDecimal[][]{baseAmounts, remainderAdjustments};
    }

    // ------------------------------ 内置分摊算法（可直接作为coreAllocator参数） ------------------------------

    /**
     * 内置均摊算法：将总金额平均分配（向下取整保留两位小数）
     * 示例：100.05元分6份 → 基础数组为[16.67, 16.67, 16.67, 16.67, 16.67, 16.67]
     */
    public static BiFunction<BigDecimal, Integer, BigDecimal[]> evenCoreAllocator() {
        return (total, quantity) -> {
            BigDecimal[] base = new BigDecimal[quantity];
            BigDecimal average = total.divide(BigDecimal.valueOf(quantity), 2, RoundingMode.DOWN);
            Arrays.fill(base, average);
            return base;
        };
    }

    /**
     * 内置加权分摊算法：按权重比例分配（向下取整保留两位小数）
     * 示例：权重[1,2,3]分60元 → 基础数组为[10.00, 20.00, 30.00]
     *
     * @param weights 权重数组（长度=分摊数量，总和>0）
     */
    public static BiFunction<BigDecimal, Integer, BigDecimal[]> weightedCoreAllocator(BigDecimal[] weights) {
        return (total, quantity) -> {
            // 校验权重
            if (weights == null || weights.length != quantity) {
                throw new IllegalArgumentException("权重数组长度与分摊数量不一致");
            }
            BigDecimal totalWeight = Arrays.stream(weights).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (totalWeight.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("权重总和必须大于0");
            }

            BigDecimal[] base = new BigDecimal[quantity];
            BigDecimal allocated = BigDecimal.ZERO;
            // 前n-1个按比例分配（向下取整）
            for (int i = 0; i < quantity - 1; i++) {
                BigDecimal ratio = weights[i].divide(totalWeight, 10, RoundingMode.HALF_UP);
                base[i] = total.multiply(ratio).setScale(2, RoundingMode.DOWN);
                allocated = allocated.add(base[i]);
            }
            // 最后一个用总金额补齐（避免精度误差）
            base[quantity - 1] = total.subtract(allocated).setScale(2, RoundingMode.DOWN);
            return base;
        };
    }

    // ------------------------------ 内置零头策略（可直接作为remainderStrategy参数） ------------------------------

    /**
     * 零头策略：按顺序分配（前n个位置各加0.01，直到零头分完）
     * 示例：总零头0.05，数量6 → [0.01,0.01,0.01,0.01,0.01,0.00]
     */
    public static BiFunction<BigDecimal, Integer, BigDecimal[]> sequentialRemainder() {
        return (totalRemainder, quantity) -> {
            BigDecimal[] remainders = new BigDecimal[quantity];
            Arrays.fill(remainders, BigDecimal.ZERO);

            // 转换零头为最小单位（分）
            int cents = totalRemainder.multiply(new BigDecimal("100"))
                    .setScale(0, RoundingMode.DOWN).intValue();

            // 按顺序分配零头（每个位置最多0.01）
            for (int i = 0; i < cents && i < quantity; i++) {
                remainders[i] = new BigDecimal("0.01");
            }
            return remainders;
        };
    }

    /**
     * 零头策略：随机分配（随机选择n个位置各加0.01）
     * 示例：总零头0.03，数量5 → 随机3个位置各0.01
     */
    public static BiFunction<BigDecimal, Integer, BigDecimal[]> randomRemainder() {
        return (totalRemainder, quantity) -> {
            BigDecimal[] remainders = new BigDecimal[quantity];
            Arrays.fill(remainders, BigDecimal.ZERO);

            int cents = totalRemainder.multiply(new BigDecimal("100"))
                    .setScale(0, RoundingMode.DOWN).intValue();
            if (cents <= 0) return remainders;

            // 随机分配零头（避免重复位置）
            int assigned = 0;
            while (assigned < cents) {
                int pos = (int) (Math.random() * quantity);
                if (remainders[pos].compareTo(BigDecimal.ZERO) == 0) {
                    remainders[pos] = new BigDecimal("0.01");
                    assigned++;
                }
            }
            return remainders;
        };
    }

    /**
     * 零头策略：集中分配（将所有零头放在第一个位置）
     * 示例：总零头0.05 → [0.05, 0.00, 0.00, ...]
     */
    public static BiFunction<BigDecimal, Integer, BigDecimal[]>集中Remainder() {
        return (totalRemainder, quantity) -> {
            BigDecimal[] remainders = new BigDecimal[quantity];
            Arrays.fill(remainders, BigDecimal.ZERO);
            remainders[0] = totalRemainder; // 所有零头放第一个位置
            return remainders;
        };
    }

    // ------------------------------ 私有工具方法 ------------------------------

    /**
     * 参数校验
     */
    private static void validateParams(BigDecimal totalAmount, int quantity) {
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("总金额必须≥0");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("分摊数量必须≥1");
        }
    }

    /**
     * 校验零头调整数组总和是否等于总零头
     */
    private static void validateRemainderSum(BigDecimal[] adjustments, BigDecimal totalRemainder) {
        BigDecimal sum = Arrays.stream(adjustments).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(totalRemainder) != 0) {
            throw new IllegalArgumentException("零头策略返回的调整值总和与总零头不一致（预期："
                    + totalRemainder + "，实际：" + sum + "）");
        }
    }
}