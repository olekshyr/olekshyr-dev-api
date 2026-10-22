package dev.olekshyr.api.blog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BodySanitizerTest {

  private final BodySanitizer sanitizer = new BodySanitizer();

  @Test
  void stripsScriptTags() {
    assertThat(sanitizer.sanitize("<p>Hi</p><script>alert(1)</script>"))
      .isEqualTo("<p>Hi</p>");
  }

  @Test
  void stripsEventHandlerAttributes() {
    assertThat(
      sanitizer.sanitize(
        "<img src=\"https://x.test/a.png\" onerror=\"alert(1)\">"
      )
    )
      .doesNotContain("onerror")
      .contains("src=\"https://x.test/a.png\"");
  }

  @Test
  void stripsJavascriptLinks() {
    assertThat(sanitizer.sanitize("<a href=\"javascript:alert(1)\">x</a>"))
      .doesNotContain("javascript")
      .contains("x");
  }

  @Test
  void dropsNonHttpsImageSources() {
    assertThat(
      sanitizer.sanitize("<img src=\"http://x.test/a.png\" alt=\"a\">")
    )
      .doesNotContain("http://");
  }

  @Test
  void linksGetRelAttributes() {
    assertThat(sanitizer.sanitize("<a href=\"https://example.com\">x</a>"))
      .contains("href=\"https://example.com\"")
      .contains("nofollow")
      .contains("noopener")
      .contains("noreferrer");
  }

  @Test
  void stripsH1ButKeepsItsText() {
    assertThat(sanitizer.sanitize("<h1>Title</h1>")).isEqualTo("Title");
  }

  @Test
  void keepsCkeditorContent() {
    String html =
      "<h2>Heading</h2>" +
      "<p><strong>Bold</strong>, <em>italic</em> and <code>code</code>.</p>" +
      "<ul><li>One</li></ul><ol><li>Two</li></ol>" +
      "<blockquote><p>Quote</p></blockquote>" +
      "<pre><code class=\"language-java\">int x = 1;</code></pre>" +
      "<figure class=\"image\"><img src=\"https://x.test/a.png\" alt=\"A\">" +
      "<figcaption>Caption</figcaption></figure>" +
      "<figure class=\"table\"><table><tbody><tr>" +
      "<td colspan=\"2\">Cell</td></tr></tbody></table></figure>";

    String clean = sanitizer.sanitize(html);

    assertThat(clean)
      .contains("<h2>Heading</h2>")
      .contains("<strong>Bold</strong>")
      .contains("<em>italic</em>")
      .contains("<ul><li>One</li></ul>")
      .contains("<blockquote><p>Quote</p></blockquote>")
      .contains("<code class=\"language-java\">int x &#61; 1;</code>")
      .contains("<figure class=\"image\">")
      .contains("<img src=\"https://x.test/a.png\" alt=\"A\" />")
      .contains("<figcaption>Caption</figcaption>")
      .contains("<figure class=\"table\">")
      .contains("<td colspan=\"2\">Cell</td>");
  }

  @Test
  void figureKeepsOnlyCkeditorClasses() {
    assertThat(
      sanitizer.sanitize(
        "<figure class=\"image image-style-side evil\"><img src=\"https://x.test/a.png\"></figure>"
      )
    )
      .contains("<figure class=\"image image-style-side\">");
    assertThat(sanitizer.sanitize("<figure class=\"evil\">x</figure>"))
      .isEqualTo("<figure>x</figure>");
  }

  @Test
  void dropsUnknownClasses() {
    assertThat(sanitizer.sanitize("<code class=\"evil\">x</code>"))
      .isEqualTo("<code>x</code>");
  }
}
