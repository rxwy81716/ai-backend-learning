package com.jiabo.redis.controller;

import com.jiabo.redis.entity.User;
import com.jiabo.redis.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @Resource
    private UserService userService;

    // String 缓存查询
    @GetMapping("/user/string/{id}")
    public User stringTest(@PathVariable Long id) {
        return userService.getUserById(id);
    }

    // Hash 缓存查询
    @GetMapping("/user/hash/{id}")
    public User hashTest(@PathVariable Long id) {
        return userService.getUserByHash(id);
    }

    // 更新 + 删缓存
    @PutMapping("/user/update")
    public String update(User user) {
        userService.updateUser(user);
        return "更新成功";
    }
}
