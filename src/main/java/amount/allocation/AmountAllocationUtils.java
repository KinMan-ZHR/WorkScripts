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
 * 终极版金额分摊工具类
 * 核心功能：通过单一分摊算法统一生成基础金额和零头调整，返回最终分摊结果
 */
public class AmountAllocationUtils {

    /**
     * 核心分摊方法：通过单一算法生成基础金额和零头调整，并返回最终结果
     *
     * @param totalAmount 待分摊总金额（必须≥0）
     * @param allocator   分摊算法（输入总金额和数量，返回完整的分摊结果）
     * @return 一维数组：每个位置的最终分摊金额
     */
    public static BigDecimal[] allocate(
            BigDecimal totalAmount,
            Allocator allocator
    ) {
        // 参数校验
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("总金额必须≥0");
        }

        // 执行分摊算法
        return allocator.allocate(totalAmount);
    }

    // ------------------------------ 分摊算法接口 ------------------------------

    /**
     * 分摊算法接口：定义如何将总金额分配到多个位置
     */
    @FunctionalInterface
    public interface Allocator {
        /**
         * 执行金额分摊
         * @param totalAmount 总金额
         * @return 分摊结果数组（必须确保总和等于总金额）
         */
        BigDecimal[] allocate(BigDecimal totalAmount);
    }

    // ------------------------------ 内置分摊算法 ------------------------------

    /**
     * 均摊算法：平均分配金额，零头按指定策略处理
     *
     * @param quantity 分摊数量
     * @param remainderStrategy 零头处理策略
     */
    public static Allocator evenAllocator(int quantity, RemainderStrategy remainderStrategy) {
        return totalAmount -> {
            if (quantity <= 0) {
                throw new IllegalArgumentException("分摊数量必须≥1");
            }

            // 计算基础金额（向下取整）
            BigDecimal baseAmount = totalAmount.divide(BigDecimal.valueOf(quantity), 2, RoundingMode.DOWN);
            BigDecimal[] result = new BigDecimal[quantity];
            Arrays.fill(result, baseAmount);

            // 计算总零头
            BigDecimal totalRemainder = totalAmount.subtract(baseAmount.multiply(BigDecimal.valueOf(quantity)));
            
            // 应用零头策略
            return remainderStrategy.apply(result, totalRemainder);
        };
    }

    /**
     * 加权分摊算法：按权重比例分配金额，零头按指定策略处理
     *
     * @param weights 权重数组
     * @param remainderStrategy 零头处理策略
     */
    public static Allocator weightedAllocator(BigDecimal[] weights, RemainderStrategy remainderStrategy) {
        return totalAmount -> {
            int quantity = weights.length;
            if (quantity <= 0) {
                throw new IllegalArgumentException("权重数组不能为空");
            }

            // 计算总权重
            BigDecimal totalWeight = Arrays.stream(weights).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (totalWeight.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("总权重必须大于0");
            }

            // 按权重分配基础金额（向下取整）
            BigDecimal[] result = new BigDecimal[quantity];
            BigDecimal allocated = BigDecimal.ZERO;
            
            for (int i = 0; i < quantity - 1; i++) {
                BigDecimal weightRatio = weights[i].divide(totalWeight, 10, RoundingMode.HALF_UP);
                result[i] = totalAmount.multiply(weightRatio).setScale(2, RoundingMode.DOWN);
                allocated = allocated.add(result[i]);
            }
            
            // 最后一个位置用剩余金额（避免精度误差）
            result[quantity - 1] = totalAmount.subtract(allocated).setScale(2, RoundingMode.DOWN);
            
            // 计算总零头（可能为负数，表示分配超额）
            BigDecimal totalRemainder = totalAmount.subtract(
                Arrays.stream(result).reduce(BigDecimal.ZERO, BigDecimal::add)
            );
            
            // 应用零头策略
            return remainderStrategy.apply(result, totalRemainder);
        };
    }

    // ------------------------------ 零头处理策略 ------------------------------

    /**
     * 零头处理策略接口：定义如何调整基础金额以处理零头
     */
    @FunctionalInterface
    public interface RemainderStrategy {
        /**
         * 应用零头调整
         * @param baseAmounts 基础金额数组
         * @param totalRemainder 总零头（可能为负数，表示分配超额）
         * @return 调整后的金额数组（必须确保总和等于总金额）
         */
        BigDecimal[] apply(BigDecimal[] baseAmounts, BigDecimal totalRemainder);
    }

    // ------------------------------ 内置零头策略 ------------------------------

    /**
     * 零头策略：按顺序分配零头（前n个位置各加0.01，直到零头分完）
     */
    public static RemainderStrategy sequential() {
        return (baseAmounts, totalRemainder) -> {
            BigDecimal[] result = Arrays.copyOf(baseAmounts, baseAmounts.length);
            if (totalRemainder.compareTo(BigDecimal.ZERO) == 0) {
                return result;
            }

            // 转换零头为最小单位（分）
            int cents = totalRemainder.multiply(new BigDecimal("100"))
                    .setScale(0, RoundingMode.DOWN).intValue();
            
            // 正数零头：按顺序增加
            if (cents > 0) {
                for (int i = 0; i < result.length && cents > 0; i++) {
                    result[i] = result[i].add(new BigDecimal("0.01"));
                    cents--;
                }
            } 
            // 负数零头：按顺序减少（绝对值处理）
            else if (cents < 0) {
                cents = -cents;
                for (int i = 0; i < result.length && cents > 0; i++) {
                    result[i] = result[i].subtract(new BigDecimal("0.01"));
                    cents--;
                }
            }
            
            return result;
        };
    }

    /**
     * 零头策略：随机分配零头
     */
    public static RemainderStrategy random() {
        return (baseAmounts, totalRemainder) -> {
            BigDecimal[] result = Arrays.copyOf(baseAmounts, baseAmounts.length);
            if (totalRemainder.compareTo(BigDecimal.ZERO) == 0) {
                return result;
            }

            int cents = totalRemainder.multiply(new BigDecimal("100"))
                    .setScale(0, RoundingMode.DOWN).intValue();
            
            // 正数零头：随机增加
            if (cents > 0) {
                List<Integer> positions = IntStream.range(0, result.length)
                        .boxed().collect(Collectors.toList());
                Collections.shuffle(positions);
                
                for (int i = 0; i < positions.size() && cents > 0; i++) {
                    result[positions.get(i)] = result[positions.get(i)].add(new BigDecimal("0.01"));
                    cents--;
                }
            } 
            // 负数零头：随机减少
            else if (cents < 0) {
                cents = -cents;
                List<Integer> positions = IntStream.range(0, result.length)
                        .boxed().collect(Collectors.toList());
                Collections.shuffle(positions);
                
                for (int i = 0; i < positions.size() && cents > 0; i++) {
                    result[positions.get(i)] = result[positions.get(i)].subtract(new BigDecimal("0.01"));
                    cents--;
                }
            }
            
            return result;
        };
    }

    /**
     * 零头策略：集中分配到最小值位置
     */
    public static RemainderStrategy minValue() {
        return (baseAmounts, totalRemainder) -> {
            BigDecimal[] result = Arrays.copyOf(baseAmounts, baseAmounts.length);
            if (totalRemainder.compareTo(BigDecimal.ZERO) == 0) {
                return result;
            }

            // 找到最小值位置
            int minIndex = 0;
            for (int i = 1; i < result.length; i++) {
                if (result[i].compareTo(result[minIndex]) < 0) {
                    minIndex = i;
                }
            }
            
            // 将零头集中到最小值位置
            result[minIndex] = result[minIndex].add(totalRemainder);
            return result;
        };
    }

    /**
     * 零头策略：集中分配到最大值位置
     */
    public static RemainderStrategy maxValue() {
        return (baseAmounts, totalRemainder) -> {
            BigDecimal[] result = Arrays.copyOf(baseAmounts, baseAmounts.length);
            if (totalRemainder.compareTo(BigDecimal.ZERO) == 0) {
                return result;
            }

            // 找到最大值位置
            int maxIndex = 0;
            for (int i = 1; i < result.length; i++) {
                if (result[i].compareTo(result[maxIndex]) > 0) {
                    maxIndex = i;
                }
            }
            
            // 将零头集中到最大值位置
            result[maxIndex] = result[maxIndex].add(totalRemainder);
            return result;
        };
    }

    /**
     * 零头策略：按金额从小到大的顺序分配零头
     */
    public static RemainderStrategy sequentialByMinValue() {
        return (baseAmounts, totalRemainder) -> {
            BigDecimal[] result = Arrays.copyOf(baseAmounts, baseAmounts.length);
            if (totalRemainder.compareTo(BigDecimal.ZERO) == 0) {
                return result;
            }

            int cents = totalRemainder.multiply(new BigDecimal("100"))
                    .setScale(0, RoundingMode.DOWN).intValue();
            
            // 创建带索引的金额列表，按金额排序
            List<IndexedAmount> indexedAmounts = IntStream.range(0, result.length)
                    .mapToObj(i -> new IndexedAmount(i, result[i]))
                    .collect(Collectors.toList());
            
            // 正数零头：从小到大增加
            if (cents > 0) {
                indexedAmounts.sort(Comparator.comparing(IndexedAmount::getAmount));
                for (IndexedAmount item : indexedAmounts) {
                    if (cents <= 0) break;
                    result[item.getIndex()] = result[item.getIndex()].add(new BigDecimal("0.01"));
                    cents--;
                }
            } 
            // 负数零头：从小到大减少（绝对值处理）
            else if (cents < 0) {
                cents = -cents;
                indexedAmounts.sort(Comparator.comparing(IndexedAmount::getAmount));
                for (IndexedAmount item : indexedAmounts) {
                    if (cents <= 0) break;
                    result[item.getIndex()] = result[item.getIndex()].subtract(new BigDecimal("0.01"));
                    cents--;
                }
            }
            
            return result;
        };
    }

    /**
     * 零头策略：按金额从大到小的顺序分配零头
     */
    public static RemainderStrategy sequentialByMaxValue() {
        return (baseAmounts, totalRemainder) -> {
            BigDecimal[] result = Arrays.copyOf(baseAmounts, baseAmounts.length);
            if (totalRemainder.compareTo(BigDecimal.ZERO) == 0) {
                return result;
            }

            int cents = totalRemainder.multiply(new BigDecimal("100"))
                    .setScale(0, RoundingMode.DOWN).intValue();
            
            // 创建带索引的金额列表，按金额排序
            List<IndexedAmount> indexedAmounts = IntStream.range(0, result.length)
                    .mapToObj(i -> new IndexedAmount(i, result[i]))
                    .collect(Collectors.toList());
            
            // 正数零头：从大到小增加
            if (cents > 0) {
                indexedAmounts.sort(Comparator.comparing(IndexedAmount::getAmount).reversed());
                for (IndexedAmount item : indexedAmounts) {
                    if (cents <= 0) break;
                    result[item.getIndex()] = result[item.getIndex()].add(new BigDecimal("0.01"));
                    cents--;
                }
            } 
            // 负数零头：从大到小减少
            else if (cents < 0) {
                cents = -cents;
                indexedAmounts.sort(Comparator.comparing(IndexedAmount::getAmount).reversed());
                for (IndexedAmount item : indexedAmounts) {
                    if (cents <= 0) break;
                    result[item.getIndex()] = result[item.getIndex()].subtract(new BigDecimal("0.01"));
                    cents--;
                }
            }
            
            return result;
        };
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