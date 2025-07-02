package amount.allocation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Random;
import java.util.function.BiFunction;

/**
 * 金额分摊工具类，支持多种分摊算法和零头处理策略
 */
public class AmountAllocationUtils {

    /**
     * 分摊金额的主方法
     * @param totalAmount 待分总金额
     * @param quantity 待分摊数量
     * @param allocationStrategy 分摊策略函数
     * @return 分摊结果数组
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
     * 均摊算法（支持自定义零头位置）
     * @param remainderPosition 零头位置策略（0~n-1）
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
     * 加权分摊算法（支持自定义零头位置）
     * @param weights 权重数组
     * @param remainderPosition 零头位置策略（0~n-1）
     * @return 加权分摊策略函数
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
     * 零头位置策略：随机位置
     * @param quantity 分摊数量
     * @return 随机位置（0~n-1）
     */
    public static int remainderPositionRandom(int quantity) {
        return new Random().nextInt(quantity);
    }

    /**
     * 零头位置策略：最大金额位置
     * @param amounts 分摊结果数组
     * @return 最大金额位置
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
     * 零头位置策略：最小金额位置
     * @param amounts 分摊结果数组
     * @return 最小金额位置
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