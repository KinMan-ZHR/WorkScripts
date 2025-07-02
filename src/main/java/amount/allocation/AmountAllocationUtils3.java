package amount.allocation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 增强版金额分摊工具类
 * 核心功能：返回一维数组（基础金额+零头），支持灵活的零头集中分配策略
 */
public class AmountAllocationUtils3 {

    /**
     * 核心分摊方法：按指定算法分摊总金额，并根据零头策略调整结果
     *
     * @param totalAmount      待分摊总金额（必须≥0）
     * @param coreAllocator    分摊核心算法（输入总金额和数量，返回基础分摊数组）
     * @param quantity         分摊数量（必须≥1）
     * @param remainderStrategy 零头分摊策略（输入基础金额数组和总零头，返回调整后的最终金额数组）
     * @return 一维数组：每个位置的最终分摊金额（基础金额+零头调整）
     * @throws IllegalArgumentException 参数校验失败时抛出
     */
    public static BigDecimal[] allocate(
            BigDecimal totalAmount,
            BiFunction<BigDecimal, Integer, BigDecimal[]> coreAllocator,
            int quantity,
            Function<AllocationContext, BigDecimal[]> remainderStrategy
    ) {
        // 参数校验
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("总金额必须≥0");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("分摊数量必须≥1");
        }

        // 1. 执行核心分摊算法，获取基础金额数组
        BigDecimal[] baseAmounts = coreAllocator.apply(totalAmount, quantity);
        // 校验基础数组长度
        if (baseAmounts.length != quantity) {
            throw new IllegalArgumentException("核心算法返回的数组长度与分摊数量不一致");
        }

        // 2. 计算总零头（总金额 - 基础金额总和）
        BigDecimal baseSum = Arrays.stream(baseAmounts).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalRemainder = totalAmount.subtract(baseSum);

