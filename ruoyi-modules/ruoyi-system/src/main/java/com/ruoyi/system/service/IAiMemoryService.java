package com.ruoyi.system.service;

/**
 * 用户长期记忆与画像服务
 *
 * @author ruoyi
 */
public interface IAiMemoryService
{
    /**
     * 异步抽取本轮对话中的长期记忆并保存（含向量化、画像更新）。
     * 由 handleUserQuestion 在对话结束后触发，不阻塞用户响应。
     */
    void extractAndSaveAsync(Long userId, String question, String answer);

    /**
     * 构造注入 Prompt 的用户上下文：画像摘要 + 相关记忆（最近 + 语义召回）。
     *
     * @return 可读文本，无内容时返回空串
     */
    String buildUserContext(Long userId, String question);

    /** 提问计数 + 最近活跃时间（轻量更新，同步执行） */
    void touchProfile(Long userId);

    /**
     * 读取画像摘要（profile_summary），用于推荐时回退预算/偏好等字段。
     * 无画像时返回 null。
     */
    String getProfileSummary(Long userId);

    /**
     * 画像快照（调试/联调用）：画像表 + 记忆列表 + **实际会注入 Prompt 的文本**。
     *
     * 画像只存在于数据库和 Prompt 里，接口响应中看不到，所以单独提供一个可观测入口，
     * 否则"画像有没有生效"只能靠翻日志。
     *
     * @param userId 用户ID
     * @return profile / memories / injectContext
     */
    java.util.Map<String, Object> getProfileSnapshot(Long userId);
}
