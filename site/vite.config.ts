import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import vuetify from "vite-plugin-vuetify";
import { execSync } from "node:child_process";
import { readFileSync } from "node:fs";

// Build-time plugin info: version from gradle.properties, commit id + time from git.
function git(cmd: string): string {
  try { return execSync(cmd).toString().trim(); } catch { return ""; }
}
function pluginVersion(): string {
  try {
    const m = readFileSync("../gradle.properties", "utf8").match(/^pluginVersion\s*=\s*(.+)$/m);
    return m ? m[1].trim() : "dev";
  } catch { return "dev"; }
}

export default defineConfig({
  base: "/intellij-gad/",
  plugins: [vue(), vuetify({ autoImport: true })],
  define: {
    __VERSION__: JSON.stringify(pluginVersion()),
    __COMMIT__: JSON.stringify(git("git rev-parse --short HEAD")),
    __COMMIT_TIME__: JSON.stringify(git("git show -s --format=%cI HEAD")),
  },
});
