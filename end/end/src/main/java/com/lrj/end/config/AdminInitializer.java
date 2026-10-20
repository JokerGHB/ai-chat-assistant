package com.lrj.end.config;

import com.lrj.end.entity.SysUser;
import com.lrj.end.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 初始管理员创建器：应用启动时检查，若不存在 admin 账号则自动创建。
 * 初始密码从环境变量 ADMIN_INITIAL_PASSWORD 读取（默认 Admin@123），
 * 以 BCrypt 哈希入库。首次登录后应立即修改。
 */
@Component
public class AdminInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    private final SysUserMapper userMapper;
    private final String adminInitialPassword;

    public AdminInitializer(SysUserMapper userMapper,
                            @Value("${auth.admin-initial-password:Admin@123}") String adminInitialPassword) {
        this.userMapper = userMapper;
        this.adminInitialPassword = adminInitialPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userMapper.findByUsername("admin").isPresent()) {
            return;
        }
        SysUser admin = new SysUser();
        admin.setUsername("admin");
        admin.setPasswordHash(new BCryptPasswordEncoder().encode(adminInitialPassword));
        admin.setRole("admin");
        admin.setStatus("active");
        userMapper.insert(admin);
        // 只提示账号已创建，绝不在日志中打印密码
        log.info("已创建初始管理员账号 admin，请使用配置的初始密码登录并尽快修改");
    }
}
