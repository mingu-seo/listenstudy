package com.codro.listenstudy.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Pins the localized policy copy in `strings.xml` to the published markdown policies
 * (`docs/05_policy/privacy-policy-ko.md`, `terms-of-use-ko.md`): section numbering/order and the
 * billing/open-source statements that must not drift apart. The enum-level structure is pinned in
 * [AboutContentTest]; this test guards the numbered titles and required body phrases, which only
 * exist in the string resources.
 */
class PolicyStringsAlignmentTest {

    private val strings: Map<String, String> by lazy {
        val file = sequenceOf(
            "src/main/res/values/strings.xml",
            "app/src/main/res/values/strings.xml",
            "android/app/src/main/res/values/strings.xml",
        ).map(::File).firstOrNull(File::exists)
            ?: error("strings.xml not found from working dir ${File(".").absolutePath}")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        (0 until nodes.length).associate { index ->
            val node = nodes.item(index)
            node.attributes.getNamedItem("name").nodeValue to node.textContent
        }
    }

    private fun string(name: String): String =
        strings[name] ?: error("missing string resource: $name")

    private val stringsXmlRawText: String by lazy {
        sequenceOf(
            "src/main/res/values/strings.xml",
            "app/src/main/res/values/strings.xml",
            "android/app/src/main/res/values/strings.xml",
        ).map(::File).first(File::exists).readText()
    }

    /**
     * The published policy markdown lives in the workspace-level `docs/05_policy` tree, which is a
     * sibling of this git repository. When the repo is checked out standalone the docs are absent;
     * those assertions are then skipped (Assume) instead of failing the isolated build.
     */
    private fun policyMarkdown(fileName: String): String? =
        sequenceOf(
            "../../../docs/05_policy/$fileName", // unit-test working dir is android/app
            "../../docs/05_policy/$fileName",
            "../docs/05_policy/$fileName",
            "docs/05_policy/$fileName",
        ).map(::File).firstOrNull(File::exists)?.readText()

    @Test
    fun `privacy billing is section 7 and retention section 8 as in the markdown policy`() {
        // privacy-policy-ko.md: "## 7. 결제 — 일회성 후원(Supporter)", "## 8. 보관 기간과 삭제".
        assertTrue(
            "billing must be numbered 7 to match privacy-policy-ko.md",
            string("about_privacy_s_billing_title").startsWith("7."),
        )
        assertTrue(
            "retention must be numbered 8 to match privacy-policy-ko.md",
            string("about_privacy_s_retention_title").startsWith("8."),
        )
    }

    @Test
    fun `privacy billing body carries the token non-retention and refund statements`() {
        // Both statements exist in privacy-policy-ko.md §7 and must not be dropped in-app:
        // purchase tokens/order ids/account identifiers are never persisted or logged, and a refund
        // clears the supporter status per the Play ownership query.
        val body = string("about_privacy_s_billing_body")
        assertTrue("must state purchase tokens are not stored", body.contains("구매 토큰"))
        assertTrue("must state order ids are not stored", body.contains("주문번호"))
        assertTrue("must state refunds are reflected", body.contains("환불"))
    }

    @Test
    fun `app policy strings do not claim to carry the verbatim full policy text`() {
        // The About screen restructures the key policy content into sections; it is NOT a verbatim
        // copy of the published markdown. Claims like "전문을 아래에 그대로" or titling the section
        // "…전문" overstate what the screen shows and must not come back.
        val checked = listOf(
            "about_privacy_body",
            "about_terms_body",
            "about_privacy_full_title",
            "about_terms_full_title",
            "about_privacy_s_status_body",
            "about_terms_s_status_body",
        )
        checked.forEach { name ->
            val value = string(name)
            assertFalse("$name must not claim a verbatim full text (전문)", value.contains("전문"))
            assertFalse("$name must not claim 그대로 싣습니다", value.contains("그대로"))
        }
    }

    @Test
    fun `strings xml carries no verbatim-mirror claims even in comments`() {
        // Raw-text check so the `<!-- ... -->` comments are covered too: the in-app sections are a
        // sectioned summary of the current policy, not a "mirror" of the markdown files.
        assertFalse(
            "strings.xml must not claim 전문을 아래에 그대로",
            stringsXmlRawText.contains("전문을 아래에 그대로"),
        )
        assertFalse(
            "strings.xml must not describe the in-app policy as a mirror",
            stringsXmlRawText.contains("mirror"),
        )
    }

    @Test
    fun `published markdown does not claim the in-app screen shows an identical full text`() {
        // The markdown IS the full document; the app shows the key content of the current policy.
        // Neither side may claim the two texts are identical ("전문과 동일", "실린 전문").
        val docs = listOf("privacy-policy-ko.md", "terms-of-use-ko.md")
            .mapNotNull { name -> policyMarkdown(name)?.let { name to it } }
        assumeTrue("workspace docs/05_policy not present in this checkout", docs.isNotEmpty())
        docs.forEach { (name, text) ->
            assertFalse("$name must not claim 전문과 동일", text.contains("전문과 동일"))
            assertFalse("$name must not claim the app carries the 전문", text.contains("실린 전문"))
            assertFalse("$name must not claim 앱 내 전문", text.contains("앱 내 전문"))
        }
    }

    @Test
    fun `published markdown keeps its billing core statements`() {
        // Rewording the honesty claims must not drop the billing substance: §7 numbering in the
        // privacy policy and the token non-retention / refund statements.
        val privacy = policyMarkdown("privacy-policy-ko.md")
        assumeTrue("workspace docs/05_policy not present in this checkout", privacy != null)
        assertTrue("billing must stay §7", privacy!!.contains("## 7. 결제 — 일회성 후원(Supporter)"))
        assertTrue("must state purchase tokens are not stored", privacy.contains("구매 토큰"))
        assertTrue("must state refunds are reflected", privacy.contains("환불"))
    }

    @Test
    fun `terms open source notice does not claim every component is apache licensed`() {
        // Google Play Billing ships under the Android SDK license, so a blanket "all Apache 2.0"
        // claim is false. Each component follows its own listed license instead.
        val body = string("about_terms_s_open_source_body")
        assertFalse("blanket all-Apache claim must be removed", body.contains("모두 Apache"))
        assertTrue(
            "terms must disclose the Google Play Billing Library component",
            body.contains("Google Play Billing"),
        )
    }
}
