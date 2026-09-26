package com.chris64233.cc.foodrecall.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * 在事务中执行写操作；若因并发插入同一唯一键触发约束冲突，
 * 则重试一次，让第二次执行走幂等查询路径返回已有记录或报 409。
 */
@Component
public class Transactions {

    private final TransactionTemplate template;
    private final TransactionTemplate requiresNewTemplate;

    public Transactions(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
        this.requiresNewTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public <T> T idempotent(Supplier<T> work) {
        try {
            return template.execute(status -> work.get());
        } catch (DataIntegrityViolationException ex) {
            return template.execute(status -> work.get());
        }
    }

    /**
     * 在当前事务成功提交后、于全新事务中执行收尾工作（如释放隔离标记），
     * 避免为了更新批次行而在持有召回行锁时与并发转换形成反向加锁。
     * afterCompletion 阶段原事务同步尚未解绑，普通传播会加入已完成的事务，
     * 因此收尾事务必须显式使用 REQUIRES_NEW。
     */
    public void afterCommit(Runnable work) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_COMMITTED) {
                        requiresNewTemplate.executeWithoutResult(t -> work.run());
                    }
                }
            });
        } else {
            work.run();
        }
    }
}
