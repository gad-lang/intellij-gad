import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import vuetify from "vite-plugin-vuetify";

// Project Pages site: served at https://gad-lang.github.io/intellij-gad/
export default defineConfig({
  base: "/intellij-gad/",
  plugins: [vue(), vuetify({ autoImport: true })],
});
