package amount.allocation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 金额分摊工具类
 * <p>
 * 用于将总金额按指定规则分摊到多个位置，支持自定义分摊算法、零头处理策略和分配精度。
 * 核心设计理念：将基础分摊算法与零头调整策略完全解耦，提高代码可维护性和扩展性。
 * <p>
 * <strong>使用方式说明</strong>：
 * 1. 选择基础分摊算法（均摊/加权分摊）
 * 2. 选择零头处理策略（顺序/随机/加权等）
 * 3. 调用核心分摊方法执行分配
 * <p>
 * <strong>示例代码</strong>：
 * <pre>{@code
 * // 示例1：均摊总金额，顺序分配零头
 * public class EvenAllocationExample {
 *     public static void main(String[] args) {
 *         // 总金额：100.50元
 *         BigDecimal totalAmount = new BigDecimal("100.50");
 *         // 分摊数量：3个位置
 *         int quantity = 3;
 *
 *         // 选择基础分摊算法：均摊
 *         BaseAllocator baseAllocator = AmountAllocationUtils.evenAllocator();
 *         // 选择零头处理策略：顺序分配
 *         RemainderStrategy remainderStrategy = AmountAllocationUtils.sequential();
 *
 *         // 执行分摊（使用默认精度：0.01元）
 *         BigDecimal[] result = AmountAllocationUtils.allocate(
 *             totalAmount,
 *             quantity,
 *             baseAllocator,
 *             remainderStrategy
 *         );
 *
 *         // 输出结果：总和应为100.50元
 *         System.out.println("均摊结果：" + Arrays.toString(result));
 *     }
 * }
 * }</pre>
 *
 * <pre>{@code
 * // 示例2：加权分摊总金额，加权处理零头
 * public class WeightedAllocationExample {
 *     public static void main(String[] args) {
 *         // 总金额：200.75元
 *         BigDecimal totalAmount = new BigDecimal("200.75");
 *         // 分摊数量：4个位置
 *         int quantity = 4;
 *         // 权重数组：[1, 2, 3, 4]
 *         BigDecimal[] weights = {
 *             new BigDecimal("1"),
 *             new BigDecimal("2"),
 *             new BigDecimal("3"),
 *             new BigDecimal("4")
 *         };
 *
 *         // 选择基础分摊算法：加权分摊
 *         BaseAllocator baseAllocator = AmountAllocationUtils.weightedAllocator(weights);
 *         // 选择零头处理策略：加权分配零头
 *         RemainderStrategy remainderStrategy = AmountAllocationUtils.weightedRemainder(weights);
 *
 *         // 执行分摊（指定精度：0.01元）
 *         BigDecimal[] result = AmountAllocationUtils.allocate(
 *             totalAmount,
 *             quantity,
 *             baseAllocator,
 *             remainderStrategy,
 *             AmountAllocationUtils.DEFAULT_PRECISION
 *         );
 *
 *         // 输出结果：总和应为200.75元
 *         System.out.println("加权分摊结果：" + Arrays.toString(result));
 *     }
 * }
 * }</pre>
 *
 * <pre>{@code
 * // 示例3：自定义分摊算法和零头策略
 * public class CustomAllocationExample {
 *     public static void main(String[] args) {
 *         // 总金额：150.30元
 *         BigDecimal totalAmount = new BigDecimal("150.30");
 *         // 分摊数量：5个位置
 *         int quantity = 5;
 *
 *         // 自定义基础分摊算法：前两个位置分30%，后三个位置分70%
 *         BaseAllocator customAllocator = (amount, qty, precision) -> {
 *             BigDecimal[] result = new BigDecimal[qty];
 *             BigDecimal firstPart = amount.multiply(new BigDecimal("0.3")).divide(
 *                 BigDecimal.valueOf(2), 2, RoundingMode.DOWN);
 *             BigDecimal secondPart = amount.multiply(new BigDecimal("0.7")).divide(
 *                 BigDecimal.valueOf(3), 2, RoundingMode.DOWN);
 *
 *             result[0] = firstPart;
 *             result[1] = firstPart;
 *             result[2] = secondPart;
 *             result[3] = secondPart;
 *             result[4] = secondPart;
 *             return result;
 *         };
 *
 *         // 自定义零头策略：优先分配给最后一个位置
 *         RemainderStrategy customStrategy = (base, remainder, precision) -> {
 *             BigDecimal[] result = Arrays.copyOf(base, base.length);
 *             result[result.length - 1] = result[result.length - 1].add(remainder);
 *             return result;
 *         };
 *
 *         // 执行分摊
 *         BigDecimal[] result = AmountAllocationUtils.allocate(
 *             totalAmount,
 *             quantity,
 *             customAllocator,
 *             customStrategy
 *         );
 *
 *         // 输出结果：总和应为150.30元
 *         System.out.println("自定义分摊结果：" + Arrays.toString(result));
 *     }
 * }
 * }</pre>
 *
 * @author KinMan Zhang or ZhangHaoRan
 * @since 2025/7/3
 */
