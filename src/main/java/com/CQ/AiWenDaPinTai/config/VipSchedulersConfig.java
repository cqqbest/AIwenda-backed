package com.CQ.AiWenDaPinTai.config;

import io.reactivex.Scheduler;
import io.reactivex.schedulers.Schedulers;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 设置vip线程
 */
@Configuration
public class VipSchedulersConfig {
    @Bean
    public Scheduler vipSchedulers(){
        ThreadFactory threadFactory = new ThreadFactory() {
            //多线程环境下计数
            private final AtomicInteger index = new AtomicInteger(1);
            @Override
            public Thread newThread(@NotNull Runnable r) {
                Thread thread = new Thread(r);
                //设置当前线程名称方便测试
                thread.setName("vip-thread-" + index.getAndIncrement());
                //设置线程为非守护线程
                thread.setDaemon(false);
                return thread;
            }
        };
        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(10, threadFactory);
        return Schedulers.from(scheduledExecutorService);
    }
}
