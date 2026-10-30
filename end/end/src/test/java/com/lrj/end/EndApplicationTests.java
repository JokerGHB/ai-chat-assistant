package com.lrj.end;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 * 测试总入口 —— 套件启动器（测试角色：周鹏）。
 *
 * <p><b>用法：</b>在 IDEA 里右键运行本类，就会按下面的顺序把全部测试类依次跑完；
 * 命令行等价物是 {@code mvn test}。想单独调试某个模块时，再右键对应的子类即可 ——
 * 分类是为了好找，套件是为了好跑，两者不冲突。
 *
 * <p><b>执行顺序是有讲究的（讲解用）：</b>
 * <ol>
 *   <li>{@link AiClientTests}：纯单元测试，不连数据库 —— 最先跑，失败说明客户端降级逻辑有问题；</li>
 *   <li>{@link StartupSmokeTests}：启动冒烟 —— 容器起不来，后面的接口测试没有意义；</li>
 *   <li>{@link AuthApiTests}：认证与账号安全 —— 令牌是所有接口的前置条件，先确保身份这一层是对的；</li>
 *   <li>{@link SessionMessageApiTests}：会话与消息 —— 依赖认证可用，覆盖业务规则、并发与降级；</li>
 *   <li>{@link AdminApiTests}：管理端 —— 依赖普通用户流程正常（需要注册出被封禁的测试账号）。</li>
 * </ol>
 * 这个顺序就是「从地基往上盖」：环境 → 身份 → 业务 → 管理，失败时最先挂的那一层就是问题所在。
 *
 * <p>注意：本类自己是启动器，不含测试方法；每条用例的职责和注释在各自的类里。
 */
@Suite
@SelectClasses({
        AiClientTests.class,
        StartupSmokeTests.class,
        AuthApiTests.class,
        SessionMessageApiTests.class,
        AdminApiTests.class
})
class EndApplicationTests {
}
