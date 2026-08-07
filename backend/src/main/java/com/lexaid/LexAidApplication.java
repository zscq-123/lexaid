package com.lexaid;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * LexAid 法律学习系统启动类
 */
@SpringBootApplication
@MapperScan({"com.lexaid.auth", "com.lexaid.content", "com.lexaid.exam",
        "com.lexaid.relation", "com.lexaid.note", "com.lexaid.ai", "com.lexaid.stats"})
public class LexAidApplication {

    public static void main(String[] args) {
        SpringApplication.run(LexAidApplication.class, args);
    }
}
