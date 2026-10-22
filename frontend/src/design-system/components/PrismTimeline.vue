<script setup>
defineProps({ items: { type: Array, default: () => [] }, label: { type: String, default: 'Trình tự sự kiện' } })
</script>
<template>
  <ol class="p-timeline" :aria-label="label">
    <li v-for="(item, i) in items" :key="i" class="p-timeline-item" :class="`p-timeline--${item.tone ?? (i === 0 ? 'accent' : 'neutral')}`">
      <span class="p-timeline-dot" aria-hidden="true"></span>
      <div class="p-timeline-body">
        <p class="p-timeline-title">{{ item.title }}</p>
        <p v-if="item.description" class="p-timeline-desc">{{ item.description }}</p>
        <time v-if="item.time" class="p-timeline-time">{{ item.time }}</time>
      </div>
    </li>
  </ol>
</template>
