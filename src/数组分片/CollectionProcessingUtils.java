package 数组分片;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 集合处理工具类
 * 提供集合分片处理、结果合并等功能，支持函数式编程范式
 *
 * <h2>使用方法</h2>
 * <pre>
 * // 示例1：将列表按每3个元素一组进行处理，最后合并结果
 * List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5, 6, 7);
 * Function<List<Integer>, List<Integer>> enhancedProcessor =
 *     CollectionProcessingUtils.enhanceProcessor(
 *         subList -> subList.stream().map(n -> n * 2).collect(Collectors.toList()),
 *         3,
 *         CollectionProcessingUtils.listMerger(),
 *         CollectionProcessingUtils.arrayListSupplier()
 *     );
 * List<Integer> result = enhancedProcessor.apply(numbers);
 * // 结果: [2, 4, 6, 8, 10, 12, 14]
 *
 * // 示例2：将集合分片后进行处理，最终合并为一个Set
 * Set<String> words = new HashSet<>(Arrays.asList("apple", "banana", "cherry", "date"));
 * Function<Set<String>, Set<String>> processor =
 *     CollectionProcessingUtils.enhanceProcessor(
 *         subSet -> subSet.stream().map(String::toUpperCase).collect(Collectors.toSet()),
 *         2,
 *         CollectionProcessingUtils.setMerger(),
 *         CollectionProcessingUtils.hashSetSupplier()
 *     );
 * Set<String> upperCaseWords = processor.apply(words);
 * // 结果: [APPLE, BANANA, CHERRY, DATE]
 *
 * // 示例3：将多个集合并行处理后扁平化
 * List<List<Integer>> nestedLists = Arrays.asList(
 *     Arrays.asList(1, 2),
 *     Arrays.asList(3, 4),
 *     Arrays.asList(5, 6)
 * );
 * Function<List<List<Integer>>, List<Integer>> flattenProcessor =
 *     CollectionProcessingUtils.enhanceProcessor(
 *         subList -> subList.stream().map(n -> n * n).collect(Collectors.toList()),
 *         1,
 *         CollectionProcessingUtils.flattenListMerger(),
 *         CollectionProcessingUtils.arrayListSupplier()
 *     );
 * List<Integer> flattenedResult = flattenProcessor.apply(nestedLists);
 * // 结果: [1, 4, 9, 16, 25, 36]
 *
 * // 示例4：实际业务场景 - 批量查询班级信息并处理结果
 * Result<List<ThreeLevelSchoolAreaInfo>> schoolAreaResult = userClientV2.queryAllThreeLevelSchoolAreaInfo();
 * List<ThreeLevelSchoolAreaInfo> schoolAreaInfos = new ArrayList<>();
 * if (schoolAreaResult.isSuccess()) {
 *     schoolAreaInfos = schoolAreaResult.getData();
 * }
 * Map<Long, ThreeLevelSchoolAreaInfo> schoolAreaInfoMap = schoolAreaInfos.stream()
 *     .collect(Collectors.toMap(ThreeLevelSchoolAreaInfo::getSchoolAreaId, v -> v));
 *
 * List<Long> classIds = objectPageInfo.getList().stream()
 *     .map(ReturnQueryOut::getClassId)
 *     .distinct()
 *     .collect(Collectors.toList());
 *
 * List<ClassDTO> classDTOList = CollectionProcessingUtils.enhanceProcessor(
 *     classClient::batchQueryClasses,  // 班级批量查询方法
 *     20000,                           // 分片大小
 *     results -> {                     // 自定义结果合并器
 *         List<ClassDTO> oneList = new ArrayList<>();
 *         for (Result<List<ClassDTO>> result : results) {
 *             if (result == null || !result.isSuccess()) {
 *                 throw com.jiuaoedu.common.BusinessException.of("查询班级对应校区信息失败！");
 *             } else if (result.getData() != null) {
 *                 oneList.addAll(result.getData());
 *             }
 *         }
 *         return oneList;
 *     },
 *     CollectionProcessingUtils.arrayListSupplier()  // 使用ArrayList作为分片集合
 * ).apply(classIds);
 * </pre>
 *
 * @author zhanghaoran or KinMan Zhang
 * @date 2025/06/12
 */
