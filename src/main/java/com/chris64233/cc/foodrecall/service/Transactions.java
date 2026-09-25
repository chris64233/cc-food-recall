package com.chris64233.cc.foodrecall.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * 在事务中执行写操作；若因并发插入同一唯一键触发约束冲突，
 * 则重试一次，让第二次执行走幂等查询路径返回已有记录或报 409。
 */
@Component
public class Transactions {

    private final TransactionTemplate template;

    public Transactions(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
    }

    public <T> T idempotent(Supplier<T> work) {
        try {
            return template.execute(status -> work.get());
        } catch (DataIntegrityViolationException ex) {
            return template.execute(status -> work.get());
        }
    }
}
