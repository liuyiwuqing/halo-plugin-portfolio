<script setup lang="ts">
import { Toast, VButton, VModal } from '@halo-dev/components'
import { ref } from 'vue'
import { portfolioApi, type Project } from '@/api/portfolio'
import { useRepositoryImport } from '@/composables/useRepositoryImport'

const emit = defineEmits<{ close: []; imported: [project: Project] }>()
const previewForm = ref<{ submit: () => void }>()
const { repoUrl, snapshot, previewing, importing, error, canImport, preview, importProject } =
  useRepositoryImport(portfolioApi, (project) => {
    Toast.success('仓库已导入为草稿，可编辑后发布')
    emit('imported', project)
    emit('close')
  })

function preventCloseWhileImporting(event: KeyboardEvent) {
  if (importing.value && event.key === 'Escape') event.stopImmediatePropagation()
}
</script>

<template>
  <VModal
    title="从公开仓库导入"
    :width="640"
    :mount-to-body="true"
    :layer-closable="!importing"
    @keyup.capture="preventCloseWhileImporting"
    @update:visible="
      (visible) => {
        if (!visible && !importing) emit('close')
      }
    "
  >
    <template #header>
      <div class="repository-modal-header">
        <strong>从公开仓库导入</strong>
        <VButton type="default" size="sm" :disabled="importing" @click="emit('close')"
          >关闭</VButton
        >
      </div>
    </template>
    <!-- VModal 会移动内容到滚动视口；稳定根节点让动态区域始终在同一父节点内更新。 -->
    <div class="repository-modal-body">
      <p class="repository-intro">粘贴 GitHub 或 Gitee 公开仓库地址，预览后导入为草稿。</p>
      <FormKit
        type="form"
        :actions="false"
        :disabled="importing || previewing"
        @node="previewForm = $event"
        @submit="preview"
      >
        <FormKit
          v-model="repoUrl"
          name="repoUrl"
          type="url"
          label="仓库地址"
          validation="required|url"
          placeholder="https://github.com/owner/repository"
          :classes="{ inner: '!max-w-none', wrapper: '!gap-2', outer: '!py-0' }"
        />
      </FormKit>

      <p v-if="error" class="repository-error" role="alert">{{ error }}</p>
      <section v-if="snapshot" class="repository-preview" aria-label="仓库预览">
        <div class="repository-preview-header">
          <strong>{{ snapshot.owner }}/{{ snapshot.name }}</strong>
          <span class="repository-provider">{{ snapshot.provider }}</span>
        </div>
        <p>{{ snapshot.description || '该仓库暂无描述' }}</p>
        <dl>
          <div>
            <dt>语言</dt>
            <dd>{{ snapshot.language || '未提供' }}</dd>
          </div>
          <div>
            <dt>Stars</dt>
            <dd>{{ snapshot.stars ?? 0 }}</dd>
          </div>
          <div>
            <dt>Forks</dt>
            <dd>{{ snapshot.forks ?? 0 }}</dd>
          </div>
          <div>
            <dt>许可证</dt>
            <dd>{{ snapshot.license || '未提供' }}</dd>
          </div>
          <div class="repository-slug">
            <dt>项目标识</dt>
            <dd>{{ snapshot.suggestedSlug }}</dd>
          </div>
        </dl>
        <p v-if="snapshot.archived">该仓库已归档。</p>
        <p class="repository-hint">
          后续手动同步更新仓库统计与元数据，保留你编辑的标题、介绍、分类和发布状态。
        </p>
      </section>
    </div>
    <template #footer>
      <div class="repository-actions">
        <VButton type="default" :disabled="importing" @click="emit('close')">取消</VButton>
        <VButton
          type="secondary"
          :disabled="importing || previewing"
          :loading="previewing"
          @click="previewForm?.submit()"
          >{{ previewing ? '正在预览…' : snapshot ? '重新预览' : '预览仓库' }}</VButton
        >
        <VButton
          v-permission="['plugin:portfolio:project:manage']"
          type="primary"
          :disabled="!canImport || importing || previewing"
          :loading="importing"
          @click="importProject"
          >导入为草稿</VButton
        >
      </div>
    </template>
  </VModal>
</template>

<style scoped>
.repository-modal-body {
  display: grid;
  gap: 1.25rem;
  min-width: 0;
}
.repository-intro,
.repository-hint {
  color: #667085;
  font-size: 0.875rem;
  line-height: 1.6;
  margin: 0;
}
.repository-error {
  color: #b42318;
  background: #fef3f2;
  border: 1px solid #fecdca;
  border-radius: 6px;
  padding: 0.75rem;
  font-size: 0.875rem;
  line-height: 1.5;
  margin: 0;
}
.repository-preview {
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  background: #f9fafb;
  padding: 1rem;
}
.repository-preview-header {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 0.5rem;
}
.repository-preview-header strong {
  overflow-wrap: anywhere;
  min-width: 0;
}
.repository-provider {
  color: #475467;
  background: #fff;
  border: 1px solid #e4e7ec;
  border-radius: 4px;
  padding: 0.125rem 0.5rem;
  font-size: 0.75rem;
}
.repository-preview p {
  margin-top: 0.75rem;
  font-size: 0.875rem;
  line-height: 1.6;
  overflow-wrap: anywhere;
}
.repository-preview dl {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1rem;
  margin-top: 1rem;
}
.repository-slug {
  grid-column: span 2;
}
.repository-preview dt {
  color: #667085;
  font-size: 0.75rem;
}
.repository-preview dd {
  margin: 0.25rem 0 0;
  overflow-wrap: anywhere;
}
.repository-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 0.5rem;
}
.repository-modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}
@media (max-width: 480px) {
  .repository-preview dl {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .repository-actions {
    gap: 0.375rem;
  }
}
</style>
