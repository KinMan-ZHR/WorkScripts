package amount.allocation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Random;
import java.util.function.BiFunction;

/**
 * 金额分摊工具类，支持多种分摊算法和零头处理策略。
 * <p>
 * 该工具类提供了灵活的金额分摊功能，主要用于将总金额按照不同算法分配到多个部分，
 * 并支持自定义零头的处理方式。核心方法是 {@link #allocate(BigDecimal, int, BiFunction)}，
 * 它接受一个分摊策略函数，允许用户自定义分摊逻辑。
 * </p>
 *
 * <h2>使用示例</h2>
 * <pre>
 * // 示例1：均摊100.01元到3个部分，零头放在第二个位置
 * BigDecimal total = new BigDecimal("100.01");
 * int quantity = 3;
 * BigDecimal[] evenResult = AmountAllocationUtils.allocate(
 *     total,
 *     quantity,
 *     AmountAllocationUtils.evenAllocation(1)
 * );
 * // 结果：[33.33, 33.35, 33.33]
 *
 * // 示例2：加权分摊，权重比例为 3:3:4，零头随机分配
 * BigDecimal[] weights = {new BigDecimal("3"), new BigDecimal("3"), new BigDecimal("4")};
 * int randomPosition = AmountAllocationUtils.remainderPositionRandom(quantity);
 * BigDecimal[] weightedResult = AmountAllocationUtils.allocate(
 *     total,
 *     quantity,
 *     AmountAllocationUtils.weightedAllocation(weights, randomPosition)
 * );
 * // 可能的结果：[30.00, 30.00, 40.00]
 *
 * // 示例3：自定义分摊算法（阶梯式分摊）
 * BiFunction<BigDecimal, Integer, BigDecimal[]> customStrategy = (totalAmt, qty) -> {
 *     BigDecimal[] result = new BigDecimal[qty];
 *     // 自定义分摊逻辑...
 *     return result;
 * };
 * BigDecimal[] customResult = AmountAllocationUtils.allocate(total, quantity, customStrategy);
 * </pre>
 *
 * <h2>注意事项</h2>
 * <ul>
 *   <li>所有金额计算均使用 {@link BigDecimal} 以确保精度</li>
 *   <li>默认保留两位小数，使用 {@link RoundingMode#DOWN} 避免超额分配</li>
 *   <li>零头位置索引从0开始，范围为 0 到 n-1</li>
 * </ul>
 */
public class AmountAllocationUtils2 {

