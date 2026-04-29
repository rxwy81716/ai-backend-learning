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
# 注意：IK 版本号要和 ES 完全一致（当前 ES 9.0.4 → analysis-ik-9.0.4）
docker exec es9 bin/elasticsearch-plugin install --batch `
  https://release.infinilabs.com/analysis-ik/stable/elasticsearch-analysis-ik-9.0.4.zip

docker restart es9

# 验证
curl.exe "http://localhost:9200/_cat/plugins?v"
# 期望看到：es9  analysis-ik  9.0.4
```

---

## 一键执行变量

```powershell
$ES    = "http://localhost:9200"
$INDEX = "knowledge_vector_store"
$DIMS  = 1024   # bge-m3=1024（与 application.yml 中 dimensions 保持一致）
```

---

## 路径 A：全新安装（推荐，无老数据时）

> 适用场景：你刚重建 ES 容器、或不在乎已有数据，重传文档即可。
>
> 步骤：**先让 Spring Boot 启动一次** 触发 `initializeSchema(true)` 建出默认索引 → 停掉应用 → 删索引 → 用 IK mapping 重建 → 启动应用 → 重传文档。

```powershell
# 1. 删掉 Spring Boot 自动建的默认索引（standard analyzer）
Invoke-RestMethod -Method Delete -Uri "$ES/$INDEX"

# 2. 用 IK mapping 重建（参考下文 Step 1 的 body，但 PUT 到 $INDEX 而不是 $NEW）
#    body 内容同 Step 1，仅替换 URL：
#    Invoke-RestMethod -Method Put -Uri "$ES/$INDEX" -ContentType ... -Body ...

# 3. 重启 Spring Boot，重新上传文档
```

> 注意：Spring AI `ElasticsearchVectorStore.initializeSchema(true)` 见到索引存在会跳过，**不会**覆盖你的 mapping。

---

## 路径 B：在线迁移（保留老数据）

> 适用场景：已有大量文档不愿重传。下面是 OLD → NEW → 别名切换的标准做法。

```powershell
$OLD = $INDEX                         # knowledge_vector_store
$NEW = "knowledge_vector_store_v2"
```

### Step 1. 创建新索引（带 ik 分词）

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

> 路径 A 时把 `$NEW` 改成 `$INDEX` 即可。

---

### Step 2. 数据迁移（reindex）

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

### Step 3. 切换索引别名（零停机）

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