        // 3. 执行零头分摊策略，获取最终金额数组
        AllocationContext context = new AllocationContext(baseAmounts, totalRemainder);
        return remainderStrategy.apply(context);
    }

    // ------------------------------ 内置分摊算法 ------------------------------

    /**
     * 均摊算法：将总金额平均分配（向下取整保留两位小数）
     */
    public static BiFunction<BigDecimal, Integer, BigDecimal[]> evenAllocator() {
        return (total, quantity) -> {
            BigDecimal[] result = new BigDecimal[quantity];
            BigDecimal average = total.divide(BigDecimal.valueOf(quantity), 2, RoundingMode.DOWN);
            Arrays.fill(result, average);
            return result;
        };
    }

    /**
     * 加权分摊算法：按权重比例分配（向下取整保留两位小数）
     */
    public static BiFunction<BigDecimal, Integer, BigDecimal[]> weightedAllocator(BigDecimal[] weights) {
        return (total, quantity) -> {
            // 校验权重
            if (weights == null || weights.length != quantity) {
                throw new IllegalArgumentException("权重数组长度与分摊数量不一致");
            }
            BigDecimal totalWeight = Arrays.stream(weights).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (totalWeight.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("权重总和必须大于0");
            }

            BigDecimal[] result = new BigDecimal[quantity];
            BigDecimal allocated = BigDecimal.ZERO;
            // 前n-1个按比例分配（向下取整）
            for (int i = 0; i < quantity - 1; i++) {
                BigDecimal ratio = weights[i].divide(totalWeight, 10, RoundingMode.HALF_UP);
                result[i] = total.multiply(ratio).setScale(2, RoundingMode.DOWN);
                allocated = allocated.add(result[i]);
            }
            // 最后一个用总金额补齐（避免精度误差）
            result[quantity - 1] = total.subtract(allocated).setScale(2, RoundingMode.DOWN);
            return result;
        };
    }

    // ------------------------------ 内置零头策略 ------------------------------

    /**
     * 零头策略：将零头集中分配到指定位置
     *
     * @param position 指定位置索引（0~n-1）
     */
    public static Function<AllocationContext, BigDecimal[]>certainRemainder(int position) {
        return context -> {
            BigDecimal[] result = Arrays.copyOf(context.getBaseAmounts(), context.getBaseAmounts().length);
            if (position < 0 || position >= result.length) {
                throw new IllegalArgumentException("集中分配位置超出范围");
            }
            result[position] = result[position].add(context.getTotalRemainder());
            return result;
        };
    }

    /**
     * 零头策略：将零头集中分配到最小值位置（多个最小值时选择第一个）
     */
    public static Function<AllocationContext, BigDecimal[]> minValueRemainder() {
        return context -> {
            BigDecimal[] result = Arrays.copyOf(context.getBaseAmounts(), context.getBaseAmounts().length);
            if (context.getTotalRemainder().compareTo(BigDecimal.ZERO) <= 0) {
                return result;
            }
            int minIndex = 0;
            for (int i = 1; i < result.length; i++) {
                if (result[i].compareTo(result[minIndex]) < 0) {
                    minIndex = i;
                }
            }
            result[minIndex] = result[minIndex].add(context.getTotalRemainder());
            return result;
        };
    }

    /**
     * 零头策略：将零头集中分配到最大值位置（多个最大值时选择第一个）
     */
    public static Function<AllocationContext, BigDecimal[]> maxValueRemainder() {
        return context -> {
            BigDecimal[] result = Arrays.copyOf(context.getBaseAmounts(), context.getBaseAmounts().length);
            if (context.getTotalRemainder().compareTo(BigDecimal.ZERO) <= 0) {
                return result;
            }
            int maxIndex = 0;
            for (int i = 1; i < result.length; i++) {
                if (result[i].compareTo(result[maxIndex]) > 0) {
                    maxIndex = i;
                }
            }
            result[maxIndex] = result[maxIndex].add(context.getTotalRemainder());
            return result;
        };
    }

    /**
     * 零头策略：按金额从小到大的顺序分配零头（每个位置最多分配0.01）
     */
    public static Function<AllocationContext, BigDecimal[]> sequentialByMinValue() {
        return context -> {
            BigDecimal[] result = Arrays.copyOf(context.getBaseAmounts(), context.getBaseAmounts().length);
            BigDecimal totalRemainder = context.getTotalRemainder();
            if (totalRemainder.compareTo(BigDecimal.ZERO) <= 0) {
                return result;
            }

            // 转换零头为最小单位（分）
            int cents = totalRemainder.multiply(new BigDecimal("100"))
                    .setScale(0, RoundingMode.DOWN).intValue();

            // 创建带索引的金额列表，按金额排序
            List<IndexedAmount> indexedAmounts = new ArrayList<>();
            for (int i = 0; i < result.length; i++) {
                indexedAmounts.add(new IndexedAmount(i, result[i]));
            }
            indexedAmounts.sort(Comparator.comparing(IndexedAmount::getAmount));

            // 按金额从小到大的顺序分配零头
            for (int i = 0; i < indexedAmounts.size() && cents > 0; i++) {
                int index = indexedAmounts.get(i).getIndex();
                result[index] = result[index].add(new BigDecimal("0.01"));
                cents--;
            }
            return result;
        };
    }

    /**
     * 零头策略：按金额从大到小的顺序分配零头（每个位置最多分配0.01）
     */
    public static Function<AllocationContext, BigDecimal[]> sequentialByMaxValue() {
        return context -> {
            BigDecimal[] result = Arrays.copyOf(context.getBaseAmounts(), context.getBaseAmounts().length);
            BigDecimal totalRemainder = context.getTotalRemainder();
            if (totalRemainder.compareTo(BigDecimal.ZERO) <= 0) {
                return result;
            }

            int cents = totalRemainder.multiply(new BigDecimal("100"))
                    .setScale(0, RoundingMode.DOWN).intValue();

            // 创建带索引的金额列表，按金额降序排序
            List<IndexedAmount> indexedAmounts = new ArrayList<>();
            for (int i = 0; i < result.length; i++) {
                indexedAmounts.add(new IndexedAmount(i, result[i]));
            }
            indexedAmounts.sort(Comparator.comparing(IndexedAmount::getAmount).reversed());

            // 按金额从大到小的顺序分配零头
            for (int i = 0; i < indexedAmounts.size() && cents > 0; i++) {
                int index = indexedAmounts.get(i).getIndex();
                result[index] = result[index].add(new BigDecimal("0.01"));
                cents--;
            }
            return result;
        };
    }

    // ------------------------------ 内部类 ------------------------------

    /**
     * 分摊上下文：封装基础金额和总零头信息
     */
    public static class AllocationContext {
        private final BigDecimal[] baseAmounts;
        private final BigDecimal totalRemainder;

        public AllocationContext(BigDecimal[] baseAmounts, BigDecimal totalRemainder) {
            this.baseAmounts = baseAmounts;
            this.totalRemainder = totalRemainder;
        }

        public BigDecimal[] getBaseAmounts() {
            return baseAmounts;
        }

        public BigDecimal getTotalRemainder() {
            return totalRemainder;
        }
    }

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