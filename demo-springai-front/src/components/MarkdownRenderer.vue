<script setup lang="ts">
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'

const props = defineProps<{
  content: string
}>()

// 单例 MarkdownIt 实例（避免每次渲染都重建）
const md = new MarkdownIt({
  html: false,        // 安全：不渲染原始 HTML
  linkify: true,      // 自动把 URL 变成链接
  breaks: true,       // 单换行 -> <br>
  highlight(code, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return `<pre><code class="hljs language-${lang}">${
          hljs.highlight(code, { language: lang, ignoreIllegals: true }).value
        }</code></pre>`
      } catch {}
    }
    return `<pre><code class="hljs">${md.utils.escapeHtml(code)}</code></pre>`
  }
})

const html = computed(() => md.render(props.content || ''))
</script>

<template>
  <div class="markdown-body" v-html="html"></div>
</template>
