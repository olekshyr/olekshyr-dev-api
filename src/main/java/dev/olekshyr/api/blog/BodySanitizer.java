package dev.olekshyr.api.blog;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

/**
 * Cleans a translation's HTML body on write. The allowlist covers what
 * CKEditor 5 emits for blog content; everything else is stripped, keeping its
 * text. Only the body is HTML: titles, excerpts and meta fields are plain text
 * the frontend escapes.
 */
@Component
class BodySanitizer {

  /**
   * One class token of a figure: CKEditor wraps images and tables in
   * figure.image / figure.table and adds style classes like image-style-side.
   * Checked per token, so the pattern has no nested repetition (ReDoS).
   */
  private static final Pattern FIGURE_CLASS_TOKEN = Pattern.compile(
    "(image|table)[a-z0-9_-]*"
  );

  /** CKEditor code blocks: <pre><code class="language-java">. */
  private static final Pattern CODE_CLASS = Pattern.compile(
    "language-[a-z0-9+#-]+"
  );

  /**
   * h1 is left out on purpose: the page renders the post title as its only
   * h1. CKEditor's "Heading 1" emits h2.
   */
  private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
    .allowElements(
      "p",
      "br",
      "hr",
      "h2",
      "h3",
      "h4",
      "h5",
      "h6",
      "strong",
      "b",
      "em",
      "i",
      "u",
      "s",
      "sub",
      "sup",
      "code",
      "pre",
      "blockquote",
      "ul",
      "ol",
      "li",
      "a",
      "img",
      "figure",
      "figcaption",
      "table",
      "caption",
      "thead",
      "tbody",
      "tfoot",
      "tr",
      "th",
      "td"
    )
    .allowUrlProtocols("https", "http", "mailto")
    .allowAttributes("href")
    .onElements("a")
    .requireRelsOnLinks("nofollow", "noopener", "noreferrer")
    .allowAttributes("src")
    .matching((element, attribute, value) ->
      value.startsWith("https://") ? value : null
    )
    .onElements("img")
    .allowAttributes("alt", "width", "height")
    .onElements("img")
    .allowAttributes("class")
    .matching((element, attribute, value) -> figureClasses(value))
    .onElements("figure")
    .allowAttributes("class")
    .matching(CODE_CLASS)
    .onElements("code")
    .allowAttributes("colspan", "rowspan")
    .onElements("th", "td")
    .toFactory();

  String sanitize(String html) {
    return POLICY.sanitize(html);
  }

  /** Keeps the allowed tokens; null drops the attribute when none are left. */
  private static String figureClasses(String value) {
    String kept = Arrays
      .stream(value.trim().split("\\s+"))
      .filter(token -> FIGURE_CLASS_TOKEN.matcher(token).matches())
      .collect(Collectors.joining(" "));
    return kept.isEmpty() ? null : kept;
  }
}
