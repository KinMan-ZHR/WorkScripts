package AIChat;

import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

// 方案1：使用信号量实现
class DiningPhilosophersSemaphore {
    private final int N;
    private final Semaphore[] forks;
    private final Semaphore maxDiners; // 限制最多4个哲学家同时进餐，避免死锁

    public DiningPhilosophersSemaphore(int n) {
        N = n;
        forks = new Semaphore[n];
        for (int i = 0; i < n; i++) {
            forks[i] = new Semaphore(1); // 每个叉子一个许可
        }
        maxDiners = new Semaphore(n - 1); // 最多允许n-1个哲学家同时进餐
    }

    public void lifecycle(int id) throws InterruptedException {
        while (true) {
            think(id);
            maxDiners.acquire(); // 尝试获取进餐许可
            try {
                forks[id].acquire(); // 拿起左边的叉子
                System.out.println("哲学家 " + id + " 拿起了左边的叉子");
                forks[(id + 1) % N].acquire(); // 拿起右边的叉子
                System.out.println("哲学家 " + id + " 拿起了右边的叉子，开始用餐");
                eat(id);
                forks[(id + 1) % N].release(); // 放下右边的叉子
                System.out.println("哲学家 " + id + " 放下了右边的叉子");
                forks[id].release(); // 放下左边的叉子
                System.out.println("哲学家 " + id + " 放下了左边的叉子");
            } finally {
                maxDiners.release(); // 释放进餐许可
            }
        }
    }

    private void think(int id) throws InterruptedException {
        System.out.println("哲学家 " + id + " 在思考");
        Thread.sleep((long) (Math.random() * 1000));
    }

    private void eat(int id) throws InterruptedException {
        System.out.println("哲学家 " + id + " 在进餐");
        Thread.sleep((long) (Math.random() * 1000));
    }

    public static void main(String[] args) {
        final int N = 5;
        DiningPhilosophersSemaphore dining = new DiningPhilosophersSemaphore(N);

        for (int i = 0; i < N; i++) {
            final int philosopherId = i;
            new Thread(() -> {
                try {
                    dining.lifecycle(philosopherId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
    }
}