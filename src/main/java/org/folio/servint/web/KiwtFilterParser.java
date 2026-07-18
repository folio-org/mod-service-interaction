package org.folio.servint.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Hand-written recursive-descent parser for the legacy web-toolkit filter
 * grammar (SimpleLookupWtk.g4, web-toolkit-ce 10.6.4; REQ-022 AC1/AC2).
 * Produces an operator AST that {@link KiwtListing} turns into JPA Criteria
 * predicates. Compounds associate as FLAT N-ary conjunction/disjunction —
 * the legacy top-two pairing quirk is deliberately not replicated
 * (registered deviation, REQ-022 AC2).
 *
 * <p>Token set: {@code == = != <> > >= < <= =~ !~ =i=}, the specials
 * {@code is [not] null|set|empty}, compounds {@code && || ! ( )},
 * middle-subject ranges {@code v1<prop<v2}, and backslash escaping inside
 * values.
 *
 * <p>Value semantics follow the r31 oracle
 * (docs/migration/evidence/r31-escaped-oracle): a value is the RAW source
 * text between its comparison operator and the next unescaped structural
 * token — backslashes retained, quotes literal, no unescaping at any point.
 * An escape's only effect is at the lexer: {@code \&} consumes one ampersand
 * so a structural {@code &&} cannot pair (same for {@code ||}). A leaf whose
 * remainder carries further absorbable operator spellings collapses into one
 * comparison against that raw remainder (oracle Phases S/S2/S3); compounds
 * split first — absorption never crosses an unescaped {@code &&}/{@code ||}
 * boundary when both sides stand alone (oracle s17).
 */
public final class KiwtFilterParser {

  /** Comparison operators; EQ covers both {@code =} and {@code ==}, NEQ both {@code !=} and {@code <>}. */
  public enum Op { EQ, NEQ, GT, GE, LT, LE, CONT, NCONT, CIEQ }

  public enum SpecialKind { IS_NULL, IS_NOT_NULL, IS_SET, IS_NOT_SET, IS_EMPTY, IS_NOT_EMPTY }

  public sealed interface Node permits And, Or, Not, Comparison, Special, Range {}

  public record And(List<Node> terms) implements Node {}

  public record Or(List<Node> terms) implements Node {}

  public record Not(Node term) implements Node {}

  /** {@code lhs op rhs} — which side is the property is decided later against the metamodel. */
  public record Comparison(String lhs, Op op, String rhs) implements Node {}

  public record Special(String subject, SpecialKind kind) implements Node {}

  /** Middle-subject range {@code low lowOp subject highOp high} (relational operators only). */
  public record Range(String low, Op lowOp, String subject, Op highOp, String high) implements Node {}

  /** Syntactically malformed expression; callers drop the whole clause (legacy ANTLR recovery). */
  public static class ParseException extends RuntimeException {
    public ParseException(String message) {
      super(message);
    }
  }

  private static final Pattern SPECIAL_EXPR =
      Pattern.compile("(?i)^(.+?)\\s+is\\s+(not\\s+)?(null|set|empty)$");