public class AmountAllocationUtils {

    /** 默认分配精度：0.01元 */
    public static final BigDecimal DEFAULT_PRECISION = new BigDecimal("0.01");

    /**
     * 核心分摊方法：结合基础分摊算法和零头调整策略，使用默认精度
     */
    public static BigDecimal[] allocate(
            BigDecimal totalAmount,
            int quantity,
            BaseAllocator baseAllocator,
            RemainderStrategy remainderStrategy
    ) {
        return allocate(totalAmount, quantity, baseAllocator, remainderStrategy, DEFAULT_PRECISION);
    }

    /**
     * 核心分摊方法：结合基础分摊算法、零头调整策略和指定精度
     */
    public static BigDecimal[] allocate(
            BigDecimal totalAmount,
            int quantity,
            BaseAllocator baseAllocator,
            RemainderStrategy remainderStrategy,
            BigDecimal precision
    ) {
        // 参数校验
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("总金额必须≥0");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("分摊数量必须≥1");
        }
        if (precision == null || precision.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("精度必须>0");
        }

        // 执行基础分摊
        BigDecimal[] baseAmounts = baseAllocator.baseAllocate(totalAmount, quantity, precision);

        // 计算零头（确保为正）
        BigDecimal baseSum = Arrays.stream(baseAmounts).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalRemainder = totalAmount.subtract(baseSum);

        // 应用零头策略
        BigDecimal[] result = remainderStrategy.apply(baseAmounts, totalRemainder, precision);

        // 验证结果总和是否等于总金额
        BigDecimal resultSum = Arrays.stream(result).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (resultSum.compareTo(totalAmount) != 0) {
            throw new IllegalStateException(
                    String.format("分摊结果总和不正确：预期 %s，实际 %s 请检查零头策略是否合理",
                            totalAmount.toPlainString(),
                            resultSum.toPlainString())
            );
        }

