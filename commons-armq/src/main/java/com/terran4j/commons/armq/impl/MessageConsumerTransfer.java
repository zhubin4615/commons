package com.terran4j.commons.armq.impl;

import com.aliyun.mq.http.MQConsumer;
import com.terran4j.commons.armq.ConsumerConfig;
import com.terran4j.commons.armq.MessageConsumer;
import com.terran4j.commons.util.error.BusinessException;
import lombok.extern.slf4j.Slf4j;

/**
 * 起一组线程，以死循环的方式来拉取消息并执行。
 *
 * @param <T> 消息实例类型。
 */
@Slf4j
public class MessageConsumerTransfer<T> {

    private final MQConsumer mqConsumer;

    private final MessageConsumer<T> consumer;

    private final Class<T> messageEntityClass;

    private final ConsumerConfig config;

    private final MessageConsumerTask[] tasks;

    private final Thread[] threads;

    public MessageConsumer<T> getConsumer() {
        return consumer;
    }

    public Class<T> getMessageEntityClass() {
        return messageEntityClass;
    }

    public ConsumerConfig getConfig() {
        return config;
    }

    public MQConsumer getMqConsumer() {
        return mqConsumer;
    }

    public MessageConsumerTransfer(MQConsumer mqConsumer,
                                   MessageConsumer<T> consumer,
                                   Class<T> messageEntityClass,
                                   ConsumerConfig config) {
        this.mqConsumer = mqConsumer;
        this.consumer = consumer;
        this.messageEntityClass = messageEntityClass;
        this.config = config;

        int threadSize = config.getThreadSize();
        if (threadSize <= 0) {
            threadSize = 1;
        }
        if (threadSize > 100) {
            threadSize = 100; // 最大不能超过 100 个线程。
        }
        tasks = new MessageConsumerTask[threadSize];
        threads = new Thread[threadSize];
    }

    public void stop() {
        for (int i = 0; i < threads.length; i++) {
            // 如果线程还活着，则标记线程中断。
            Thread thread = threads[i];
            if (thread != null && thread.isAlive()) {
                thread.interrupt();
            }

            MessageConsumerTask<T> task = tasks[i];
            for (int j = 0; j < 10; j++) {
                if (task.isRunning()) {
                    try {
                        Thread.sleep(config.getPollingSecond() * 100);
                    } catch (InterruptedException e) {
                        log.error("stop consumer occur InterruptedException: {}", e.getMessage());
                    }
                }
            }
            threads[i] = null;
            tasks[i] = null;
        }
    }

    public void start() throws BusinessException {
        for (int i = 0; i < threads.length; i++) {

            // 如果线程还活着，则标记线程中断。
            Thread thread = threads[i];
            if (thread != null && thread.isAlive()) {
                thread.interrupt();
            }

            // 创建新的轮循任务及对应的线程对象。
            MessageConsumerTask task = new MessageConsumerTask(this);
            thread = new Thread(task);
            thread.setDaemon(true);
            String threadName = "ARMQ-Consumer-"
                    + MessageServiceImpl.getTopicName(messageEntityClass)
                    + "-" + i;
            thread.setName(threadName);
            thread.start();

            // 保持在数组中。
            tasks[i] = task;
            threads[i] = thread;
        }
    }

}
