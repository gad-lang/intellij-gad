<script setup lang="ts">
import { ref } from "vue";
import { marked } from "marked";
import readme from "../../README.md?raw";
import plan from "../../PLAN.md?raw";
const logo = import.meta.env.BASE_URL + "gad.svg";
const tab = ref("overview");
const readmeHtml = marked.parse(readme) as string;
const planHtml = marked.parse(plan) as string;
</script>
<template>
  <v-app>
    <v-app-bar color="surface" flat>
      <v-app-bar-title>
        <img :src="logo" height="24" style="vertical-align:-5px;margin-right:8px" />
        intellij-gad
      </v-app-bar-title>
      <v-spacer />
      <v-btn href="https://github.com/gad-lang/intellij-gad" icon="mdi-github" variant="text" />
    </v-app-bar>
    <v-main>
      <v-tabs v-model="tab" bg-color="surface">
        <v-tab value="overview">Overview</v-tab>
        <v-tab value="plan">Design</v-tab>
      </v-tabs>
      <v-container>
        <div v-show="tab === 'overview'" class="markdown" v-html="readmeHtml" />
        <div v-show="tab === 'plan'" class="markdown" v-html="planHtml" />
      </v-container>
    </v-main>
  </v-app>
</template>
<style>
.markdown { max-width: 60rem; line-height: 1.6; }
.markdown h1, .markdown h2 { border-bottom: 1px solid rgba(127,127,127,.3); padding-bottom: .2em; }
.markdown pre { background: rgba(127,127,127,.12); padding: 10px 12px; border-radius: 6px; overflow: auto; }
.markdown code { font-family: monospace; }
</style>
