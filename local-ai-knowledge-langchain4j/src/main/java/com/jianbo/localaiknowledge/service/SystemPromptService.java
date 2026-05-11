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
 * SystemPrompt 管理服务（与 Spring AI 版完全一致，无 AI 框架依赖）。
 * Caffeine 缓存加速，写操作主动清除缓存。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SystemPromptService {

    private final SystemPromptMapper promptMapper;

    @Cacheable(value = "systemPrompt", key = "'default'")
    public SystemPrompt getDefault() {
        log.debug("加载默认 SystemPrompt（缓存未命中）");
        return promptMapper.selectDefault();
    }

    @Cacheable(value = "systemPrompt", key = "#name")
    public SystemPrompt getByName(String name) {
        log.debug("加载 SystemPrompt: {}（缓存未命中）", name);
        return promptMapper.selectByName(name);
    }

    public List<SystemPrompt> getAll() {
        return promptMapper.selectAll();
    }

    @CacheEvict(value = "systemPrompt", allEntries = true)
    public void create(SystemPrompt prompt) {
        promptMapper.insert(prompt);
        log.info("SystemPrompt 已创建: {}", prompt.getName());
    }

    @CacheEvict(value = "systemPrompt", allEntries = true)
    public void update(SystemPrompt prompt) {
        promptMapper.update(prompt);
        log.info("SystemPrompt 已更新: {}", prompt.getName());
    }

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