  /** Identifier-shaped tail after a non-absorbable operator becomes the subject (oracle s6/s9). */
  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_.]*");

  /** Structural (non-comparison) tokens. */
  private enum Sym { AND, OR, NOT, LPAREN, RPAREN }

  private record Text(String value) {}

  /** Comparison operator with its end offset in the raw input (absorption needs the raw remainder). */
  private record OpToken(Op op, int end) {}

  /** Structural token with its raw offset (leaf collapse needs the boundary position). */
  private record SymToken(Sym sym, int start) {}

  private final List<Object> tokens;
  private final String raw;
  private int pos;

  private KiwtFilterParser(List<Object> tokens, String raw) {
    this.tokens = tokens;
    this.raw = raw;
  }

  /** Parses one {@code filters} parameter value into its expression tree. */
  public static Node parse(String expression) {
    var tokens = tokenize(expression);
    try {
      var parser = new KiwtFilterParser(absorbEmptyRightSides(tokens, expression), expression);
      var node = parser.parseOr();
      if (parser.pos != parser.tokens.size()) {
        throw new ParseException("Unexpected trailing input in filter: " + expression);
      }
      return node;
    } catch (ParseException failed) {
      return absorbWholeExpression(tokens, expression, failed);
    }
  }

  /**
   * Fallback when the structural parse fails outright (a component after a
   * structural token cannot stand alone, or trailing tokens remain): the
   * whole parameter re-reads as ONE comparison of the first subject and
   * operator against the raw remainder — quotes, escapes and structural
   * text included (oracle q2/dq1, x4-x6 legality-gated).
   */
  private static Node absorbWholeExpression(List<Object> tokens, String raw,
      ParseException failed) {
    if (tokens.size() < 2 || !(tokens.get(0) instanceof Text subject)
        || !(tokens.get(1) instanceof OpToken op)) {
      throw failed;
    }
    var value = raw.substring(op.end()).trim();
    if (value.isEmpty()) {
      throw failed;
    }
    return new Comparison(subject.value(), op.op(), legalAbsorbedValue(value, failed));
  }

  /**
   * Legacy greedy value consumption for TRULY-EMPTY right sides inside
   * compounds (REQ-022 AC6, deviation D-32; evidence
   * docs/migration/evidence/r22-legacy-compound): an empty leaf followed by
   * more text absorbs the raw remainder as its literal value; a trailing
   * empty leaf collapses the paren-stripped parameter into one comparison of
   * the first subject and operator against the literal remainder. A leaf
   * with more than one operator token is NOT empty — it collapses within its
   * own extent in {@link #parseLeaf()} instead (oracle s17: a real
   * {@code &&} still splits). A negation alongside an empty right side drops
   * the whole parameter (legacy answers 500 — defect not replicated, D-32).
   */
  private static List<Object> absorbEmptyRightSides(List<Object> tokens, String raw) {
    int emptyOp = -1;
    for (int i = 0; i < tokens.size(); i++) {
      if (tokens.get(i) instanceof OpToken
          && !(i + 1 < tokens.size() && tokens.get(i + 1) instanceof Text)
          && firstOpOfItsLeaf(tokens, i)) {
        emptyOp = i;
        break;
      }
    }
    if (emptyOp < 0 || tokens.stream().noneMatch(t -> t instanceof SymToken s
        && (s.sym() == Sym.AND || s.sym() == Sym.OR || s.sym() == Sym.LPAREN))) {
      // no truly-empty right side, or a plain single comparison — the
      // empty-RHS single is dropped downstream before property validation (AC1)
      return tokens;
    }
    if (tokens.stream().anyMatch(t -> t instanceof SymToken s && s.sym() == Sym.NOT)) {
      throw new ParseException("Empty right side under negation: " + raw);
    }
    var cause = new ParseException("Unparseable absorbed value: " + raw);
    if (tokens.subList(emptyOp + 1, tokens.size()).stream()
        .anyMatch(t -> !(t instanceof SymToken s && s.sym() == Sym.RPAREN))) {
      // text follows: the empty leaf absorbs the raw remainder as its value
      var absorbed = new ArrayList<>(tokens.subList(0, emptyOp + 1));
      var value = raw.substring(((OpToken) tokens.get(emptyOp)).end()).trim();
      absorbed.add(new Text(legalAbsorbedValue(value, cause)));
      return absorbed;
    }
    // trailing: collapse to first-subject first-op against the raw remainder
    int firstText = -1;
    for (int i = 0; i < tokens.size(); i++) {
      if (tokens.get(i) instanceof Text) {
        firstText = i;
        break;
      }
    }
    if (firstText < 0 || firstText + 1 >= tokens.size()
        || !(tokens.get(firstText + 1) instanceof OpToken first)) {
      throw new ParseException("Uncollapsible empty right side: " + raw);
    }
    long parensBeforeSubject = tokens.subList(0, firstText).stream()
        .filter(t -> t instanceof SymToken s && s.sym() == Sym.LPAREN).count();
    var value = raw.substring(first.end());
    for (int k = 0; k < parensBeforeSubject && value.endsWith(")"); k++) {
      value = value.substring(0, value.length() - 1);
    }
    return List.of(tokens.get(firstText), first, new Text(legalAbsorbedValue(value.trim(), cause)));
  }

  /** True when no other operator token precedes index {@code i} within its leaf. */
  private static boolean firstOpOfItsLeaf(List<Object> tokens, int i) {
    for (int j = i - 1; j >= 0; j--) {
      if (tokens.get(j) instanceof SymToken) {
        return true;
      }
      if (tokens.get(j) instanceof OpToken) {
        return false;
      }
    }
    return true;
  }

  /**
   * Gate for absorbed values, per the r31 oracle: the operator spellings
   * {@code == =~ =i= != !~ <= >= <>}, escaped pairs, quotes, lone {@code &}
   * and {@code |} are all legal literal value text (Phases B/S2/S3). A bare
   * {@code !} {@code (} or {@code )} voids the whole filter (legacy error
   * recovery, x4-x6). A single {@code =} {@code <} or {@code >} re-shapes
   * the expression so the text after it becomes the subject: an
   * identifier-shaped tail answers 400 invalid-property (s6/s9); any other
   * tail voids the filter (s3; legacy's uncaught 500 on a non-identifier
   * tail, s8, is not replicated — same governed family as D-30/D-32).
   */
  private static String legalAbsorbedValue(String value, ParseException cause) {
    int lastSingleOpEnd = -1;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      char next = i + 1 < value.length() ? value.charAt(i + 1) : '\0';
      switch (c) {
        case '\\' -> i++; // escaped pair is literal value text
        case '(', ')' -> throw cause;
        case '!' -> {
          if (next == '=' || next == '~') {
            i++;
          } else {
            throw cause;
          }
        }
        case '=' -> {
          if (next == '=' || next == '~') {
            i++;
          } else if (next == 'i' && i + 2 < value.length() && value.charAt(i + 2) == '=') {
            i += 2;
          } else {
            lastSingleOpEnd = i + 1;
          }
        }
        case '<' -> {
          if (next == '=' || next == '>') {
            i++;
          } else {
            lastSingleOpEnd = i + 1;
          }
        }
        case '>' -> {
          if (next == '=') {
            i++;
          } else {
            lastSingleOpEnd = i + 1;
          }
        }
        default -> {
        }
      }
    }
    if (lastSingleOpEnd >= 0) {
      var tail = value.substring(lastSingleOpEnd).trim();
      if (IDENTIFIER.matcher(tail).matches()) {
        throw new InvalidKiwtPropertyException(tail);
      }
      throw cause;
    }
    return value;
  }

  // ------------------------------------------------------------- tokenizer

  private static List<Object> tokenize(String input) {
    var tokens = new ArrayList<Object>();
    var text = new StringBuilder();
    int i = 0;
    while (i < input.length()) {
      char c = input.charAt(i);
      char next = peekChar(input, i + 1);
      switch (c) {
        case '\\' -> {
          // the escaped pair is literal value text, backslash RETAINED
          // (grammar ESCAPED_SPECIAL; oracle e1-e5/db1/db2 — legacy never
          // unescapes)
          text.append(c);
          if (i + 1 < input.length()) {
            text.append(next);
            i += 2;
          } else {
            i++;
          }
        }
        case '&' -> {
          if (next == '&') {
            flush(tokens, text);
            tokens.add(new SymToken(Sym.AND, i));
            i += 2;
          } else {
            text.append(c);
            i++;
          }
        }
        case '|' -> {
          if (next == '|') {
            flush(tokens, text);
            tokens.add(new SymToken(Sym.OR, i));
            i += 2;
          } else {
            text.append(c);
            i++;
          }
        }
        case '!' -> {
          flush(tokens, text);
          if (next == '=') {
            tokens.add(new OpToken(Op.NEQ, i + 2));
            i += 2;
          } else if (next == '~') {
            tokens.add(new OpToken(Op.NCONT, i + 2));
            i += 2;
          } else {
            tokens.add(new SymToken(Sym.NOT, i));
            i++;
          }
        }
        case '=' -> {
          flush(tokens, text);
          if (next == '=') {
            tokens.add(new OpToken(Op.EQ, i + 2));
            i += 2;
          } else if (next == '~') {
            tokens.add(new OpToken(Op.CONT, i + 2));
            i += 2;
          } else if (next == 'i' && peekChar(input, i + 2) == '=') {
            tokens.add(new OpToken(Op.CIEQ, i + 3));
            i += 3;
          } else {
            tokens.add(new OpToken(Op.EQ, i + 1));
            i++;
          }
        }
        case '<' -> {
          flush(tokens, text);
          if (next == '>') {
            tokens.add(new OpToken(Op.NEQ, i + 2));
            i += 2;
          } else if (next == '=') {
            tokens.add(new OpToken(Op.LE, i + 2));
            i += 2;
          } else {
            tokens.add(new OpToken(Op.LT, i + 1));
            i++;
          }
        }
        case '>' -> {
          flush(tokens, text);
          if (next == '=') {
            tokens.add(new OpToken(Op.GE, i + 2));
            i += 2;
          } else {
            tokens.add(new OpToken(Op.GT, i + 1));
            i++;
          }
        }
        case '(' -> {
          flush(tokens, text);
          tokens.add(new SymToken(Sym.LPAREN, i));
          i++;
        }
        case ')' -> {
          flush(tokens, text);
          tokens.add(new SymToken(Sym.RPAREN, i));
          i++;
        }
        default -> {
          text.append(c);
          i++;
        }
      }
    }
    flush(tokens, text);
    return tokens;
  }

  private static char peekChar(String input, int index) {
    return index < input.length() ? input.charAt(index) : '\0';
  }

  private static void flush(List<Object> tokens, StringBuilder text) {
    var value = text.toString().trim();
    text.setLength(0);
    if (!value.isEmpty()) {
      tokens.add(new Text(value));
    }
  }

  // ---------------------------------------------------- recursive descent

  private Node parseOr() {
    var terms = new ArrayList<Node>();
    terms.add(parseAnd());
    while (peekSym() == Sym.OR) {
      pos++;
      terms.add(parseAnd());
    }
    return terms.size() == 1 ? terms.get(0) : new Or(List.copyOf(terms));
  }

  private Node parseAnd() {
    var terms = new ArrayList<Node>();
    terms.add(parseUnary());
    while (peekSym() == Sym.AND) {
      pos++;
      terms.add(parseUnary());
    }
    return terms.size() == 1 ? terms.get(0) : new And(List.copyOf(terms));
  }

  private Node parseUnary() {
    var sym = peekSym();
    if (sym == Sym.NOT) {
      pos++;
      return new Not(parseUnary());
    }
    if (sym == Sym.LPAREN) {
      pos++;
      var inner = parseOr();
      if (peekSym() != Sym.RPAREN) {
        throw new ParseException("Missing closing parenthesis");
      }
      pos++;
      return inner; // groups become nested predicates, no dedicated node
    }
    return parseLeaf();
  }

  private Node parseLeaf() {
    if (!(peekToken() instanceof Text first)) {
      throw new ParseException("Expected a filter term");
    }
    pos++;
    if (!(peekToken() instanceof OpToken op1)) {
      return parseSpecial(first.value());
    }
    pos++;
    var second = optionalText();
    if (peekToken() instanceof OpToken op2) {
      if (isRelational(op1.op()) && isRelational(op2.op()) && !second.isEmpty()) {
        pos++;
        var third = optionalText();
        if (!third.isEmpty()) {
          return new Range(first.value(), op1.op(), second, op2.op(), third);
        }
        throw new ParseException("Malformed range expression");
      }
      return collapseLeaf(first, op1);
    }
    return new Comparison(first.value(), op1.op(), second);
  }

  /**
   * Operator-absorbing leaf collapse (oracle Phases S/S2/S3): a leaf whose
   * remainder carries further operator tokens is ONE comparison against the
   * raw text up to the leaf's structural boundary — s17 proves a real
   * {@code &&}/{@code ||} still splits, so the collapse never crosses it.
   */
  private Node collapseLeaf(Text first, OpToken op1) {
    int rawEnd = raw.length();
    while (pos < tokens.size()) {
      if (tokens.get(pos) instanceof SymToken s
          && (s.sym() == Sym.AND || s.sym() == Sym.OR || s.sym() == Sym.RPAREN)) {
        rawEnd = s.start();
        break;
      }
      pos++;
    }
    var value = raw.substring(op1.end(), rawEnd).trim();
    var cause = new ParseException("Unparseable absorbed value: " + raw);
    return new Comparison(first.value(), op1.op(), legalAbsorbedValue(value, cause));
  }

  /** An absent value side (e.g. {@code a==}) is the empty string — the clause is dropped downstream (REQ-022 AC1). */
  private String optionalText() {
    if (peekToken() instanceof Text text) {
      pos++;
      return text.value();
    }
    return "";
  }

  private Node parseSpecial(String text) {
    var matcher = SPECIAL_EXPR.matcher(text);
    if (!matcher.matches()) {
      throw new ParseException("Malformed filter clause: " + text);
    }
    var subject = matcher.group(1).trim();
    var negated = matcher.group(2) != null;
    var kind = switch (matcher.group(3).toLowerCase(Locale.ROOT)) {
      case "null" -> negated ? SpecialKind.IS_NOT_NULL : SpecialKind.IS_NULL;
      case "set" -> negated ? SpecialKind.IS_NOT_SET : SpecialKind.IS_SET;
      default -> negated ? SpecialKind.IS_NOT_EMPTY : SpecialKind.IS_EMPTY;
    };
    return new Special(subject, kind);
  }

  private static boolean isRelational(Op op) {
    return op == Op.GT || op == Op.GE || op == Op.LT || op == Op.LE;
  }

  private Object peekToken() {
    return pos < tokens.size() ? tokens.get(pos) : null;
  }

  private Sym peekSym() {
    return peekToken() instanceof SymToken s ? s.sym() : null;
  }
}
