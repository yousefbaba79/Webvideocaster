package com.webvideocaster.app.dlna

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/** Small namespace-agnostic helpers over the platform DOM parser. */
internal object Xml {
    private val hardeningFeatures = listOf(
        "http://apache.org/xml/features/disallow-doctype-decl" to true,
        "http://xml.org/sax/features/external-general-entities" to false,
        "http://xml.org/sax/features/external-parameter-entities" to false,
    )

    fun parse(xml: String): Document? = try {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        for ((feature, value) in hardeningFeatures) {
            // Not every platform parser supports every feature; Android's ignores external entities anyway.
            runCatching { factory.setFeature(feature, value) }
        }
        factory.newDocumentBuilder().parse(InputSource(StringReader(xml.trim().removePrefix("﻿"))))
    } catch (e: Exception) {
        null
    }

    fun firstText(document: Document, localName: String): String? =
        document.getElementsByTagNameNS("*", localName).item(0)?.textContent?.trim()

    fun Element.localNameOrTag(): String = localName ?: tagName.substringAfter(':')

    fun Element.childElements(): List<Element> {
        val nodes = childNodes
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    fun Element.childText(localName: String): String? =
        childElements().firstOrNull { it.localNameOrTag() == localName }?.textContent?.trim()
}