        return result;
    }

    // ------------------------------ 基础分摊接口 ------------------------------

    /**
     * 基础分摊算法接口
     */
    @FunctionalInterface
    public interface BaseAllocator {
        /**
         * 执行基础金额分摊
         */
        BigDecimal[] baseAllocate(BigDecimal totalAmount, int quantity, BigDecimal precision);
    }

    // ------------------------------ 内置基础分摊算法 ------------------------------

    /**
     * 均摊基础算法：将总金额平均分配到每个位置
     */
    public static BaseAllocator evenAllocator() {
        return (totalAmount, quantity, precision) -> {
            // 计算基础金额（向下取整到指定精度）
            BigDecimal baseAmount = totalAmount.divide(BigDecimal.valueOf(quantity),
                    getScale(precision), RoundingMode.DOWN);

            BigDecimal[] baseAmounts = new BigDecimal[quantity];
            Arrays.fill(baseAmounts, baseAmount);
            return baseAmounts;
        };
    }

    /**
     * 加权分摊基础算法：按权重比例分配总金额
     */
    public static BaseAllocator weightedAllocator(BigDecimal[] weights) {
        return (totalAmount, quantity, precision) -> {
            if (weights == null || weights.length != quantity) {
                throw new IllegalArgumentException("权重数组长度与分摊数量不一致");
            }

            // 计算总权重
            BigDecimal totalWeight = Arrays.stream(weights).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (totalWeight.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("总权重必须大于0");
            }

            // 按权重分配基础金额（向下取整到指定精度）
            BigDecimal[] baseAmounts = new BigDecimal[quantity];
            int scale = getScale(precision);

            for (int i = 0; i < quantity; i++) {
                BigDecimal weightRatio = weights[i].divide(totalWeight, 10, RoundingMode.HALF_UP);
                BigDecimal amount = totalAmount.multiply(weightRatio);
                baseAmounts[i] = amount.setScale(scale, RoundingMode.DOWN);
            }

            return baseAmounts;
        };
    }

    // ------------------------------ 零头处理策略 ------------------------------

    /**
     * 零头处理策略接口
     */
    @FunctionalInterface
    public interface RemainderStrategy {
        /**
         * 应用零头调整
         */
        BigDecimal[] apply(BigDecimal[] baseAmounts, BigDecimal totalRemainder, BigDecimal precision);
    }

    // ------------------------------ 内置零头策略 ------------------------------

    /**
     * 顺序分配零头策略：按数组顺序逐个分配零头
     */
    public static RemainderStrategy sequential() {
        return (baseAmounts, totalRemainder, precision) -> adjustByUnitOrder(baseAmounts, totalRemainder, precision,
                IntStream.range(0, baseAmounts.length).toArray());
    }

    /**
     * 随机分配零头策略：随机选择位置分配零头
     */
    public static RemainderStrategy random() {
        return (baseAmounts, totalRemainder, precision) -> {
            List<Integer> positions = IntStream.range(0, baseAmounts.length)
                    .boxed()
                    .collect(Collectors.toList());
            Collections.shuffle(positions);

            return adjustByUnitOrder(baseAmounts, totalRemainder, precision,
                    positions.stream().mapToInt(Integer::intValue).toArray());
        };
    }

    /**
     * 金额从小到大顺序分配策略：按金额升序逐个分配零头
     */
    public static RemainderStrategy sequentialByMinValue() {
        return (baseAmounts, totalRemainder, precision) -> adjustBySortedValue(baseAmounts, totalRemainder, precision, false);
    }

    /**
     * 金额从大到小顺序分配策略：按金额降序逐个分配零头
     */
    public static RemainderStrategy sequentialByMaxValue() {
        return (baseAmounts, totalRemainder, precision) -> adjustBySortedValue(baseAmounts, totalRemainder, precision, true);
    }

    /**
     * 最小值集中策略：将零头集中分配到最小值位置
     */
    public static RemainderStrategy minValue() {
        return (baseAmounts, totalRemainder, precision) -> {
            if (totalRemainder.compareTo(BigDecimal.ZERO) <= 0) {
                return baseAmounts;
            }
            int minIndex = findMinIndex(baseAmounts);
            BigDecimal[] result = Arrays.copyOf(baseAmounts, baseAmounts.length);
            result[minIndex] = result[minIndex].add(totalRemainder);
            return result;
        };
    }

    /**
     * 最大值集中策略：将零头集中分配到最大值位置
     */
    public static RemainderStrategy maxValue() {
        return (baseAmounts, totalRemainder, precision) -> {
            if (totalRemainder.compareTo(BigDecimal.ZERO) <= 0) {
                return baseAmounts;
            }
            int maxIndex = findMaxIndex(baseAmounts);
            BigDecimal[] result = Arrays.copyOf(baseAmounts, baseAmounts.length);
            result[maxIndex] = result[maxIndex].add(totalRemainder);
            return result;
        };
    }

    /**
     * 按指定顺序和单位调整零头
     */
    private static BigDecimal[] adjustByUnitOrder(
            BigDecimal[] baseAmounts,
            BigDecimal totalRemainder,
            BigDecimal precision,
            int[] positions
    ) {
        BigDecimal[] result = Arrays.copyOf(baseAmounts, baseAmounts.length);
        // 转换为最小单位（可正可负）
        int units = totalRemainder.divide(precision, 0, RoundingMode.DOWN).intValue();

        // 正数：加
        while (units > 0) {
            for (int pos : positions) {
                if (units == 0) break;
                result[pos] = result[pos].add(precision);
                units--;
            }
        }
        // 负数：减
        while (units < 0) {
            units = -units;  // 取绝对值
            for (int pos : positions) {
                if (units == 0) break;
                if (result[pos].compareTo(precision) >= 0) {
                    result[pos] = result[pos].subtract(precision);
                    units--;
                }
            }
        }
        return result;
    }

    /**
     * 按金额排序后调整零头
     */
    private static BigDecimal[] adjustBySortedValue(
            BigDecimal[] baseAmounts,
            BigDecimal totalRemainder,
            BigDecimal precision,
            boolean descending
    ) {
        // 创建带索引的金额列表
        IndexedAmount[] indexedAmounts = IntStream.range(0, baseAmounts.length)
                .mapToObj(i -> new IndexedAmount(i, baseAmounts[i]))
                .toArray(IndexedAmount[]::new);

        // 排序
        Comparator<IndexedAmount> comparator = Comparator.comparing(IndexedAmount::getAmount);
        if (descending) {
            comparator = comparator.reversed();
        }
        Arrays.sort(indexedAmounts, comparator);

        // 提取排序后的索引顺序
        int[] positions = Arrays.stream(indexedAmounts)
                .mapToInt(IndexedAmount::getIndex)
                .toArray();

        // 按顺序调整零头
        return adjustByUnitOrder(baseAmounts, totalRemainder, precision, positions);
    }

    /**
     * 找到数组中的最小值索引
     */
    private static int findMinIndex(BigDecimal[] amounts) {
        int minIndex = 0;
        for (int i = 1; i < amounts.length; i++) {
            if (amounts[i].compareTo(amounts[minIndex]) < 0) {
                minIndex = i;
            }
        }
        return minIndex;
    }

    /**
     * 找到数组中的最大值索引
     */
    private static int findMaxIndex(BigDecimal[] amounts) {
        int maxIndex = 0;
        for (int i = 1; i < amounts.length; i++) {
            if (amounts[i].compareTo(amounts[maxIndex]) > 0) {
                maxIndex = i;
            }
        }
        return maxIndex;
    }

    /**
     * 获取精度对应的小数位数
     */
    private static int getScale(BigDecimal precision) {
        String precisionStr = precision.stripTrailingZeros().toPlainString();
        int dotIndex = precisionStr.indexOf('.');
        return dotIndex < 0 ? 0 : precisionStr.length() - dotIndex - 1;
    }

    // ------------------------------ 内部类 ------------------------------

    /**
     * 带索引的金额，用于排序
     */
    private static class IndexedAmount {
        private final int index;
        private final BigDecimal amount;

        public IndexedAmount(int index, BigDecimal amount) {
            this.index = index;
            this.amount = amount;
        }

        public int getIndex() {
            return index;
        }

        public BigDecimal getAmount() {
            return amount;
        }
    }

    /**
     * 加权零头分配策略：按权重比例分配零头
     * <p>
     * 算法说明：
     * 1. 计算每个位置的权重占比
     * 2. 根据权重占比分配零头（向下取整到精度）
     * 3. 处理剩余零头：将剩余零头按顺序逐个分配
     */
    public static RemainderStrategy weightedRemainder(BigDecimal[] weights) {
        if (weights == null) {
            throw new IllegalArgumentException("权重数组不能为空");
        }

        return (baseAmounts, totalRemainder, precision) -> {
            if (totalRemainder.compareTo(BigDecimal.ZERO) == 0) {
                return baseAmounts;
            }

            int quantity = baseAmounts.length;
            if (weights.length != quantity) {
                throw new IllegalArgumentException("权重数组长度与分摊数量不一致");
            }

            // 计算总权重
            BigDecimal totalWeight = Arrays.stream(weights).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (totalWeight.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("总权重必须大于0");
            }

            // 创建结果数组
            BigDecimal[] result = Arrays.copyOf(baseAmounts, quantity);
            int scale = getScale(precision);

            boolean isNegative = totalRemainder.compareTo(BigDecimal.ZERO) < 0;
            BigDecimal adjustment = isNegative ? precision.negate() : precision;

            // 按权重比例分配零头
            BigDecimal[] remainderParts = new BigDecimal[quantity];
            BigDecimal allocatedRemainder = BigDecimal.ZERO;

            for (int i = 0; i < quantity; i++) {
                // 计算该位置应分配的零头
                BigDecimal weightRatio = weights[i].divide(totalWeight, 10, RoundingMode.HALF_UP);
                BigDecimal remainderPart = totalRemainder.multiply(weightRatio);

                // 正数向下取整，负数向上取整
                remainderParts[i] = isNegative
                        ? remainderPart.setScale(scale, RoundingMode.UP)
                        : remainderPart.setScale(scale, RoundingMode.DOWN);
                allocatedRemainder = allocatedRemainder.add(remainderParts[i]);
            }

            // 计算剩余未分配的零头
            BigDecimal remainingRemainder = totalRemainder.subtract(allocatedRemainder);
            int remainingUnits = remainingRemainder.abs().divide(precision, 0, RoundingMode.DOWN).intValue();

            // 按顺序分配剩余零头单位
            if (remainingUnits > 0) {
                // 1. 按权重从大到小排序位置索引
                Integer[] sortedIndices = IntStream.range(0, quantity)
                        .boxed()
                        .sorted((i, j) -> weights[j].compareTo(weights[i]))
                        .toArray(Integer[]::new);

                // 2. 优先分配给零头为0的位置
                for (int index : sortedIndices) {
                    if (remainingUnits <= 0) break;

                    // 只给零头为0的位置分配
                    if (remainderParts[index].compareTo(BigDecimal.ZERO) == 0) {
                        remainderParts[index] = remainderParts[index].add(adjustment);
                        remainingUnits--;
                    }
                }

                // 3. 如果还有剩余零头，继续按权重从大到小分配
                while (remainingUnits > 0) {
                    for (int index : sortedIndices) {
                        if (remainingUnits <= 0) break;
                        remainderParts[index] = remainderParts[index].add(adjustment);
                        remainingUnits--;
                    }
                }
            }

            // 应用零头分配结果
            for (int i = 0; i < quantity; i++) {
                result[i] = result[i].add(remainderParts[i]);
            }

            return result;
        };
    }
}