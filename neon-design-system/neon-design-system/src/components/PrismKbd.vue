<script setup>
defineProps({ code: String })
</script>
<template>
  <kbd class="p-kbd" :aria-label="code">{{ code }}</kbd>
</template>
