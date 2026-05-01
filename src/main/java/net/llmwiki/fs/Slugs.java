package net.llmwiki.fs;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.regex.Pattern;

public final class Slugs {

  private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9]+");

  /** Lowercase, hyphen-separated, no special chars, max 60 chars. */
  public static String slugify(String s) {
    if (s == null) return "untitled";
    String n = Normalizer.normalize(s, Normalizer.Form.NFD)
        .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
        .toLowerCase();
    String hy = NON_WORD.matcher(n).replaceAll("-").replaceAll("^-+|-+$", "");
    if (hy.isEmpty()) hy = "untitled";
    return hy.length() > 60 ? hy.substring(0, 60).replaceAll("-+$", "") : hy;
  }

  public static String today() { return LocalDate.now().toString(); }

  /** YYYY-MM-DD-slug.md */
  public static String datedFilename(String slug) {
    return today() + "-" + slug + ".md";
  }

  private Slugs() {}
}
