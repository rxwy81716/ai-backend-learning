# ES 索引中文分词迁移（ik_max_word） — PowerShell 版

> 当前 Spring AI ElasticsearchVectorStore 默认使用 `standard` 分词器，
> 中文场景下 BM25 检索质量受限（按字切分）。
> 推荐用 `ik_max_word` 重建索引，提升混合检索的中文召回效果。

> ⚠️ **PowerShell 注意事项**
> - PowerShell 内置的 `curl` 是 `Invoke-WebRequest` 的别名，参数和真 curl 不兼容，本文统一用 `Invoke-RestMethod`。
> - JSON 请求体用 here-string `@" ... "@` 写多行，避免转义地狱。
> - 如果你坚持用真 curl，请改成 `curl.exe`（带 .exe 后缀）调用 Windows 自带的 curl。

---

## 前置：安装 IK 插件

```powershell
# 进入 ES 安装目录的 bin（或 ES 容器内）
# 注意：版本号要和 ES 完全一致，例如 ES 8.13.0 → analysis-ik-8.13.0
.\elasticsearch-plugin.bat install `
  https://release.infinilabs.com/analysis-ik/stable/elasticsearch-analysis-ik-8.13.0.zip

# 重启 ES 服务
Restart-Service elasticsearch-service-x64   # 若用 Windows 服务安装
# 或手动停止 → 启动 elasticsearch.bat
```

---

## 一键执行变量

```powershell
$ES   = "http://localhost:9200"
$OLD  = "knowledge_vector_store"
$NEW  = "knowledge_vector_store_v2"
$DIMS = 1024   # bge-m3=1024, MiniMax=1536（与 application.yml 中 dimensions 保持一致）
```

---

## Step 1. 创建新索引（带 ik 分词）

```powershell
$body = @"
{
  "settings": {
    "analysis": {
      "analyzer": {
        "ik_smart_combined": {
          "type": "custom",
          "tokenizer": "ik_max_word",
          "filter": ["lowercase"]
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "content": {
        "type": "text",
        "analyzer": "ik_max_word",
        "search_analyzer": "ik_smart"
      },
      "embedding": {
        "type": "dense_vector",
        "dims": $DIMS,
        "index": true,
        "similarity": "cosine"
      },
      "metadata": {
        "type": "object",
        "properties": {
          "source":      { "type": "keyword" },
          "user_id":     { "type": "keyword" },
          "doc_scope":   { "type": "keyword" },
          "chunk_index": { "type": "keyword" },
          "total_chunks":{ "type": "keyword" }
        }
      }
    }
  }
}
"@

Invoke-RestMethod -Method Put -Uri "$ES/$NEW" `
  -ContentType "application/json; charset=utf-8" `
  -Body ([System.Text.Encoding]::UTF8.GetBytes($body))
```

---

## Step 2. 数据迁移（reindex）

```powershell
$body = @"
{
  "source": { "index": "$OLD" },
  "dest":   { "index": "$NEW" }
}
"@

Invoke-RestMethod -Method Post -Uri "$ES/_reindex?wait_for_completion=true" `
  -ContentType "application/json; charset=utf-8" `
  -Body ([System.Text.Encoding]::UTF8.GetBytes($body))
```

> 数据量大时建议改为 `wait_for_completion=false`，返回 `task_id`，再用 `GET /_tasks/{task_id}` 轮询进度。

---

## Step 3. 切换索引别名（零停机）

```powershell
# 删除旧索引（必须先删，否则别名和原索引同名会冲突）
Invoke-RestMethod -Method Delete -Uri "$ES/$OLD"

# 创建别名指向新索引（应用代码无需改动）
$body = @"
{
  "actions": [
    { "add": { "index": "$NEW", "alias": "$OLD" } }
  ]
}
"@

Invoke-RestMethod -Method Post -Uri "$ES/_aliases" `
  -ContentType "application/json; charset=utf-8" `
  -Body ([System.Text.Encoding]::UTF8.GetBytes($body))
```

---

## 验证分词效果

```powershell
$body = @"
{
  "analyzer": "ik_max_word",
  "text": "向量数据库中的混合检索"
}
"@

Invoke-RestMethod -Method Post -Uri "$ES/$OLD/_analyze" `
  -ContentType "application/json; charset=utf-8" `
  -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) `
  | ConvertTo-Json -Depth 5
```

**预期输出**会切出：`向量 / 数据库 / 数据 / 库 / 中 / 的 / 混合 / 检索` 等合理 token。
如果切出来还是 `向 / 量 / 数 / 据 / 库 ...` 单字，说明 IK 插件没装好或没重启 ES。

---

## 排错速查

| 现象                                          | 原因                                                               |
|---------------------------------------------|------------------------------------------------------------------|
| `unknown analyzer [ik_max_word]`            | IK 插件未安装 / ES 没重启                                                |
| `index_already_exists_exception`            | 新索引名已存在，换 v3 / v4                                                |
| `resource_already_exists_exception` 在 alias | 旧索引没删干净                                                          |
| 中文乱码                                        | `Body` 没用 UTF-8 字节数组（保留 `[System.Text.Encoding]::UTF8.GetBytes`） |
| reindex 卡很久                                 | 改 `wait_for_completion=false` 异步执行                               |
