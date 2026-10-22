<script setup>
defineProps({ variant: { type: String, default: 'primary' }, type: { type: String, default: 'button' }, loading: Boolean, disabled: Boolean })
</script>
<template>
  <button :type="type" class="p-button" :class="`p-button--${variant}`" :disabled="disabled || loading" :aria-busy="loading || undefined">
    <span v-if="loading" class="p-spinner" aria-hidden="true"></span><slot />
  </button>
</template>
