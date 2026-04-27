package com.jianbo.localaiknowledge.service;

import com.jianbo.localaiknowledge.mapper.SystemPromptMapper;
import com.jianbo.localaiknowledge.model.SystemPrompt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * SystemPrompt 管理服务（Caffeine 缓存加速）
 *
 * 高并发下避免每次 RAG 请求都查 DB，缓存 10 分钟自动过期。
 * 写操作（增/改/设默认）会主动清除缓存。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SystemPromptService {

    private final SystemPromptMapper promptMapper;

    /** 获取默认 Prompt（高频调用，走缓存） */
    @Cacheable(value = "systemPrompt", key = "'default'")
    public SystemPrompt getDefault() {
        log.debug("加载默认 SystemPrompt（缓存未命中）");
        return promptMapper.selectDefault();
    }

    /** 按名称获取 Prompt（走缓存） */
    @Cacheable(value = "systemPrompt", key = "#name")
    public SystemPrompt getByName(String name) {
        log.debug("加载 SystemPrompt: {}（缓存未命中）", name);
        return promptMapper.selectByName(name);
    }

    /** 获取所有 Prompt */
    public List<SystemPrompt> getAll() {
        return promptMapper.selectAll();
    }

    /** 创建 Prompt */
    @CacheEvict(value = "systemPrompt", allEntries = true)
    public void create(SystemPrompt prompt) {
        promptMapper.insert(prompt);
        log.info("SystemPrompt 已创建: {}", prompt.getName());
    }

    /** 更新 Prompt */
    @CacheEvict(value = "systemPrompt", allEntries = true)
    public void update(SystemPrompt prompt) {
        promptMapper.update(prompt);
        log.info("SystemPrompt 已更新: {}", prompt.getName());
    }

    /** 设置默认 Prompt */
    @CacheEvict(value = "systemPrompt", allEntries = true)
    public void setDefault(String name) {
        promptMapper.clearDefault();
        SystemPrompt prompt = promptMapper.selectByName(name);
        if (prompt != null) {
            prompt.setIsDefault(true);
            promptMapper.update(prompt);
            log.info("默认 SystemPrompt 已切换为: {}", name);
        }
    }
}
