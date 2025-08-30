# Orangejam

This is our submission for the 5th problem statement "5. Visualising Architecture with Knit". We built it as an Intellij plugin, so that it can integrate with existing projects that use Knit.

## How to use

- Clone the repository, open in IDEA, run it with the Jetbrains Runtime (jbr), to open a sandboxed editor with the plugin installed. Then open a project that uses knit.
  - Use the side bar to select the knit module, and click generate to see the graph
  - Right click the gradle module to open the context menu to visualize the module graph
  - For a given provider/consumer, gutter functions will appear in line, click on them to see either the relevant dependency subgraph, or the providers for a consumer

## Relevant problem statement

The goal of this project is to make projects that use Knit have more visibility into the dependency relationships in the project. We do this entirely within the editor for seamless integration with the developer workflow

## Technical write up

Given an open project, we look through to find modules that uses the `tiktok.knit` dependency. For a project that uses the dependency, we then analyze it to produce a dependency graph.

We read compiled class files with ASM and use Knit internal `InjectionBinder` mechanism to produce a dependency graph. The dependency graph can then be easily traverse using `Graph` class and allow for further analysis.  We then compiled the dependency graph into `.dot` to render as `.png` files. These would then be displayed within the editor.

Given the dependency graph, we can then compute useful features like jumping to the provider of a given `by di` consumer. We can also slice the graph into a subgraph to show the developer a visualization of the relevant dependencies related to the class that is being worked on.

## Features and functionalities

- Visualize a module's dependency graph as a context menu / side bar toggle within the editor
- Visualize a subgraph of only the relevant components related to the Provider/Consumer as an inline editor gutter
- Jump to provider as an inline editor gutter

## Libraries used

- Graphviz
- Intellij Plugin SDK
