#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
向量库拆分迁移：把旧 aa_rag_index(aa_rag_prefix*) 的文档按 ID 前缀迁到三个独立索引。

背景：拆分前 kb-/book-/memory- 三类文档共用一个 Redis 索引，
拆分后分别进入 aa_knowledge_index / aa_book_index / aa_memory_index。

原理：直接复制 RedisJSON 文档（含 embedding），**不重新调用大模型**，秒级完成。
文档 body 完全不动，只换 key 前缀；索引由应用启动时的 RedisVectorConfig 自动创建
（initializeSchema=true 时会扫描已存在的前缀文档）。

用法：
  pip install redis
  python vector_split_migrate.py            # 执行迁移
  python vector_split_migrate.py --dry-run  # 只统计不写入

幂等：目标 key 已存在会覆盖；源 key 不删除（可回滚）。
"""
import sys
import redis

OLD_PREFIX = "aa_rag_prefix"
ROUTES = [
    ("kb-",     "aa_knowledge_prefix"),
    ("book-",   "aa_book_prefix"),
    ("memory-", "aa_memory_prefix"),
]
REDIS_HOST = "127.0.0.1"
REDIS_PORT = 6379
REDIS_DB = 0
REDIS_PASSWORD = None  # 有密码填这里，或改从环境变量读


def main():
    dry = "--dry-run" in sys.argv
    r = redis.Redis(host=REDIS_HOST, port=REDIS_PORT, db=REDIS_DB,
                    password=REDIS_PASSWORD, decode_responses=True)
    r.ping()
    keys = r.keys(OLD_PREFIX + "*")
    print(f"旧索引文档总数: {len(keys)}")

    stats = {p: 0 for _, p in ROUTES}
    unknown = 0
    for key in keys:
        tail = key[len(OLD_PREFIX):]
        target_prefix = None
        for idp, pfx in ROUTES:
            if tail.startswith(idp):
                target_prefix = pfx
                break
        if target_prefix is None:
            unknown += 1
            print(f"  跳过未知前缀: {key}")
            continue
        if dry:
            stats[target_prefix] += 1
            continue
        raw = r.get(key)
        if raw is None:
            print(f"  跳过无内容 key: {key}")
            continue
        # RedisJSON 用 JSON.GET；若返回的是普通字符串则退化处理
        doc = r.execute_command("JSON.GET", key)
        if doc:
            r.execute_command("JSON.SET", target_prefix + tail, "$", doc)
            stats[target_prefix] += 1
        else:
            print(f"  跳过非 JSON key: {key}")

    for pfx in stats:
        print(f"  {pfx}: {stats[pfx]} 条")
    print("DRY-RUN 完成，未写入" if dry else "迁移完成")
    print("下一步：重启 ruoyi-system，RedisVectorConfig 会自动建三个新索引并扫描这些文档。")
    print("确认业务正常后，再删除旧索引：FT.DROPINDEX aa_rag_index + 删除 aa_rag_prefix* keys。")


if __name__ == "__main__":
    main()
