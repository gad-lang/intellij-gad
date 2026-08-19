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
/* Restore full markdown typography inside v-html content — Vuetify's global CSS
   reset strips margins, list markers and other element defaults. */
.markdown { max-width: 60rem; line-height: 1.6; overflow-wrap: break-word; }
.markdown > :first-child { margin-top: 0; }
.markdown > :last-child { margin-bottom: 0; }
.markdown p { margin: .75em 0; }
.markdown h1, .markdown h2, .markdown h3, .markdown h4, .markdown h5, .markdown h6 { margin: 1.4em 0 .5em; line-height: 1.25; font-weight: 600; }
.markdown h1 { font-size: 2em; }
.markdown h2 { font-size: 1.5em; }
.markdown h3 { font-size: 1.25em; }
.markdown h4 { font-size: 1.05em; }
.markdown h1, .markdown h2 { border-bottom: 1px solid rgba(127,127,127,.3); padding-bottom: .2em; }
.markdown ul, .markdown ol { padding-left: 1.5em; margin: .5em 0; list-style: revert; }
.markdown li { margin: .25em 0; }
.markdown li > ul, .markdown li > ol { margin: .25em 0; }
.markdown blockquote { margin: .75em 0; padding: .2em 1em; border-left: 3px solid rgba(127,127,127,.4); opacity: .85; }
.markdown a { color: rgb(var(--v-theme-primary)); text-decoration: none; }
.markdown a:hover { text-decoration: underline; }
.markdown hr { border: 0; border-top: 1px solid rgba(127,127,127,.3); margin: 1.5em 0; }
.markdown img { max-width: 100%; }
.markdown table { border-collapse: collapse; margin: .75em 0; display: block; overflow-x: auto; }
.markdown th, .markdown td { border: 1px solid rgba(127,127,127,.3); padding: .4em .6em; text-align: left; }
.markdown th { background: rgba(127,127,127,.1); }
.markdown pre { background: rgba(127,127,127,.12); padding: 10px 12px; border-radius: 6px; overflow: auto; margin: .75em 0; }
.markdown code { font-family: monospace; }
.markdown :not(pre) > code { background: rgba(127,127,127,.15); padding: .1em .35em; border-radius: 4px; font-size: .9em; }
</style>