    /**
     * 分摊金额的主方法，根据指定的分摊策略将总金额分配到多个部分。
     *
     * @param totalAmount 待分总金额，不能为 null 或负数
     * @param quantity 待分摊数量，必须为正整数
     * @param allocationStrategy 分摊策略函数，负责定义具体的分摊逻辑
     * @return 分摊结果数组，长度等于 quantity
     * @throws IllegalArgumentException 如果 totalAmount 为 null、负数，或 quantity 非正
     */
    public static BigDecimal[] allocate(BigDecimal totalAmount, int quantity,
                                        BiFunction<BigDecimal, Integer, BigDecimal[]> allocationStrategy) {
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("总金额不能为负数");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("分摊数量必须为正整数");
        }
        return allocationStrategy.apply(totalAmount, quantity);
    }

    // ---------------------------- 默认分摊算法 ----------------------------

    /**
     * 均摊算法，将总金额平均分配到每个部分，并将余数放到指定位置。
     * <p>
     * 例如：将 100.00 元均摊到 3 个部分，余数 0.01 放在位置 1，结果为 [33.33, 33.34, 33.33]。
     * </p>
     *
     * @param remainderPosition 零头位置策略，指定余数放置的位置（0~n-1）
     * @return 均摊策略函数
     */
    public static BiFunction<BigDecimal, Integer, BigDecimal[]> evenAllocation(int remainderPosition) {
        return (total, quantity) -> {
            BigDecimal[] result = new BigDecimal[quantity];
            BigDecimal average = total.divide(BigDecimal.valueOf(quantity), 2, RoundingMode.DOWN);
            BigDecimal remainder = total.subtract(average.multiply(BigDecimal.valueOf(quantity)));

            Arrays.fill(result, average);
            if (remainder.compareTo(BigDecimal.ZERO) > 0 && remainderPosition < quantity) {
                result[remainderPosition] = result[remainderPosition].add(remainder);
            }
            return result;
        };
    }

    /**
     * 加权分摊算法，根据权重比例分配总金额，并将余数放到指定位置。
     * <p>
     * 例如：总金额 100 元，权重 [2, 3]，则分配比例为 2:3，结果为 [40.00, 60.00]。
     * </p>
     *
     * @param weights 权重数组，长度必须与分摊数量一致，且元素之和必须大于0
     * @param remainderPosition 零头位置策略，指定余数放置的位置（0~n-1）
     * @return 加权分摊策略函数
     * @throws IllegalArgumentException 如果 weights 为 null、长度与 quantity 不一致，或总权重非正
     */
    public static BiFunction<BigDecimal, Integer, BigDecimal[]> weightedAllocation(
            BigDecimal[] weights, int remainderPosition) {
        return (total, quantity) -> {
            if (weights == null || weights.length != quantity) {
                throw new IllegalArgumentException("权重数组长度必须与分摊数量一致");
            }

            // 计算总权重
            BigDecimal totalWeight = Arrays.stream(weights)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (totalWeight.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("总权重必须大于0");
            }

            BigDecimal[] result = new BigDecimal[quantity];
            BigDecimal allocated = BigDecimal.ZERO;

            // 按权重分配（向下取整）
            for (int i = 0; i < quantity - 1; i++) {
                BigDecimal weightRatio = weights[i].divide(totalWeight, 10, RoundingMode.HALF_UP);
                result[i] = total.multiply(weightRatio).setScale(2, RoundingMode.DOWN);
                allocated = allocated.add(result[i]);
            }

            // 最后一个位置处理零头
            result[quantity - 1] = total.subtract(allocated);

            // 调整零头位置
            if (remainderPosition < quantity && remainderPosition != quantity - 1) {
                BigDecimal remainder = result[quantity - 1];
                if (remainder.compareTo(BigDecimal.ZERO) != 0) {
                    result[remainderPosition] = result[remainderPosition].add(remainder);
                    result[quantity - 1] = BigDecimal.ZERO;
                }
            }

            return result;
        };
    }

    // ---------------------------- 零头位置策略工厂 ----------------------------

    /**
     * 零头位置策略：随机位置。
     * 生成一个 0 到 quantity-1 之间的随机整数作为零头位置。
     *
     * @param quantity 分摊数量
     * @return 随机位置索引（0~n-1）
     */
    public static int remainderPositionRandom(int quantity) {
        return new Random().nextInt(quantity);
    }

    /**
     * 零头位置策略：最大金额位置。
     * 分析已分配的金额数组，返回金额最大的位置索引。
     *
     * @param amounts 分摊结果数组
     * @return 最大金额位置索引
     */
    public static int remainderPositionMax(BigDecimal[] amounts) {
        int maxIndex = 0;
        for (int i = 1; i < amounts.length; i++) {
            if (amounts[i].compareTo(amounts[maxIndex]) > 0) {
                maxIndex = i;
            }
        }
        return maxIndex;
    }

    /**
     * 零头位置策略：最小金额位置。
     * 分析已分配的金额数组，返回金额最小的位置索引。
     *
     * @param amounts 分摊结果数组
     * @return 最小金额位置索引
     */
    public static int remainderPositionMin(BigDecimal[] amounts) {
        int minIndex = 0;
        for (int i = 1; i < amounts.length; i++) {
            if (amounts[i].compareTo(amounts[minIndex]) < 0) {
                minIndex = i;
            }
        }
        return minIndex;
    }
}