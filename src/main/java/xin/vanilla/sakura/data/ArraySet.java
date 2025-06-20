package xin.vanilla.sakura.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ArraySet<E> {
    private final List<E> list = new ArrayList<>();

    public ArraySet() {
    }

    public ArraySet(Collection<? extends E> elements) {
        this.list.addAll(elements);
    }

    /**
     * 添加元素，返回是否成功添加
     */
    public boolean add(E e) {
        if (!list.contains(e)) {
            list.add(e);
            return true;
        }
        return false;
    }

    /**
     * 添加元素，返回是否成功添加
     */
    public boolean addAll(Collection<? extends E> elements) {
        return this.list.addAll(elements);
    }

    /**
     * 添加元素，返回是否成功添加
     */
    public boolean addAll(int index, Collection<? extends E> c) {
        return this.list.addAll(index, c);
    }

    /**
     * 移除元素，返回是否成功移除
     */
    public boolean remove(E e) {
        return list.remove(e);
    }

    /**
     * 获取指定索引处的元素
     */
    public E get(int index) {
        return list.get(index);
    }

    /**
     * 获取元素的索引，如果不存在返回 -1
     */
    public int indexOf(E e) {
        return list.indexOf(e);
    }

    /**
     * 返回集合的大小
     */
    public int size() {
        return list.size();
    }

    /**
     * 检查是否包含某个元素
     */
    public boolean contains(E e) {
        return list.contains(e);
    }

    /**
     * 清空集合
     */
    public void clear() {
        list.clear();
    }

    /**
     * 获取不可变列表视图
     */
    public List<E> asList() {
        return Collections.unmodifiableList(list);
    }
}
