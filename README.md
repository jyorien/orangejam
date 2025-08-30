# Orange Jam

**Orange Jam** is our submission for Problem Statement 5: *Visualising Architecture with Knit*. We built it as an IntelliJ plugin to seamlessly integrate with projects that use TikTok’s [Knit](https://github.com/tiktok/knit), offering developers instant insight into their dependency structure — all without leaving the IDE.

---

## 🔧 How to Use

1. **Clone this repository** and open it in IntelliJ IDEA.
2. **Run the plugin** using the JetBrains Runtime (jbr) to launch a sandboxed IntelliJ instance with the plugin pre-installed.
3. **Open a Kotlin project that uses Knit** and:
   - Use the **sidebar** to select a Knit module and click *Generate* to view the dependency graph.
   - **Right-click** a Gradle module to access a context menu and generate the module-level graph.
   - Use the **editor gutter icons** to:
     - Jump directly to a *provider* from a consumer.
     - Visualize a *focused subgraph* showing only relevant dependencies.

---

## 🎯 Problem Statement

Knit is a compile-time dependency injection framework, but understanding how modules and classes are wired together can be difficult from code alone.

Orange Jam solves this by:
- Visualizing the full DI graph inside the editor.
- Highlighting how classes depend on each other.
- Allowing developers to navigate directly from the graph to the relevant code.

---

## 🛠️ Technical Overview

When a Knit project is opened, Orange Jam:

1. **Scans the project** for modules that include the `tiktok.knit` dependency.
2. **Reads compiled `.class` files** using [ASM](https://asm.ow2.io/) and extracts dependency information via Knit’s `InjectionBinder`.
3. **Constructs a dependency graph** and exports it to `.dot` format.
4. **Renders the graph** as an SVG or PNG using Graphviz, displayed inside IntelliJ using JCEF.

With this graph, we enable:
- Clickable nodes to jump directly to classes or methods.
- Focused subgraphs for specific classes.
- Seamless integration into the IDE via gutters and context menus.

---

## ✨ Features

- Module graph generation from the sidebar or context menu.
- Click on nodes in tool window graph to navigate to the corresponding method
- Focused subgraphs for a given provider/consumer class.
- Gutter icons for jumping to providers or visualizing relevant dependencies.
- No source code modifications required — all analysis is done via compiled bytecode.

---

## 📚 Libraries & Tools Used

- [Graphviz](https://graphviz.org/) - graph rendering
- [ASM](https://asm.ow2.io/) - bytecode analysis
- [IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/welcome.html) - plugin development

---

## 🧪 Limitations

- 🐢 The plugin requires the project to be built before it can generate graphs. For large codebases, slow builds may hinder seamless usage.
- 🔀 Cross-class method navigation is not yet supported. Clicking on a method in the graph currently works only within the same class.

---
