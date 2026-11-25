package dannypx.foe.helper.wiki;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A small, forgiving HTML reader for the wiki panel: turns a page into a tree of elements and text, the way a browser
 * would (tags left open are closed for it, entities read, scripts and styles left out).
 */
public final class Html {
    private Html() {}

    /// An element (tag != null) or a piece of text (text != null).
    public static final class Node {
        public final String tag;
        public final Map<String, String> attrs;
        public final List<Node> children = new ArrayList<>();
        public final String text;
        public Node parent;

        Node(String tag, Map<String, String> attrs) {
            this.tag = tag;
            this.attrs = attrs;
            this.text = null;
        }

        Node(String text) {
            this.tag = null;
            this.attrs = Map.of();
            this.text = text;
        }

        public String attr(String name) {
            String v = attrs.get(name);
            return v == null ? "" : v;
        }

        public boolean hasClass(String c) {
            for (String s : attr("class").split("\\s+")) if (s.equals(c)) return true;
            return false;
        }

        /// The first element below this one (itself included) with this id, or null.
        public Node byId(String id) {
            if (tag != null && id.equals(attrs.get("id"))) return this;
            for (Node c : children) {
                Node f = c.byId(id);
                if (f != null) return f;
            }
            return null;
        }

        /// The first element below this one (itself included) with this class, or null.
        public Node byClass(String c) {
            if (tag != null && hasClass(c)) return this;
            for (Node ch : children) {
                Node f = ch.byClass(c);
                if (f != null) return f;
            }
            return null;
        }

        /// The first element below this one (itself included) with this tag, or null.
        public Node byTag(String t) {
            if (t.equals(tag)) return this;
            for (Node ch : children) {
                Node f = ch.byTag(t);
                if (f != null) return f;
            }
            return null;
        }

        /// All the text under it, spaces collapsed.
        public String text() {
            StringBuilder sb = new StringBuilder();
            collect(this, sb);
            return sb.toString().replaceAll("\\s+", " ").trim();
        }

        private static void collect(Node n, StringBuilder sb) {
            if (n.text != null) sb.append(n.text);
            for (Node c : n.children) collect(c, sb);
        }
    }

    static final Set<String> VOID = Set.of("br", "img", "hr", "meta", "link", "input", "area", "base", "col", "embed", "source", "wbr");
    static final Set<String> SKIP = Set.of("script", "style", "noscript", "template", "svg", "head", "form", "button", "select", "textarea", "iframe");
    /// Opening one of these closes an open <p> (as a browser does).
    static final Set<String> CLOSES_P = Set.of("p", "div", "ul", "ol", "table", "h1", "h2", "h3", "h4", "h5", "h6", "hr", "pre", "blockquote", "dl");

    /// The page's tree, under a root element.
    public static Node parse(String html) {
        Node root = new Node("#root", new LinkedHashMap<>());
        Node cur = root;
        int i = 0, n = html.length();
        while (i < n) {
            int lt = html.indexOf('<', i);
            if (lt < 0) lt = n;
            if (lt > i) {
                String t = decode(html.substring(i, lt));
                if (!t.isEmpty()) {
                    Node tn = new Node(t);
                    tn.parent = cur;
                    cur.children.add(tn);
                }
            }
            if (lt >= n) break;
            if (html.startsWith("<!--", lt)) {
                int end = html.indexOf("-->", lt + 4);
                i = end < 0 ? n : end + 3;
                continue;
            }
            if (html.startsWith("<!", lt) || html.startsWith("<?", lt)) {
                int end = html.indexOf('>', lt);
                i = end < 0 ? n : end + 1;
                continue;
            }
            int gt = tagEnd(html, lt);
            if (gt < 0) break;
            String inside = html.substring(lt + 1, gt).trim();
            i = gt + 1;
            if (inside.isEmpty()) continue;
            if (inside.startsWith("/")) {
                String name = inside.substring(1).trim().toLowerCase(Locale.ROOT);
                int sp = name.indexOf(' ');
                if (sp >= 0) name = name.substring(0, sp);
                // close back to the matching open element, if there is one
                for (Node up = cur; up != null && up != root; up = up.parent) {
                    if (name.equals(up.tag)) {
                        cur = up.parent;
                        break;
                    }
                }
                continue;
            }
            boolean selfClosing = inside.endsWith("/");
            if (selfClosing) inside = inside.substring(0, inside.length() - 1);
            int sp = 0;
            while (sp < inside.length() && !Character.isWhitespace(inside.charAt(sp))) sp++;
            String name = inside.substring(0, sp).toLowerCase(Locale.ROOT);
            Map<String, String> attrs = attrs(inside.substring(sp));
            if (SKIP.contains(name)) {
                // leave it and everything in it out
                int close = indexOfIgnoreCase(html, "</" + name, i);
                if (close < 0) { i = n; continue; }
                int end = html.indexOf('>', close);
                i = end < 0 ? n : end + 1;
                continue;
            }
            if (CLOSES_P.contains(name)) {
                for (Node up = cur; up != null && up != root; up = up.parent) {
                    if ("p".equals(up.tag)) { cur = up.parent; break; }
                    if (!isInline(up.tag)) break;
                }
            }
            if (name.equals("li") || name.equals("tr") || name.equals("td") || name.equals("th") || name.equals("dt") || name.equals("dd")) {
                // a new one closes the last one left open
                Set<String> same = switch (name) {
                    case "li" -> Set.of("li");
                    case "tr" -> Set.of("tr", "td", "th");
                    case "dt", "dd" -> Set.of("dt", "dd");
                    default -> Set.of("td", "th");
                };
                Set<String> stop = switch (name) {
                    case "li" -> Set.of("ul", "ol");
                    case "tr" -> Set.of("table", "thead", "tbody", "tfoot");
                    case "dt", "dd" -> Set.of("dl");
                    default -> Set.of("tr", "table");
                };
                for (Node up = cur; up != null && up != root; up = up.parent) {
                    if (stop.contains(up.tag)) break;
                    if (same.contains(up.tag)) { cur = up.parent; break; }
                }
            }
            Node el = new Node(name, attrs);
            el.parent = cur;
            cur.children.add(el);
            if (!selfClosing && !VOID.contains(name)) cur = el;
        }
        return root;
    }

