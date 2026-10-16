package data

import kotlin.test.Test
import kotlin.test.assertEquals

class PageImageExtractorTest {

    @Test
    fun imagesAreRankedAndResolved() {
        val html = """
            <html><head><title>Titre document</title>
            <meta name="twitter:image" content="/img/tw.jpg">
            <meta content="https://cdn.example.com/og.jpg" property="og:image">
            <link rel="image_src" href="//static.example.com/src.png">
            </head><body>
            <header><img src="/header-banner.jpg" width="800" height="200"></header>
            <article>
              <img src="/logo.png" width="300" height="300">
              <img src="/tiny.jpg" width="16" height="16">
              <img class="site-logo" src="/brand.jpg">
              <img src="data:image/gif;base64,R0lGOD" data-src="/lazy.jpg">
              <img srcset="/s-400.jpg 400w, /s-1200.jpg 1200w, /s-3000.jpg 3000w" src="/s-400.jpg">
            </article>
            <noscript><img src="/noscript.jpg"></noscript>
            </body></html>
        """.trimIndent()
        val r = PageImageExtractor.extract(html, "https://example.com/blog/post")
        assertEquals(
            listOf(
                "https://cdn.example.com/og.jpg",
                "https://example.com/img/tw.jpg",
                "https://static.example.com/src.png",
                "https://example.com/lazy.jpg",
                "https://example.com/s-1200.jpg",
                "https://example.com/noscript.jpg",
                "https://example.com/header-banner.jpg",
            ),
            r.images,
        )
        assertEquals("Titre document", r.title)
    }

    @Test
    fun recipeJsonLdImagesSkipOrganizationLogo() {
        val html = """<head><script type="application/ld+json">
            {"@context":"https://schema.org","@graph":[
              {"@type":"Organization","name":"Site","logo":{"@type":"ImageObject","url":"https://r.fr/logo.png"}},
              {"@type":"Recipe","name":"Crêpes","image":[{"@type":"ImageObject","url":"https://r.fr/crepes.jpg"},"https://r.fr/crepes-2.jpg"]}
            ]}</script></head>"""
        val r = PageImageExtractor.extract(html, "https://r.fr/crepes")
        assertEquals(listOf("https://r.fr/crepes.jpg", "https://r.fr/crepes-2.jpg"), r.images)
    }

    @Test
    fun declaredLogoRanksBehindPageImages() {
        val html = """<head><meta property="og:image" content="/resources/header/nLogo.png"></head>
            <body><main><img src="/promo/rentree.jpg" width="800" height="400"></main></body>"""
        val r = PageImageExtractor.extract(html, "https://shop.example/")
        assertEquals(listOf("https://shop.example/promo/rentree.jpg", "https://shop.example/resources/header/nLogo.png"), r.images)
    }

    @Test
    fun touchIconOnlyAsLastResortAndBaseHref() {
        assertEquals("https://example.com/touch.png",
            PageImageExtractor.extract("""<head><link rel="apple-touch-icon" href="/touch.png"></head>""", "https://example.com/").image)
        assertEquals("https://cdn.example.net/assets/pic.jpg",
            PageImageExtractor.extract("""<head><base href="https://cdn.example.net/assets/"><meta property="og:image" content="pic.jpg"></head>""", "https://example.com/a/b").image)
    }

    @Test
    fun captchaImagesIgnored() {
        assertEquals(emptyList(), PageImageExtractor.extract("""<head><meta property="og:image" content="https://img.datadome.co/captcha/page.png"></head>""", "https://x.fr/").images)
    }

    @Test
    fun genericPlatformImagesIgnored() {
        assertEquals(emptyList(), PageImageExtractor.extract("""<head><meta property="og:image" content="https://static.cdninstagram.com/rsrc.php/yj/r/f01UdNyJjJj.webp"></head>""", "https://www.instagram.com/p/x/").images)
    }

    @Test
    fun bestFromSrcset() {
        assertEquals("b.jpg", PageImageExtractor.bestFromSrcset("a.jpg 300w, b.jpg 800w"))
        assertEquals("b.jpg", PageImageExtractor.bestFromSrcset("a.jpg 1x, b.jpg 2x"))
        assertEquals("big.jpg", PageImageExtractor.bestFromSrcset("big.jpg 2400w"))
        assertEquals(null, PageImageExtractor.bestFromSrcset(""))
    }
}
