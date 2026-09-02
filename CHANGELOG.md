# Changelog

## 0.1.1-rc.3

- **Syntax highlighting**: `class`, `mixin` and `interface` are highlighted as
  keywords only in declaration position (`class [Name] {`, `mixin [Name] {`,
  `interface {`/`[`/`Name {`). They are contextual keywords — plain identifiers
  everywhere else — so using them as parameter names, selectors or dict keys
  (`fn(class)`, `x.mixin`, `{interface: 1}`) no longer mis-highlights them.
  (Shared `gad-textmate` grammar.)

## 0.1.1-rc.2

Keeps the plugin in step with recent Gad language features.

- **Structure view**: marker types declared with `type Name { … }` are now
  recognized and shown with their own icon, listing their fields, `props`,
  methods and `call(…)` factory (via the updated `gad symbols`).
- **Syntax highlighting**: the shared grammar now highlights the `StaticType`
  builtin alongside `Class` / `Mixin`.
- Highlighting, completion and the Gad Doc panel work with the new language
  syntax added in this cycle:
  - `type<X>` / `type<X|Y>` **meta-type** parameters (dispatch on a type value).
  - marker `type Name { … }` types and the `const Name = type { … }` form.
  - `gad.transform(…)` mapped transforms and the `t.raises(…)` test assertion.
  - `match` without a subject (`match { … }`), mixable `: value` / `{ block }`
    arms, and `;`/newline arm separators.

## 0.1.1-rc.1

- Structure view (`gad symbols`), Gad Doc auto-refresh on file switch.
