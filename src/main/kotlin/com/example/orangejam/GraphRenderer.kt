package com.example.orangejam

import com.intellij.openapi.diagnostic.Logger
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import guru.nidi.graphviz.parse.Parser
import guru.nidi.graphviz.engine.Graphviz
import guru.nidi.graphviz.engine.Format


object GraphRenderer {
    private val log = Logger.getInstance(GraphRenderer::class.java)

    // dot -> svg
    fun renderDotToSvg(dot: Path, svg: Path): Boolean {
        return try {
            val g = Parser().read(Files.readString(dot))
            val svgText = Graphviz.fromGraph(g).render(Format.SVG).toString()
            Files.createDirectories(svg.parent)
            Files.writeString(svg, svgText)
            true
        } catch (t: Throwable) {
            log.warn("graphviz-java SVG render failed", t)
            false
        }
    }

    // dot -> png
    fun renderDotToPng(dot: Path, png: Path): Boolean {
        return try {
            val g = Parser().read(Files.readString(dot))
            val img = Graphviz.fromGraph(g).render(Format.PNG).toImage()
            Files.createDirectories(png.parent)
            ImageIO.write(img, "PNG", png.toFile())
            true
        } catch (t: Throwable) {
            log.warn("graphviz-java PNG render failed", t)
            false
        }
    }
}