public class CollectionProcessingUtils {

    /**
     * 集合处理器函数式接口
     * @param <I> 输入集合类型，必须是Collection<T>的子类
     * @param <O> 处理结果类型
     * @param <T> 集合元素类型
     */
    @FunctionalInterface
    public interface CollectionProcessor<I extends Collection<T>, O, T> {
        /**
         * 处理集合的方法
         * @param collection 输入集合
         * @return 处理结果
         */
        O process(I collection);
    }

    /**
     * 结果合并器函数式接口
     * @param <O> 待合并的结果类型
     * @param <R> 合并后的结果类型
     */
    @FunctionalInterface
    public interface ResultMerger<O, R> {
        /**
         * 合并多个结果的方法
         * @param results 待合并的结果列表
         * @return 合并后的结果
         */
        R merge(List<O> results);
    }

    /**
     * 集合供应商函数式接口
     * @param <I> 集合类型，必须是Collection<T>的子类
     * @param <T> 集合元素类型
     */
    @FunctionalInterface
    public interface CollectionSupplier<I extends Collection<T>, T> {
        /**
         * 创建集合实例的方法
         * @return 新的集合实例
         */
        I create();
    }

    /**
     * 增强集合处理器，支持分片处理
     *
     * @param <I> 输入集合类型
     * @param <O> 单次处理结果类型
     * @param <R> 最终合并结果类型
     * @param <T> 集合元素类型
     *
     * @param processor 集合处理器
     * @param sliceSize 分片大小
     * @param merger 结果合并器
     * @param collectionSupplier 集合供应商，用于创建分片集合
     *
     * @return 增强后的处理器函数，支持对输入集合进行分片处理并合并结果
     */
    public static <I extends Collection<T>, O, R, T>
    Function<I, R> enhanceProcessor(
            CollectionProcessor<I, O, T> processor,
            int sliceSize,
            ResultMerger<O, R> merger,
            CollectionSupplier<I, T> collectionSupplier) {

        return collection -> {
            if (collection == null || collection.isEmpty()) {
                return merger.merge(Collections.emptyList());
            }

            List<O> results = new ArrayList<>();
            List<T> elements = new ArrayList<>(collection);

            // 对集合进行分片处理
            for (int i = 0; i < elements.size(); i += sliceSize) {
                I subCollection = collectionSupplier.create();
                subCollection.addAll(elements.subList(i, Math.min(i + sliceSize, elements.size())));
                results.add(processor.process(subCollection));
            }

            return merger.merge(results);
        };
    }

    /**
     * 获取ArrayList供应商实例
     * @param <T> 集合元素类型
     * @return ArrayList供应商
     */
    public static <T> CollectionSupplier<List<T>, T> arrayListSupplier() {
        return ArrayList::new;
    }

    /**
     * 获取HashSet供应商实例
     * @param <T> 集合元素类型
     * @return HashSet供应商
     */
    public static <T> CollectionSupplier<Set<T>, T> hashSetSupplier() {
        return HashSet::new;
    }

    /**
     * 获取列表合并器实例，直接返回原列表
     * @param <O> 列表元素类型
     * @return 列表合并器
     */
    public static <O> ResultMerger<O, List<O>> listMerger() {
        return results -> results;
    }

    /**
     * 获取集合合并器实例，将多个集合合并为一个Set
     * @param <O> 集合元素类型
     * @return 集合合并器
     */
    public static <O> ResultMerger<O, Set<O>> setMerger() {
        return HashSet::new;
    }

    /**
     * 获取扁平化列表合并器实例，将多个集合合并为一个扁平化列表
     * @param <O> 输入集合类型，必须是Collection<E>的子类
     * @param <E> 集合元素类型
     * @return 扁平化列表合并器
     */
    public static <O extends Collection<E>, E> ResultMerger<O, List<E>> flattenListMerger() {
        return results -> results.stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }
}