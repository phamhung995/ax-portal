<script setup>
defineProps({ id: { type: String, required: true }, label: String, hint: String, error: String, required: Boolean, disabled: Boolean })
</script>
<template>
  <div class="p-field" :class="{ 'is-disabled': disabled }">
    <label :for="id"><slot name="label">{{ label }}</slot><span v-if="required" class="p-required"> *</span></label>
    <slot :describedby="error ? `${id}-error` : hint ? `${id}-hint` : undefined" :invalid="!!error" />
    <p v-if="error" :id="`${id}-error`" class="p-field-error">{{ error }}</p>
    <p v-else-if="hint" :id="`${id}-hint`" class="p-field-hint">{{ hint }}</p>
  </div>
</template>
