<script setup lang="ts">
import { useTheme } from "vuetify";
import { ref } from "vue";
import { marked } from "marked";
import readme from "../../README.md?raw";
import plan from "../../PLAN.md?raw";
const theme = useTheme();
const toggleTheme = () => { theme.global.name.value = theme.global.current.value.dark ? "light" : "dark"; };
const logo = import.meta.env.BASE_URL + "gad.svg";
const tab = ref("overview");
const readmeHtml = marked.parse(readme) as string;
const planHtml = marked.parse(plan) as string;
// Build info injected at build time (see vite.config.ts).
const version = __VERSION__;
const commit = __COMMIT__;
const commitTime = __COMMIT_TIME__ ? new Date(__COMMIT_TIME__).toLocaleString() : "";
const commitUrl = `https://github.com/gad-lang/intellij-gad/commit/${commit}`;
const releasesUrl = "https://github.com/gad-lang/intellij-gad/releases";
// The release asset is versioned (intellij-gad-<version>.zip); build the direct
// download link for this build's version.
const downloadUrl = `${releasesUrl}/download/v${version}/intellij-gad-${version}.zip`;
</script>
<template>
  <v-app>
    <v-app-bar color="surface" flat>
      <v-app-bar-title>
        <img :src="logo" height="24" style="vertical-align:-5px;margin-right:8px" />
        intellij-gad
      </v-app-bar-title>
      <v-spacer />
      <v-btn :href="downloadUrl" prepend-icon="mdi-download" variant="tonal" color="primary" class="mr-2" title="Download the plugin .zip for this release">Download .zip</v-btn>
      <v-btn :href="releasesUrl" icon="mdi-tag-multiple" variant="text" title="All releases" />
      <v-btn :icon="theme.global.current.value.dark ? 'mdi-weather-sunny' : 'mdi-weather-night'" @click="toggleTheme" variant="text" title="Toggle theme" />
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
    <v-footer color="surface" class="text-caption d-flex align-center flex-wrap ga-2">
      <span>Gad Language <strong>v{{ version }}</strong></span>
      <span v-if="commit">· commit <a :href="commitUrl" target="_blank" rel="noopener">{{ commit }}</a></span>
      <span v-if="commitTime">· {{ commitTime }}</span>
      <v-spacer />
      <a :href="downloadUrl">Download .zip</a>
      <span>·</span>
      <a :href="releasesUrl">Releases</a>
      <span>·</span>
      <a href="https://github.com/gad-lang/intellij-gad">gad-lang/intellij-gad</a>
    </v-footer>
  </v-app>
</template>
<style>
.markdown { max-width: 60rem; line-height: 1.6; }
.markdown h1, .markdown h2 { border-bottom: 1px solid rgba(127,127,127,.3); padding-bottom: .2em; }
.markdown pre { background: rgba(127,127,127,.12); padding: 10px 12px; border-radius: 6px; overflow: auto; }
.markdown code { font-family: monospace; }
</style>