    static boolean isInline(String tag) {
        return tag != null && Set.of("a", "span", "b", "strong", "i", "em", "u", "del", "s", "code", "sup", "sub", "small", "big", "font", "abbr", "mark").contains(tag);
    }

    private static int tagEnd(String html, int lt) {
        char quote = 0;
        for (int i = lt + 1; i < html.length(); i++) {
            char c = html.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '>') {
                return i;
            }
        }
        return -1;
    }

    private static int indexOfIgnoreCase(String s, String what, int from) {
        return s.toLowerCase(Locale.ROOT).indexOf(what.toLowerCase(Locale.ROOT), from);
    }

    static Map<String, String> attrs(String s) {
        Map<String, String> out = new LinkedHashMap<>();
        int i = 0, n = s.length();
        while (i < n) {
            while (i < n && Character.isWhitespace(s.charAt(i))) i++;
            int start = i;
            while (i < n && !Character.isWhitespace(s.charAt(i)) && s.charAt(i) != '=') i++;
            if (i == start) { i++; continue; }
            String key = s.substring(start, i).toLowerCase(Locale.ROOT);
            while (i < n && Character.isWhitespace(s.charAt(i))) i++;
            String value = "";
            if (i < n && s.charAt(i) == '=') {
                i++;
                while (i < n && Character.isWhitespace(s.charAt(i))) i++;
                if (i < n && (s.charAt(i) == '"' || s.charAt(i) == '\'')) {
                    char q = s.charAt(i++);
                    int end = s.indexOf(q, i);
                    if (end < 0) end = n;
                    value = s.substring(i, end);
                    i = end + 1;
                } else {
                    int vs = i;
                    while (i < n && !Character.isWhitespace(s.charAt(i))) i++;
                    value = s.substring(vs, i);
                }
            }
            out.put(key, decode(value));
        }
        return out;
    }

    /// Entities read: &amp; &lt; &gt; &quot; &#39; &nbsp; and numbered ones.
    public static String decode(String s) {
        if (s.indexOf('&') < 0) return s;
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '&') {
                int semi = s.indexOf(';', i);
                if (semi > i && semi - i <= 10) {
                    String ent = s.substring(i + 1, semi);
                    String rep = entity(ent);
                    if (rep != null) {
                        sb.append(rep);
                        i = semi + 1;
                        continue;
                    }
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static String entity(String e) {
        switch (e) {
            case "amp": return "&";
            case "lt": return "<";
            case "gt": return ">";
            case "quot": return "\"";
            case "apos": return "'";
            case "nbsp": return " ";
            case "ndash": return "–";
            case "mdash": return "—";
            case "hellip": return "…";
            case "laquo": return "«";
            case "raquo": return "»";
            case "rsquo": return "’";
            case "lsquo": return "‘";
            case "rdquo": return "”";
            case "ldquo": return "“";
            case "times": return "×";
            case "copy": return "©";
            case "deg": return "°";
            default:
        }
        try {
            if (e.startsWith("#x") || e.startsWith("#X")) return new String(Character.toChars(Integer.parseInt(e.substring(2), 16)));
            if (e.startsWith("#")) return new String(Character.toChars(Integer.parseInt(e.substring(1))));
        } catch (RuntimeException ignored) {
            // not an entity
        }
        return null;
    }
}
