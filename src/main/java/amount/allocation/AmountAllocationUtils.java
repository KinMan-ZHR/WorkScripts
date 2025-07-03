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
        BigDecimal[] baseAmounts = baseAllocator.allocate(totalAmount, quantity, precision);

        // 计算零头（确保为正）
        BigDecimal baseSum = Arrays.stream(baseAmounts).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalRemainder = totalAmount.subtract(baseSum);

        // 应用零头策略
        BigDecimal[] result = remainderStrategy.apply(baseAmounts, totalRemainder, precision);

        // 验证结果总和是否等于总金额
        BigDecimal resultSum = Arrays.stream(result).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (resultSum.compareTo(totalAmount) != 0) {
            throw new IllegalStateException(
                    String.format("分摊结果总和不正确：预期 %s，实际 %s",
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
        BigDecimal[] allocate(BigDecimal totalAmount, int quantity, BigDecimal precision);
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
                BigDecimal weightRatio = weights[i].divide(totalWeight, 10, RoundingMode.DOWN);
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
        if (units > 0) {
            for (int pos : positions) {
                if (units == 0) break;
                result[pos] = result[pos].add(precision);
                units--;
            }
        }
        // 负数：减
        else if (units < 0) {
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
}